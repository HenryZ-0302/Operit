import argparse
import json
import os
import re
import ssl
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional


def _load_env(env_path: Path) -> None:
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        k = k.strip()
        v = v.strip().strip('"').strip("'")
        if k and k not in os.environ:
            os.environ[k] = v


def _build_ssl_context(force_tls12: bool, insecure: bool) -> ssl.SSLContext:
    ctx = ssl.create_default_context()
    if insecure:
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

    if force_tls12 and hasattr(ssl, "TLSVersion"):
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.maximum_version = ssl.TLSVersion.TLSv1_2

    return ctx


def _decode_output(data: Optional[bytes]) -> str:
    if not data:
        return ""
    return data.decode("utf-8", errors="replace")


def _http_json(
    url: str,
    method: str,
    token: str,
    ctx: ssl.SSLContext,
    payload: Optional[dict] = None,
    extra_headers: Optional[dict] = None,
    timeout_s: int = 60,
    retries: int = 5,
) -> tuple[object, dict]:
    data: Optional[bytes] = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")

    headers: dict[str, str] = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "OperitTools/1.0 (import_anthropic_skills.py)",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if extra_headers:
        for k, v in extra_headers.items():
            headers[str(k)] = str(v)

    backoff_s = 1.0
    last_error: Optional[BaseException] = None

    for attempt in range(retries):
        req = urllib.request.Request(url, data=data, method=method.upper())
        for k, v in headers.items():
            req.add_header(k, v)

        try:
            with urllib.request.urlopen(req, timeout=timeout_s, context=ctx) as resp:
                body = resp.read()
                text = _decode_output(body)
                resp_headers = {k.lower(): v for k, v in resp.headers.items()}
                if not text.strip():
                    return None, resp_headers
                return json.loads(text), resp_headers
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = _decode_output(e.read())
            except Exception:
                pass

            if e.code == 403 and "Resource not accessible by personal access token" in body:
                hint = (
                    "\n\nHint: Your GITHUB_TOKEN likely lacks permission to create issues in the target repo.\n"
                    "- If you use a Fine-grained PAT: grant repository access to the target repo and enable 'Issues: Read and write'.\n"
                    "- If you use a Classic PAT: ensure it has 'public_repo' (for public repos) or 'repo' (for private repos) scope.\n"
                    "- Also ensure the token belongs to an account that has access to the target repo.\n"
                )
                body = body + hint

            retryable = e.code in (403, 429, 500, 502, 503, 504)
            if retryable and attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue

            raise RuntimeError(f"GitHub API error: HTTP {e.code} {e.reason}\n{body}")
        except (urllib.error.URLError, ssl.SSLError, OSError) as e:
            last_error = e
            if attempt + 1 < retries:
                time.sleep(backoff_s * (2**attempt))
                continue
            break

    raise RuntimeError(
        "Request failed (network/SSL). "
        "If you are behind a proxy, set HTTPS_PROXY/HTTP_PROXY. "
        "If TLS handshake keeps failing, try --github-tls12. As a last resort, try --github-insecure. "
        f"Original: {last_error!r}"
    )


def _openai_chat_completion(base_url: str, api_key: str, payload: dict) -> dict:
    url = base_url.rstrip("/") + "/v1/chat/completions"
    data = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {api_key}")

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw)
    except urllib.error.HTTPError as e:
        body = ""
        try:
            body = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(f"AI request failed: HTTP {e.code} {e.reason}\n{body}")


def _extract_ai_text(resp: dict) -> str:
    try:
        msg = resp["choices"][0]["message"]["content"]
    except Exception as e:
        raise RuntimeError(f"Unexpected AI response: {e}\n{json.dumps(resp, ensure_ascii=False)[:2000]}")
    return (msg or "").strip()


@dataclass
class SourceSkill:
    name: str
    path: str
    html_url: str


