package com.ai.assistance.operit.data.skill

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.SkillRepoZipPoolManager
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillRepository private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: SkillRepository? = null

        private const val TAG = "SkillRepository"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
        private const val BUFFER_SIZE = 64 * 1024

        fun getInstance(context: Context): SkillRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkillRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val skillManager by lazy { SkillManager.getInstance(context) }

    private val skillVisibilityPreferences by lazy { SkillVisibilityPreferences.getInstance(context) }

    private val defaultBranchCache = ConcurrentHashMap<String, String>()

    private data class GitHubSkillTarget(
        val owner: String,
        val repo: String,
        val ref: String?,
        val subDir: String?
    )

    fun getSkillsDirectoryPath(): String = skillManager.getSkillsDirectoryPath()

    fun getAvailableSkillPackages(): Map<String, SkillPackage> = skillManager.getAvailableSkills()

    fun getAiVisibleSkillPackages(): Map<String, SkillPackage> {
        return skillManager.getAvailableSkills().filter { (skillName, _) ->
            skillVisibilityPreferences.isSkillVisibleToAi(skillName)
        }
    }

    fun readSkillContent(skillName: String): String? = skillManager.readSkillContent(skillName)

    fun deleteSkill(skillName: String): Boolean = skillManager.deleteSkill(skillName)

    suspend fun importSkillFromZip(zipFile: File): String {
        return withContext(Dispatchers.IO) {
            skillManager.importSkillFromZip(zipFile)
        }
    }

    suspend fun importSkillFromGitHubRepo(repoUrl: String): String {
        return withContext(Dispatchers.IO) {
            val target = parseGitHubSkillTarget(repoUrl)
                ?: return@withContext context.getString(R.string.skill_invalid_github_url)

            val owner = target.owner
            val repoName = target.repo
            val repoKey = "$owner/$repoName"
            val ref = target.ref
                ?: defaultBranchCache[repoKey]
                ?: getGithubDefaultBranch(owner, repoName)?.also { defaultBranchCache[repoKey] = it }
                ?: return@withContext context.getString(R.string.skill_cannot_determine_default_branch, "$owner/$repoName")

            val encodedRef = encodePathSegment(ref)
            val zipUrl = "https://codeload.github.com/$owner/$repoName/zip/$encodedRef"
            val repoRefKey = "$owner/$repoName@$ref"
            val pooledZip = SkillRepoZipPoolManager.getOrDownloadZip(repoRefKey) { outFile ->
                downloadFromUrl(zipUrl, outFile)
            }

            val suffix = (target.subDir ?: "repo")
                .replace('/', '_')
                .take(60)
            val fallbackTempFile = File(context.cacheDir, "skill_${owner}_${repoName}_$suffix.zip")
            if (pooledZip == null) {
                if (fallbackTempFile.exists()) fallbackTempFile.delete()
            }

            try {
                val skillsRootDir = File(getSkillsDirectoryPath())
                val beforeDirs = skillsRootDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()

                val zipFile = if (pooledZip != null) {
                    pooledZip
                } else {
                    val downloaded = downloadFromUrl(zipUrl, fallbackTempFile)
                    if (!downloaded || !fallbackTempFile.exists() || fallbackTempFile.length() <= 0L) {
                        if (fallbackTempFile.exists()) fallbackTempFile.delete()
                        return@withContext context.getString(R.string.skill_download_zip_failed)
                    }
                    fallbackTempFile
                }

                val result = skillManager.importSkillFromZip(zipFile, target.subDir)

                if (pooledZip == null) {
                    runCatching { fallbackTempFile.delete() }
                }

                // Write repoUrl marker for reliable installed-state detection.
                if (result.startsWith(context.getString(R.string.skill_imported))) {
                    val afterDirs = skillsRootDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
                    val newDirs = afterDirs - beforeDirs
                    val newDirName = newDirs.singleOrNull()
                    if (!newDirName.isNullOrBlank()) {
                        try {
                            File(skillsRootDir, newDirName)
                                .resolve(".operit_repo_url")
                                .writeText(repoUrl.trim())
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to write .operit_repo_url marker", e)
                        }
                    }
                }

                result
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to import skill from GitHub repo", e)
                if (pooledZip == null && fallbackTempFile.exists()) fallbackTempFile.delete()
                context.getString(R.string.skill_import_failed, e.message ?: "Unknown error")
            }
        }
    }

    private fun parseGitHubSkillTarget(inputUrlRaw: String): GitHubSkillTarget? {
        val inputUrl = inputUrlRaw.trim()
        if (inputUrl.isBlank()) return null

        val urlWithScheme = if (inputUrl.startsWith("http://", ignoreCase = true) || inputUrl.startsWith("https://", ignoreCase = true)) {
            inputUrl
        } else {
            "https://$inputUrl"
        }

        val urlNoFragment = urlWithScheme.substringBefore('#')
        val uri = try {
            URI(urlNoFragment)
        } catch (_: Exception) {
            return null
        }

        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null

        fun cleanRepoName(repoRaw: String): String {
            return repoRaw.removeSuffix(".git")
        }

        return when {
            host == "github.com" || host.endsWith(".github.com") -> {
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                if (owner.isBlank() || repo.isBlank()) return null

                var ref: String? = null
                var subDir: String? = null

                if (segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob")) {
                    ref = segments[3]
                    val remainder = if (segments.size > 4) segments.subList(4, segments.size).joinToString("/") else ""
                    if (remainder.isNotBlank()) {
                        subDir = if (segments[2] == "blob") {
                            if (remainder.endsWith("SKILL.md", ignoreCase = true) || remainder.endsWith("skill.md", ignoreCase = true)) {
                                remainder.substringBeforeLast('/')
                            } else {
                                remainder.substringBeforeLast('/').ifBlank { null }
                            }
                        } else {
                            remainder
                        }
                    }
                }

                GitHubSkillTarget(owner = owner, repo = repo, ref = ref, subDir = subDir)
            }

            host == "raw.githubusercontent.com" -> {
                if (segments.size < 4) return null
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                val ref = segments[2]
                val remainder = segments.subList(3, segments.size).joinToString("/")
                val subDir = if (remainder.endsWith("SKILL.md", ignoreCase = true) || remainder.endsWith("skill.md", ignoreCase = true)) {
                    remainder.substringBeforeLast('/')
                } else {
                    remainder.substringBeforeLast('/').ifBlank { null }
                }
                GitHubSkillTarget(owner = owner, repo = repo, ref = ref, subDir = subDir)
            }

            else -> null
        }
    }

    private fun encodePathSegment(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }

    private fun downloadFromUrl(zipUrl: String, outFile: File): Boolean {
        val url = URL(zipUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doInput = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }

        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            AppLogger.e(TAG, "Download failed, HTTP ${connection.responseCode}")
            return false
        }

        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        return true
    }

    private fun getGithubDefaultBranch(owner: String, repoName: String): String? {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        return try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JsonParser.parseString(response).asJsonObject
                jsonObject.get("default_branch")?.asString
            } else {
                AppLogger.e(TAG, "GitHub API failed, HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch GitHub default branch", e)
            null
        }
    }
}
