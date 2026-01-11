import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional


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


def _repo_root() -> Path:
    import subprocess

    p = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if p.returncode != 0:
        raise RuntimeError("Not a git repository (or git not installed).")
    return Path(p.stdout.strip())


def _get_origin_url(repo_root: Path) -> str:
    import subprocess

    p = subprocess.run(
        ["git", "remote", "get-url", "origin"],
        cwd=str(repo_root),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if p.returncode != 0:
        return ""
    return p.stdout.strip()


def _infer_repo_from_origin(origin_url: str) -> str:
    origin_url = origin_url.strip()
    if not origin_url:
        return ""

    # git@github.com:owner/repo.git
    m = re.match(r"^git@github\.com:([^/]+)/([^/]+?)(?:\.git)?$", origin_url)
    if m:
        return f"{m.group(1)}/{m.group(2)}"

    # https://github.com/owner/repo.git
    m = re.match(r"^https?://github\.com/([^/]+)/([^/]+?)(?:\.git)?/?$", origin_url)
    if m:
        return f"{m.group(1)}/{m.group(2)}"

    return ""


def _request_json(url: str, token: str) -> tuple[list, dict]:
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if token:
        req.add_header("Authorization", f"Bearer {token}")

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = resp.read().decode("utf-8")
            data = json.loads(body)
            headers = {k.lower(): v for k, v in resp.headers.items()}
            if not isinstance(data, list):
                raise RuntimeError(f"Unexpected GitHub response (expected list): {body[:500]}")
            return data, headers
    except urllib.error.HTTPError as e:
        body = ""
        try:
            body = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(f"GitHub API error: HTTP {e.code} {e.reason}\n{body}")


def _parse_link_next(link_header: str) -> str:
    # <url>; rel="next", <url>; rel="last"
    if not link_header:
        return ""
    parts = [p.strip() for p in link_header.split(",")]
    for part in parts:
        if 'rel="next"' in part or "rel=next" in part:
            m = re.search(r"<([^>]+)>", part)
            if m:
                return m.group(1)
    return ""


@dataclass
class IssueItem:
    number: int
    title: str
    html_url: str
    state: str
    created_at: str
    updated_at: str
    labels: list[str]
    assignees: list[str]
    comments: int


def _iter_issues(api_base: str, repo: str, token: str, state: str) -> Iterable[IssueItem]:
    owner, name = repo.split("/", 1)

    params = {
        "state": state,
        "per_page": "100",
        "sort": "updated",
        "direction": "desc",
    }
    url = (
        api_base.rstrip("/")
        + f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/issues"
        + "?"
        + urllib.parse.urlencode(params)
    )

    while url:
        data, headers = _request_json(url, token=token)
        for it in data:
            if not isinstance(it, dict):
                continue
            if "pull_request" in it:
                continue
            yield IssueItem(
                number=int(it.get("number") or 0),
                title=str(it.get("title") or ""),
                html_url=str(it.get("html_url") or ""),
                state=str(it.get("state") or ""),
                created_at=str(it.get("created_at") or ""),
                updated_at=str(it.get("updated_at") or ""),
                labels=[str(l.get("name")) for l in (it.get("labels") or []) if isinstance(l, dict)],
                assignees=[
                    str(a.get("login"))
                    for a in (it.get("assignees") or [])
                    if isinstance(a, dict)
                ],
                comments=int(it.get("comments") or 0),
            )

        url = _parse_link_next(headers.get("link", ""))


def _to_markdown(issues: list[IssueItem], repo: str, state: str) -> str:
    lines: list[str] = []
    lines.append(f"# GitHub Issues ({repo})")
    lines.append("")
    lines.append(f"State: `{state}`")
    lines.append(f"Count: `{len(issues)}`")
    lines.append("")

    for it in issues:
        labels = ", ".join(it.labels) if it.labels else ""
        assignees = ", ".join(it.assignees) if it.assignees else ""

        meta_parts = []
        if labels:
            meta_parts.append(f"labels: {labels}")
        if assignees:
            meta_parts.append(f"assignees: {assignees}")
        meta_parts.append(f"comments: {it.comments}")
        meta_parts.append(f"updated: {it.updated_at}")
        meta = " | ".join(meta_parts)

        title = it.title.replace("\r", " ").replace("\n", " ").strip()
        lines.append(f"- #{it.number} [{title}]({it.html_url}) ({it.state})")
        lines.append(f"  - {meta}")

    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fetch GitHub issues for the current repo and write a Markdown list for manual review."
    )
    parser.add_argument(
        "--env",
        default=str(Path(__file__).resolve().parent / ".env"),
        help="Path to .env file (default: tools/github/.env)",
    )
    parser.add_argument(
        "--repo",
        default="",
        help="GitHub repo in owner/name form. If omitted, uses GITHUB_REPO env or infers from git origin.",
    )
    parser.add_argument(
        "--state",
        default="all",
        choices=["open", "closed", "all"],
        help="Issue state to fetch (default: all)",
    )
    parser.add_argument(
        "--out",
        default="",
        help="Output markdown file path (default: tools/github/issues_<state>.md)",
    )
    args = parser.parse_args()

    _load_env(Path(args.env))

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    api_base = os.environ.get("GITHUB_API_URL", "https://api.github.com").strip() or "https://api.github.com"

    repo = (args.repo or os.environ.get("GITHUB_REPO", "") or "").strip()
    if not repo:
        repo_root = _repo_root()
        origin = _get_origin_url(repo_root)
        repo = _infer_repo_from_origin(origin)

    if not repo or "/" not in repo:
        print(
            "Missing repo. Provide --repo owner/name or set GITHUB_REPO, or ensure git remote origin is a GitHub URL.",
            file=sys.stderr,
        )
        return 2

    issues = list(_iter_issues(api_base=api_base, repo=repo, token=token, state=args.state))

    if args.out:
        out_path = Path(args.out)
    else:
        out_path = Path(__file__).resolve().parent / f"issues_{args.state}.md"

    out_path.write_text(_to_markdown(issues, repo=repo, state=args.state), encoding="utf-8")
    print(f"Wrote: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