def _parse_link_next(link_header: str) -> str:
    if not link_header:
        return ""
    parts = [p.strip() for p in link_header.split(",")]
    for part in parts:
        if 'rel="next"' in part or "rel=next" in part:
            m = re.search(r"<([^>]+)>", part)
            if m:
                return m.group(1)
    return ""


def _iter_issues(api_base: str, repo: str, token: str, ctx: ssl.SSLContext, state: str, labels: str) -> list[dict]:
    owner, name = repo.split("/", 1)
    params = {
        "state": state,
        "per_page": "100",
        "sort": "updated",
        "direction": "desc",
    }
    if labels:
        params["labels"] = labels

    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/issues"
        + "?"
        + urllib.parse.urlencode(params)
    )

    out: list[dict] = []
    while url:
        data, headers = _http_json(url=url, method="GET", token=token, ctx=ctx)
        if not isinstance(data, list):
            raise RuntimeError(f"Unexpected GitHub response (expected list): {str(data)[:500]}")
        for it in data:
            if not isinstance(it, dict):
                continue
            if "pull_request" in it:
                continue
            out.append(it)
        url = _parse_link_next(str(headers.get("link", "")))

    return out


def _get_tree_sha_for_ref(api_base: str, repo: str, token: str, ctx: ssl.SSLContext, ref: str) -> str:
    owner, name = repo.split("/", 1)
    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/commits/{urllib.parse.quote(ref)}"
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx)
    if not isinstance(data, dict):
        return ""
    commit = data.get("commit")
    if isinstance(commit, dict):
        tree = commit.get("tree")
        if isinstance(tree, dict):
            sha = str(tree.get("sha") or "").strip()
            return sha
    return ""


def _list_source_skills_recursive(
    api_base: str,
    repo: str,
    ref: str,
    root_path: str,
    token: str,
    ctx: ssl.SSLContext,
) -> tuple[list[SourceSkill], bool]:
    owner, name = repo.split("/", 1)
    tree_sha = _get_tree_sha_for_ref(api_base=api_base, repo=repo, token=token, ctx=ctx, ref=ref)
    if not tree_sha:
        raise RuntimeError(f"Failed to resolve tree sha for ref: {ref}")

    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/git/trees/{urllib.parse.quote(tree_sha)}?recursive=1"
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx, timeout_s=120)
    if not isinstance(data, dict):
        raise RuntimeError(f"Unexpected git tree response: {str(data)[:500]}")

    truncated = bool(data.get("truncated"))
    tree = data.get("tree")
    if not isinstance(tree, list):
        raise RuntimeError(f"Unexpected git tree response (missing tree): {str(data)[:500]}")

    root = str(root_path or "").strip().strip("/")
    if root in (".", "./"):
        root = ""
    root_prefix = (root + "/") if root else ""

    skill_dirs: set[str] = set()
    for it in tree:
        if not isinstance(it, dict):
            continue
        if str(it.get("type") or "") != "blob":
            continue
        p = str(it.get("path") or "")
        if not p.endswith("/SKILL.md"):
            continue
        if root_prefix and not p.startswith(root_prefix):
            continue
        dir_path = p[: -len("/SKILL.md")]
        if not dir_path:
            continue
        skill_dirs.add(dir_path)

    skills: list[SourceSkill] = []
    for dir_path in sorted(skill_dirs):
        skill_id = dir_path.split("/")[-1]
        skills.append(
            SourceSkill(
                name=skill_id,
                path=dir_path,
                html_url=f"https://github.com/{repo}/tree/{ref}/{dir_path}",
            )
        )

    skills.sort(key=lambda x: x.name)
    return skills, truncated


def _extract_skill_metadata_repo_url(issue_body: str) -> str:
    if not issue_body:
        return ""
    m = re.search(r"<!--\s*operit-skill-json:\s*(\{.*?\})\s*-->", issue_body, re.DOTALL)
    if not m:
        return ""
    raw = m.group(1)
    try:
        meta = json.loads(raw)
    except Exception:
        return ""
    if isinstance(meta, dict):
        v = meta.get("repositoryUrl") or meta.get("repoUrl") or ""
        return str(v).strip()
    return ""


