package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.GitIgnoreFilter
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.github.difflib.DiffUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class FileStat(
    val size: Long,
    val lastModified: Long
)

@Serializable
data class BackupManifest(
    val timestamp: Long,
    val files: Map<String, String>, // relativePath -> hash
    val fileStats: Map<String, FileStat> = emptyMap() // relativePath -> (size, lastModified)
)

class WorkspaceBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "WorkspaceBackupManager"
        private const val BACKUP_DIR_NAME = ".backup"
        private const val OBJECTS_DIR_NAME = "objects"

        @Volatile
        private var INSTANCE: WorkspaceBackupManager? = null

        fun getInstance(context: Context): WorkspaceBackupManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkspaceBackupManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val toolHandler by lazy { AIToolHandler.getInstance(context) }

    data class WorkspaceFileChange(
        val path: String,
        val changeType: ChangeType,
        val changedLines: Int
    )

    enum class ChangeType {
        ADDED,
        DELETED,
        MODIFIED
    }

    /**
     * Synchronizes the workspace state based on the message timestamp.
     * It either creates a new backup or restores to a previous state.
     */
    suspend fun syncState(workspacePath: String, messageTimestamp: Long, workspaceEnv: String? = null) {
        syncStateProvider(workspacePath, workspaceEnv, messageTimestamp)
    }

    private fun withWorkspaceEnvParams(base: List<ToolParameter>, workspaceEnv: String?): List<ToolParameter> {
        if (workspaceEnv.isNullOrBlank()) return base
        return base + ToolParameter("environment", workspaceEnv)
    }

    private fun joinPath(parent: String, child: String): String {
        val p = parent.trimEnd('/')
        val c = child.trimStart('/')
        return if (p.isEmpty()) "/$c" else "$p/$c"
    }

    private fun makeRelativePath(root: String, fullPath: String): String? {
        val normalizedRoot = root.trimEnd('/')
        if (normalizedRoot.isBlank()) return null
        if (!fullPath.startsWith(normalizedRoot)) return null
        return fullPath.removePrefix(normalizedRoot).trimStart('/').ifBlank { "" }
    }

    private fun objectBucketPrefix(hash: String): String {
        if (hash.length < 2) return "__"
        return hash.substring(0, 2)
    }

    private fun buildShardedObjectPath(objectsDir: String, hash: String): String {
        return joinPath(joinPath(objectsDir, objectBucketPrefix(hash)), hash)
    }

    private fun buildLegacyObjectPath(objectsDir: String, hash: String): String {
        return joinPath(objectsDir, hash)
    }

    private suspend fun resolveObjectPathForRead(objectsDir: String, hash: String, workspaceEnv: String?): String? {
        val sharded = buildShardedObjectPath(objectsDir, hash)
        if (fileExistsProvider(sharded, workspaceEnv)?.exists == true) return sharded

        val legacy = buildLegacyObjectPath(objectsDir, hash)
        if (fileExistsProvider(legacy, workspaceEnv)?.exists == true) return legacy

        return null
    }

    private suspend fun ensureDirectory(path: String, workspaceEnv: String?) {
        toolHandler.executeTool(
            AITool(
                name = "make_directory",
                parameters = withWorkspaceEnvParams(
                    listOf(
                        ToolParameter("path", path),
                        ToolParameter("create_parents", "true")
                    ),
                    workspaceEnv
                )
            )
        )
    }

    private suspend fun listBackupsInBackupDir(backupDir: String, workspaceEnv: String?): List<Long> {
        val listRes =
            toolHandler.executeTool(
                AITool(
                    name = "list_files",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", backupDir)), workspaceEnv)
                )
            )

        val listing = listRes.result as? DirectoryListingData
        val entries = listing?.entries.orEmpty()
        return entries
            .asSequence()
            .filter { !it.isDirectory }
            .map { it.name }
            .filter { it.endsWith(".json") }
            .mapNotNull { it.removeSuffix(".json").toLongOrNull() }
            .sorted()
            .toList()
    }

    private suspend fun loadBackupManifestProvider(backupDir: String, targetTimestamp: Long, workspaceEnv: String?): BackupManifest? {
        val manifestPath = joinPath(backupDir, "$targetTimestamp.json")
        val readRes =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_full",
                    parameters = withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", manifestPath),
                            ToolParameter("text_only", "true")
                        ),
                        workspaceEnv
                    )
                )
            )
        val content = (readRes.result as? FileContentData)?.content
        if (!readRes.success || content.isNullOrBlank()) {
            return null
        }
        return runCatching { json.decodeFromString<BackupManifest>(content) }.getOrNull()
    }

    private suspend fun readBinaryBase64(path: String, workspaceEnv: String?): String? {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_binary",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
                )
            )
        return (res.result as? BinaryFileContentData)?.contentBase64
    }

    private suspend fun writeBinaryBase64(path: String, contentBase64: String, workspaceEnv: String?) {
        toolHandler.executeTool(
            AITool(
                name = "write_file_binary",
                parameters = withWorkspaceEnvParams(
                    listOf(
                        ToolParameter("path", path),
                        ToolParameter("base64Content", contentBase64)
                    ),
                    workspaceEnv
                )
            )
        )
    }

    private suspend fun deleteFileProvider(path: String, workspaceEnv: String?) {
        toolHandler.executeTool(
            AITool(
                name = "delete_file",
                parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
            )
        )
    }

    private suspend fun fileExistsProvider(path: String, workspaceEnv: String?): FileExistsData? {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "file_exists",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
                )
            )
        return res.result as? FileExistsData
    }

    private suspend fun fileInfoProvider(path: String, workspaceEnv: String?): FileInfoData? {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "file_info",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
                )
            )
        return res.result as? FileInfoData
    }

    private fun parseLastModifiedToMillis(lastModified: String): Long? {
        val raw = lastModified.trim()
        if (raw.isBlank()) return null

        val patterns = listOf("yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = true
                val date = sdf.parse(raw) ?: continue
                return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private suspend fun loadGitignoreRulesProvider(workspacePath: String, workspaceEnv: String?): List<String> {
        val rules = mutableListOf<String>()
        rules.addAll(listOf(".backup", ".operit"))

        val gitignorePath = joinPath(workspacePath, ".gitignore")
        val readRes =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_full",
                    parameters = withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", gitignorePath),
                            ToolParameter("text_only", "true")
                        ),
                        workspaceEnv
                    )
                )
            )
        val content = (readRes.result as? FileContentData)?.content
        if (readRes.success && !content.isNullOrBlank()) {
            content
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { rules.add(it) }
        }

        return rules
    }

    private suspend fun listWorkspaceTextFilesProvider(workspacePath: String, workspaceEnv: String?, gitignoreRules: List<String>): List<String> {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "find_files",
                    parameters = withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", workspacePath),
                            ToolParameter("pattern", "*"),
                            ToolParameter("use_path_pattern", "false"),
                            ToolParameter("case_insensitive", "false")
                        ),
                        workspaceEnv
                    )
                )
            )
        val all = (res.result as? FindFilesResultData)?.files.orEmpty()

        val normalizedRoot = workspacePath.trimEnd('/')
        return all
            .asSequence()
            .mapNotNull { fullPath ->
                val rel = makeRelativePath(normalizedRoot, fullPath) ?: return@mapNotNull null
                if (rel.isBlank()) return@mapNotNull null
                val name = rel.substringAfterLast('/')
                if (!FileUtils.isTextBasedFileName(name)) return@mapNotNull null
                if (GitIgnoreFilter.shouldIgnore(rel, name, isDirectory = false, rules = gitignoreRules)) return@mapNotNull null
                fullPath
            }
            .toList()
    }

    private suspend fun syncStateProvider(workspacePath: String, workspaceEnv: String?, messageTimestamp: Long) {
        withContext(Dispatchers.IO) {
            val exists = fileExistsProvider(workspacePath, workspaceEnv)
            if (exists == null || !exists.exists || !exists.isDirectory) {
                AppLogger.w(TAG, "Workspace path does not exist or is not a directory: $workspacePath")
                return@withContext
            }

            val backupDir = joinPath(workspacePath, BACKUP_DIR_NAME)
            ensureDirectory(backupDir, workspaceEnv)
            val existingBackups = listBackupsInBackupDir(backupDir, workspaceEnv)
            AppLogger.d(TAG, "syncState called for timestamp: $messageTimestamp. Existing backups: $existingBackups")

            val newerBackups = existingBackups.filter { it > messageTimestamp }
            if (newerBackups.isNotEmpty()) {
                val restoreTimestamp = newerBackups.first()
                AppLogger.i(TAG, "Newer backups found. Rewinding workspace to state at $restoreTimestamp")
                AppLogger.d(TAG, "[Rewind] Calculated restoreTimestamp: $restoreTimestamp")
                restoreToStateProvider(workspacePath, workspaceEnv, backupDir, restoreTimestamp)

                val backupsToDelete = newerBackups.filter { it >= restoreTimestamp }
                AppLogger.d(TAG, "[Rewind] Backups to be deleted: $backupsToDelete")
                AppLogger.d(TAG, "Deleting backups from $restoreTimestamp onwards: $backupsToDelete")
                backupsToDelete.forEach { ts ->
                    deleteFileProvider(joinPath(backupDir, "$ts.json"), workspaceEnv)
                }
                AppLogger.i(TAG, "Deleted ${backupsToDelete.size} newer backup manifests.")
            } else {
                if (existingBackups.contains(messageTimestamp)) {
                    AppLogger.d(TAG, "Backup for timestamp $messageTimestamp already exists. Skipping creation.")
                    return@withContext
                }
                AppLogger.i(TAG, "No newer backups found for timestamp $messageTimestamp. Creating a new backup.")
                createNewBackupProvider(workspacePath, workspaceEnv, backupDir, messageTimestamp, existingBackups)
            }
        }
    }

    private suspend fun createNewBackupProvider(
        workspacePath: String,
        workspaceEnv: String?,
        backupDir: String,
        newTimestamp: Long,
        existingBackups: List<Long>
    ) {
        val startMs = SystemClock.elapsedRealtime()
        val objectsDir = joinPath(backupDir, OBJECTS_DIR_NAME)
        ensureDirectory(objectsDir, workspaceEnv)

        val newManifestFiles = mutableMapOf<String, String>()
        val newManifestFileStats = mutableMapOf<String, FileStat>()

        val previousManifest = existingBackups.lastOrNull()?.let { loadBackupManifestProvider(backupDir, it, workspaceEnv) }
        val previousFiles = previousManifest?.files ?: emptyMap()
        val previousStats = previousManifest?.fileStats ?: emptyMap()

        val gitignoreRules = loadGitignoreRulesProvider(workspacePath, workspaceEnv)
        val workspaceFiles = listWorkspaceTextFilesProvider(workspacePath, workspaceEnv, gitignoreRules)

        var reusedCount = 0
        var hashedCount = 0
        var objectsWrittenCount = 0
        var statMissingCount = 0

        for (filePath in workspaceFiles) {
            try {
                val relativePath = makeRelativePath(workspacePath, filePath) ?: continue

                val info = fileInfoProvider(filePath, workspaceEnv)
                val infoSize = info?.size
                val infoLastModifiedMs = info?.lastModified?.let { parseLastModifiedToMillis(it) }
                val currentStat =
                    if (infoSize != null && infoLastModifiedMs != null) {
                        FileStat(size = infoSize, lastModified = infoLastModifiedMs)
                    } else {
                        statMissingCount += 1
                        null
                    }

                val previousHash = previousFiles[relativePath]
                val previousStat = previousStats[relativePath]

                val canReuse =
                    previousHash != null &&
                        currentStat != null &&
                        previousStat != null &&
                        currentStat.size == previousStat.size &&
                        currentStat.lastModified == previousStat.lastModified

                val hash: String
                val stat: FileStat
                if (canReuse) {
                    hash = requireNotNull(previousHash)
                    stat = requireNotNull(currentStat)
                    reusedCount += 1
                } else {
                    val base64 = readBinaryBase64(filePath, workspaceEnv) ?: continue
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val md = MessageDigest.getInstance("SHA-256")
                    hash = md.digest(bytes).joinToString("") { "%02x".format(it) }
                    stat = currentStat ?: FileStat(size = bytes.size.toLong(), lastModified = 0L)
                    hashedCount += 1

                    val objectPath = buildShardedObjectPath(objectsDir, hash)
                    val objectExists = fileExistsProvider(objectPath, workspaceEnv)?.exists == true
                    if (!objectExists) {
                        val bucketDir = joinPath(objectsDir, objectBucketPrefix(hash))
                        ensureDirectory(bucketDir, workspaceEnv)
                        writeBinaryBase64(objectPath, base64, workspaceEnv)
                        objectsWrittenCount += 1
                    }
                }

                newManifestFiles[relativePath] = hash
                newManifestFileStats[relativePath] = stat
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to process file for backup: $filePath", e)
            }
        }

        val manifest =
            BackupManifest(
                timestamp = newTimestamp,
                files = newManifestFiles,
                fileStats = newManifestFileStats
            )
        val manifestPath = joinPath(backupDir, "$newTimestamp.json")
        toolHandler.executeTool(
            AITool(
                name = "write_file",
                parameters = withWorkspaceEnvParams(
                    listOf(
                        ToolParameter("path", manifestPath),
                        ToolParameter("content", json.encodeToString(manifest)),
                        ToolParameter("append", "false")
                    ),
                    workspaceEnv
                )
            )
        )

        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        AppLogger.i(
            TAG,
            "Workspace backup completed in ${elapsedMs}ms (timestamp=$newTimestamp, files=${workspaceFiles.size}, reused=$reusedCount, hashed=$hashedCount, objectsWritten=$objectsWrittenCount, statMissing=$statMissingCount)"
        )
    }

    private suspend fun restoreToStateProvider(
        workspacePath: String,
        workspaceEnv: String?,
        backupDir: String,
        targetTimestamp: Long?
    ) {
        val objectsDir = joinPath(backupDir, OBJECTS_DIR_NAME)
        AppLogger.d(TAG, "Attempting to restore workspace to timestamp: $targetTimestamp")
        val targetManifest = if (targetTimestamp != null) {
            loadBackupManifestProvider(backupDir, targetTimestamp, workspaceEnv)
        } else {
            null
        }

        val manifestFiles = targetManifest?.files ?: emptyMap()
        val manifestRelativePaths = manifestFiles.keys

        val gitignoreRules = loadGitignoreRulesProvider(workspacePath, workspaceEnv)
        val workspaceFiles = listWorkspaceTextFilesProvider(workspacePath, workspaceEnv, gitignoreRules)

        AppLogger.d(TAG, "Step 1: Deleting tracked files not present in the target manifest...")
        for (currentFilePath in workspaceFiles) {
            val rel = makeRelativePath(workspacePath, currentFilePath) ?: continue
            if (rel !in manifestRelativePaths) {
                AppLogger.i(TAG, "Deleting tracked text file not in manifest: $rel")
                deleteFileProvider(currentFilePath, workspaceEnv)
            }
        }

        AppLogger.d(TAG, "Step 2: Restoring and updating files from the target manifest...")
        for ((relativePath, hash) in manifestFiles) {
            val targetPath = joinPath(workspacePath, relativePath)

            val needsCopy =
                run {
                    val targetExists = fileExistsProvider(targetPath, workspaceEnv)?.exists == true
                    if (!targetExists) {
                        true
                    } else {
                        val targetBase64 = readBinaryBase64(targetPath, workspaceEnv) ?: return@run true
                        val bytes = Base64.decode(targetBase64, Base64.DEFAULT)
                        val md = MessageDigest.getInstance("SHA-256")
                        val currentHash = md.digest(bytes).joinToString("") { "%02x".format(it) }
                        currentHash != hash
                    }
                }

            if (!needsCopy) continue

            val objectPath = resolveObjectPathForRead(objectsDir, hash, workspaceEnv)
            if (objectPath == null) {
                AppLogger.e(TAG, "Object file not found for hash $hash, cannot restore $relativePath")
                continue
            }

            val parent = targetPath.substringBeforeLast('/', "")
            if (parent.isNotBlank()) {
                ensureDirectory(parent, workspaceEnv)
            }
            AppLogger.i(TAG, "Restoring file: $relativePath")
            val objectBase64 = readBinaryBase64(objectPath, workspaceEnv) ?: continue
            writeBinaryBase64(targetPath, objectBase64, workspaceEnv)
        }
    }

    private fun normalizeTextLinesForDiff(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        return normalized.split('\n')
    }

    private fun estimateChangedLines(beforeText: String, afterText: String): Int {
        if (beforeText == afterText) return 0
        return try {
            val beforeLines = normalizeTextLinesForDiff(beforeText)
            val afterLines = normalizeTextLinesForDiff(afterText)
            val patch = DiffUtils.diff(beforeLines, afterLines)
            var changed = 0
            for (delta in patch.deltas) {
                when (delta.type) {
                    com.github.difflib.patch.DeltaType.INSERT -> changed += delta.target.lines.size
                    com.github.difflib.patch.DeltaType.DELETE -> changed += delta.source.lines.size
                    com.github.difflib.patch.DeltaType.CHANGE -> {
                        val a = delta.source.lines.size
                        val b = delta.target.lines.size
                        changed += if (a > b) a else b
                    }
                    else -> Unit
                }
            }
            changed
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to estimate changed lines", e)
            0
        }
    }

    suspend fun previewChanges(workspacePath: String, targetTimestamp: Long, workspaceEnv: String? = null): List<WorkspaceFileChange> {
        return previewChangesProvider(workspacePath, workspaceEnv, targetTimestamp)
    }

    suspend fun previewChangesForRewind(workspacePath: String, workspaceEnv: String?, rewindTimestamp: Long): List<WorkspaceFileChange> {
        val backupDir = joinPath(workspacePath, BACKUP_DIR_NAME)
        val existingBackups = listBackupsInBackupDir(backupDir, workspaceEnv)
        val newerBackups = existingBackups.filter { it > rewindTimestamp }
        if (newerBackups.isEmpty()) return emptyList()
        val restoreTimestamp = newerBackups.first()
        return previewChangesProvider(workspacePath, workspaceEnv, restoreTimestamp)
    }

    private suspend fun previewChangesProvider(workspacePath: String, workspaceEnv: String?, targetTimestamp: Long): List<WorkspaceFileChange> {
        return withContext(Dispatchers.IO) {
            val exists = fileExistsProvider(workspacePath, workspaceEnv)
            if (exists == null || !exists.exists || !exists.isDirectory) {
                AppLogger.w(TAG, "Workspace path does not exist or is not a directory: $workspacePath")
                return@withContext emptyList()
            }

            val backupDir = joinPath(workspacePath, BACKUP_DIR_NAME)
            val objectsDir = joinPath(backupDir, OBJECTS_DIR_NAME)
            val gitignoreRules = loadGitignoreRulesProvider(workspacePath, workspaceEnv)

            val targetManifest = loadBackupManifestProvider(backupDir, targetTimestamp, workspaceEnv)
            val manifestFiles = targetManifest?.files ?: emptyMap()
            val manifestRelativePaths = manifestFiles.keys

            val changes = mutableListOf<WorkspaceFileChange>()

            val workspaceFiles = listWorkspaceTextFilesProvider(workspacePath, workspaceEnv, gitignoreRules)
            for (currentFilePath in workspaceFiles) {
                val relativePath = makeRelativePath(workspacePath, currentFilePath) ?: continue
                if (relativePath !in manifestRelativePaths) {
                    val currentBase64 = readBinaryBase64(currentFilePath, workspaceEnv)
                    val currentText =
                        currentBase64
                            ?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() }
                    val lines = currentText?.let { normalizeTextLinesForDiff(it).size } ?: 0
                    changes.add(WorkspaceFileChange(relativePath, ChangeType.DELETED, lines))
                } else {
                    val objectFileName = manifestFiles[relativePath] ?: continue
                    val objectPath = resolveObjectPathForRead(objectsDir, objectFileName, workspaceEnv)
                    if (objectPath != null) {
                        val currentBase64 = readBinaryBase64(currentFilePath, workspaceEnv)
                        val objectBase64 = readBinaryBase64(objectPath, workspaceEnv)
                        if (currentBase64 != null && objectBase64 != null && currentBase64 != objectBase64) {
                            val currentText =
                                runCatching { String(Base64.decode(currentBase64, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                            val objectText =
                                runCatching { String(Base64.decode(objectBase64, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                            val changedLines =
                                if (currentText != null && objectText != null) {
                                    estimateChangedLines(currentText, objectText)
                                } else {
                                    0
                                }
                            if (changedLines > 0) {
                                changes.add(WorkspaceFileChange(relativePath, ChangeType.MODIFIED, changedLines))
                            }
                        }
                    }
                }
            }

            for ((relativePath, hash) in manifestFiles) {
                val targetPath = joinPath(workspacePath, relativePath)
                val targetExists = fileExistsProvider(targetPath, workspaceEnv)?.exists == true
                if (!targetExists) {
                    val objectPath = resolveObjectPathForRead(objectsDir, hash, workspaceEnv)
                    if (objectPath != null) {
                        val objectBase64 = readBinaryBase64(objectPath, workspaceEnv)
                        val objectText =
                            objectBase64
                                ?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() }
                        val lines = objectText?.let { normalizeTextLinesForDiff(it).size } ?: 0
                        changes.add(WorkspaceFileChange(relativePath, ChangeType.ADDED, lines))
                    }
                }
            }

            changes
        }
    }
}