def _list_source_skills(
    api_base: str,
    repo: str,
    ref: str,
    root_path: str,
    token: str,
    ctx: ssl.SSLContext,
) -> list[SourceSkill]:
    owner, name = repo.split("/", 1)
    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/contents/{urllib.parse.quote(root_path.strip('/'))}"
        + "?"
        + urllib.parse.urlencode({"ref": ref})
    )

    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx)
    if not isinstance(data, list):
        raise RuntimeError(f"Unexpected source contents response: {str(data)[:500]}")

    skills: list[SourceSkill] = []
    for it in data:
        if not isinstance(it, dict):
            continue
        if it.get("type") != "dir":
            continue
        skills.append(
            SourceSkill(
                name=str(it.get("name") or "").strip(),
                path=str(it.get("path") or "").strip(),
                html_url=str(it.get("html_url") or "").strip(),
            )
        )

    skills = [s for s in skills if s.name]
    skills.sort(key=lambda x: x.name)
    return skills


def _pick_skill_markdown(
    api_base: str,
    repo: str,
    ref: str,
    skill_path: str,
    token: str,
    ctx: ssl.SSLContext,
) -> tuple[str, str]:
    owner, name = repo.split("/", 1)
    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/contents/{urllib.parse.quote(skill_path.strip('/'))}"
        + "?"
        + urllib.parse.urlencode({"ref": ref})
    )
    data, _ = _http_json(url=url, method="GET", token=token, ctx=ctx)
    if not isinstance(data, list):
        return "", ""

    files: list[dict] = [it for it in data if isinstance(it, dict) and it.get("type") == "file"]

    def find_name(names: list[str]) -> Optional[dict]:
        lower = {str(it.get("name") or "").lower(): it for it in files}
        for n in names:
            if n.lower() in lower:
                return lower[n.lower()]
        return None

    candidate = find_name(["SKILL.md", "README.md", "readme.md"])  # prefer SKILL.md
    if candidate is None:
        md_files = [it for it in files if str(it.get("name") or "").lower().endswith(".md")]
        md_files.sort(key=lambda it: str(it.get("name") or "").lower())
        candidate = md_files[0] if md_files else None

    if not candidate:
        return "", ""

    download_url = str(candidate.get("download_url") or "").strip()
    file_name = str(candidate.get("name") or "").strip()
    if not download_url:
        return "", ""

    req = urllib.request.Request(download_url, method="GET")
    if token:
        req.add_header("Authorization", f"Bearer {token}")

    with urllib.request.urlopen(req, timeout=60, context=ctx) as resp:
        content = _decode_output(resp.read())

    return file_name, content


def _extract_first_heading(md: str) -> str:
    if not md:
        return ""
    for raw in md.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#"):
            return line.lstrip("#").strip()
    return ""


def _ai_generate_description(
    ai_base_url: str,
    ai_api_key: str,
    ai_model: str,
    ai_temperature: float,
    skill_name: str,
    source_repo_url: str,
    md_file_name: str,
    md_content: str,
) -> str:
    md = (md_content or "").strip()
    if len(md) > 6000:
        md = md[:6000].rstrip() + "\n\n[truncated]\n"

    system_prompt = (
        "You write concise marketplace descriptions for software skills. "
        "You must only use the given source content. "
        "Do not invent capabilities not supported by the text."
    )

    user_prompt = textwrap.dedent(
        f"""
        Write a short Chinese description for an Operit Skill Market listing.

        Constraints:
        - Output ONLY the description text, no Markdown headings, no lists.
        - 1-2 sentences, ideally 30-120 Chinese characters.
        - Do not include \"##\" or \"**\".

        Skill folder: {skill_name}
        Source repo URL: {source_repo_url}
        Source file: {md_file_name}

        Source content:
        {md}
        """
    ).strip()

    payload: dict = {
        "model": ai_model,
        "temperature": ai_temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    resp = _openai_chat_completion(base_url=ai_base_url, api_key=ai_api_key, payload=payload)
    out = _extract_ai_text(resp)
    out = out.replace("\r", " ").replace("\n", " ").strip()
    out = re.sub(r"\s+", " ", out)
    return out


def _build_operit_issue_body(description: str, repository_url: str, version: str) -> str:
    metadata = {
        "repositoryUrl": repository_url,
        "category": "",
        "tags": "",
        "version": version,
    }

    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    lines: list[str] = []
    lines.append(f"<!-- operit-skill-json: {json.dumps(metadata, ensure_ascii=False, separators=(',', ':'))} -->")
    lines.append(f"<!-- operit-parser-version: {version} -->")
    lines.append("")
    lines.append("## 📋 Skill 信息")
    lines.append("")
    lines.append(f"**描述:** {description}")
    lines.append("")

    if repository_url:
        lines.append("## 🔗 仓库信息")
        lines.append("")
        lines.append(f"**仓库地址:** {repository_url}")
        lines.append("")

    if repository_url:
        lines.append("## 📦 安装方式")
        lines.append("")
        lines.append("1. 打开 Operit → 包管理 → Skills")
        lines.append("2. 点击「导入 Skill」→ 「从仓库导入」")
        lines.append(f"3. 输入仓库地址：`{repository_url}`")
        lines.append("4. 确认导入")
        lines.append("")

    lines.append("## 🛠️ 技术信息")
    lines.append("")
    lines.append("| 项目 | 值 |")
    lines.append("|------|-----|")
    lines.append("| 发布平台 | Operit Skill 市场 |")
    lines.append("| 解析版本 | 1.0 |")
    lines.append(f"| 发布时间 | {now_str} |")
    lines.append("| 状态 | ⏳ Pending Review |")
    lines.append("")

    return "\n".join(lines)


def _create_issue(api_base: str, repo: str, token: str, ctx: ssl.SSLContext, title: str, body: str, labels: list[str]) -> dict:
    owner, name = repo.split("/", 1)
    url = api_base.rstrip("/") + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/issues"

    payload = {
        "title": title,
        "body": body,
        "labels": labels,
    }

    data, _ = _http_json(url=url, method="POST", token=token, ctx=ctx, payload=payload, timeout_s=120)
    if isinstance(data, dict):
        return data
    raise RuntimeError(f"Unexpected create issue response: {str(data)[:500]}")


def _safe_title(base: str, suffix: str) -> str:
    t = (base or "").strip()
    if not t:
        t = "Untitled Skill"
    if suffix:
        if not t.endswith(suffix):
            t = t + suffix
    t = t.replace("\r", " ").replace("\n", " ").strip()
    t = re.sub(r"\s+", " ", t)
    if len(t) > 120:
        t = t[:120].rstrip()
    return t


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Import anthropics/skills entries into Operit Skill Market by creating GitHub issues."
    )
    parser.add_argument(
        "--env",
        default=str(Path(__file__).resolve().parent / ".env"),
        help="Path to .env file (default: tools/github/.env)",
    )
    parser.add_argument(
        "--source-repo",
        default="anthropics/skills",
        help="Source GitHub repo (default: anthropics/skills)",
    )
    parser.add_argument(
        "--source-ref",
        default="main",
        help="Source ref/branch (default: main)",
    )
    parser.add_argument(
        "--source-root",
        default="skills",
        help="Root path in source repo that contains skill folders (default: skills)",
    )
    parser.add_argument(
        "--recursive",
        action="store_true",
        help="Discover skills by searching for SKILL.md under --source-root (supports nested repo layouts)",
    )
    parser.add_argument(
        "--allow-duplicate-ids",
        action="store_true",
        help="Allow duplicate skill IDs (folder names). Not recommended because issue title is the ID.",
    )
    parser.add_argument(
        "--target-repo",
        default="AAswordman/OperitSkillMarket",
        help="Target GitHub repo to create issues in (default: AAswordman/OperitSkillMarket)",
    )
    parser.add_argument(
        "--labels",
        default="skill-plugin",
        help="Comma-separated labels to apply (default: skill-plugin)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Limit how many skills to import (0 means all)",
    )
    parser.add_argument(
        "--only-skill",
        default="",
        help="Only import a single skill folder by name (e.g. pdf)",
    )
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Skip skills that already exist in target issues (by matching repositoryUrl in issue body)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Do not create issues; only print planned actions",
    )
    parser.add_argument(
        "--print-description",
        action="store_true",
        help="Print the AI-generated description text (use with --dry-run for preview)",
    )
    parser.add_argument(
        "--print-body",
        action="store_true",
        help="Print the generated Operit issue body (use with --dry-run for preview)",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print progress logs",
    )
    parser.add_argument(
        "--github-tls12",
        action="store_true",
        help="Force TLS 1.2 for GitHub HTTPS requests (workaround for some networks/proxies).",
    )
    parser.add_argument(
        "--github-insecure",
        action="store_true",
        help="Disable TLS certificate verification for GitHub HTTPS requests (NOT recommended).",
    )
    parser.add_argument(
        "--title-suffix",
        default="",
        help="Ignored. Issue title is always the skill folder name (ID).",
    )
    parser.add_argument(
        "--version",
        default="v1",
        help="operit-parser-version value (default: v1)",
    )

    args = parser.parse_args()

    _load_env(Path(args.env))

    github_token = os.environ.get("GITHUB_TOKEN", "").strip()
    github_api_base = os.environ.get("GITHUB_API_URL", "https://api.github.com").strip() or "https://api.github.com"

    ai_base_url = os.environ.get("AI_BASE_URL", "").strip()
    ai_api_key = os.environ.get("AI_API_KEY", "").strip()
    ai_model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
    try:
        ai_temperature = float(os.environ.get("AI_TEMPERATURE", "0.2") or 0.2)
    except Exception:
        ai_temperature = 0.2

    if not ai_base_url or not ai_api_key:
        print("Missing AI_BASE_URL/AI_API_KEY. Fill tools/github/.env (gitignored) or set env vars.")
        return 2

    if not github_token and not args.dry_run:
        print("Missing GITHUB_TOKEN. Creating issues requires a GitHub token.")
        return 2

    if not args.target_repo or "/" not in args.target_repo:
        print("Invalid --target-repo. Expect owner/name.")
        return 2

    github_ctx = _build_ssl_context(force_tls12=bool(args.github_tls12), insecure=bool(args.github_insecure))

    labels = [x.strip() for x in str(args.labels).split(",") if x.strip()]
    labels_csv = ",".join(labels)

    if args.verbose:
        print(f"[info] target repo: {args.target_repo}")
        print(f"[info] labels: {labels_csv or '(none)'}")

    existing_repo_urls: set[str] = set()
    if args.skip_existing:
        issues = _iter_issues(
            api_base=github_api_base,
            repo=args.target_repo,
            token=github_token,
            ctx=github_ctx,
            state="all",
            labels="",
        )
        for it in issues:
            body = str(it.get("body") or "")
            repo_url = _extract_skill_metadata_repo_url(body)
            if repo_url:
                existing_repo_urls.add(repo_url)

    truncated_tree = False
    if args.recursive:
        skills, truncated_tree = _list_source_skills_recursive(
            api_base=github_api_base,
            repo=args.source_repo,
            ref=args.source_ref,
            root_path=args.source_root,
            token=github_token,
            ctx=github_ctx,
        )
    else:
        skills = _list_source_skills(
            api_base=github_api_base,
            repo=args.source_repo,
            ref=args.source_ref,
            root_path=args.source_root,
            token=github_token,
            ctx=github_ctx,
        )

    if args.verbose and args.recursive and truncated_tree:
        print(
            "[warn] Git tree API returned truncated results; some skills may be missing. "
            "Try narrowing --source-root to a smaller subdirectory."
        )

    if not args.allow_duplicate_ids:
        name_counts: dict[str, int] = {}
        for s in skills:
            name_counts[s.name] = name_counts.get(s.name, 0) + 1
        dups = sorted([k for k, v in name_counts.items() if v > 1])
        if dups:
            print(
                "Duplicate skill IDs found (same folder name under different paths). "
                "Since issue title is the ID, this would create ambiguous entries. "
                "Use --allow-duplicate-ids to proceed anyway, or narrow --source-root.\n"
                + "Duplicates: "
                + ", ".join(dups[:50])
                + (" ..." if len(dups) > 50 else "")
            )
            return 2

    if str(args.only_skill).strip():
        only = str(args.only_skill).strip()
        skills = [s for s in skills if s.name == only]
        if not skills:
            print(f"No such skill folder under {args.source_repo}/{args.source_root}@{args.source_ref}: {only}")
            return 2

    if args.limit and args.limit > 0:
        skills = skills[: int(args.limit)]

    created = 0
    skipped = 0
    failed = 0

    total = len(skills)
    for idx, skill in enumerate(skills, start=1):
        if args.verbose:
            print(f"[info] ({idx}/{total}) processing skill: {skill.name}")
        repo_url = f"https://github.com/{args.source_repo}/tree/{args.source_ref}/{skill.path.lstrip('/')}"

        if args.skip_existing and repo_url in existing_repo_urls:
            skipped += 1
            print(f"[skip] {skill.name} already exists: {repo_url}")
            continue

        md_file_name, md_content = _pick_skill_markdown(
            api_base=github_api_base,
            repo=args.source_repo,
            ref=args.source_ref,
            skill_path=skill.path,
            token=github_token,
            ctx=github_ctx,
        )
        if args.verbose:
            picked = md_file_name or "(none)"
            print(f"[info] source markdown: {picked}")

        title = _safe_title(skill.name, suffix="")

        try:
            description = _ai_generate_description(
                ai_base_url=ai_base_url,
                ai_api_key=ai_api_key,
                ai_model=ai_model,
                ai_temperature=ai_temperature,
                skill_name=skill.name,
                source_repo_url=repo_url,
                md_file_name=md_file_name or "",
                md_content=md_content or "",
            )

            if args.print_description:
                print(f"[description] {skill.name}: {description}")

            if not description:
                raise RuntimeError("AI returned empty description")

            body = _build_operit_issue_body(description=description, repository_url=repo_url, version=str(args.version))

            if args.print_body:
                print(f"[body] {skill.name}:\n{body}")

            if args.dry_run:
                created += 1
                print(f"[dry-run] create issue: {title} | {repo_url}")
                continue

            try:
                issue = _create_issue(
                    api_base=github_api_base,
                    repo=args.target_repo,
                    token=github_token,
                    ctx=github_ctx,
                    title=title,
                    body=body,
                    labels=labels,
                )
            except Exception as e:
                msg = str(e)
                if "422" in msg:
                    issue = _create_issue(
                        api_base=github_api_base,
                        repo=args.target_repo,
                        token=github_token,
                        ctx=github_ctx,
                        title=title,
                        body=body,
                        labels=[],
                    )
                else:
                    raise

            created += 1
            issue_url = str(issue.get("html_url") or "")
            number = issue.get("number")
            print(f"[ok] #{number} {issue_url}")

        except Exception as e:
            failed += 1
            print(f"[fail] {skill.name}: {e}")

    print(f"Done. created={created} skipped={skipped} failed={failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
