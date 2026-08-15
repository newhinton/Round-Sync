package ca.pkay.rcloneexplorer.data

import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class DuplicateGroup(
    val hashOrKey: String,
    val size: Long,
    val items: List<FileItem>
)

data class BatchRenameRule(
    val prefix: String = "",
    val suffix: String = "",
    val findText: String = "",
    val replaceText: String = "",
    val useSequentialNumbering: Boolean = false,
    val startNumber: Int = 1,
    val numberPadding: Int = 2
)

enum class FileTypeFilter(val displayName: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    ARCHIVES("Archives")
}

object RcloneExtensions {
    private const val TAG = "RcloneExtensions"

    /**
     * Copy a single file or folder to a destination path or remote.
     */
    suspend fun copyItem(
        rclone: Rclone,
        sourceRemote: RemoteItem,
        sourceItem: FileItem,
        destRemote: RemoteItem,
        destPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourcePath = buildRemotePath(sourceRemote, sourceItem.path)
            val destLocation = if (destPath == "//" + destRemote.name || destPath.isEmpty()) {
                "${destRemote.name}:"
            } else {
                "${destRemote.name}:$destPath"
            }

            val fullDest = if (sourceItem.isDir) {
                if (destLocation.endsWith(":")) "$destLocation${sourceItem.name}" else "$destLocation/${sourceItem.name}"
            } else {
                if (destLocation.endsWith(":")) "$destLocation${sourceItem.name}" else "$destLocation/${sourceItem.name}"
            }

            val command = if (sourceItem.isDir) {
                arrayOf("copy", sourcePath, fullDest, "--transfers", "2", "--stats=1s")
            } else {
                arrayOf("copyto", sourcePath, fullDest)
            }

            val process = executeRcloneCommand(rclone, *command)
            process?.waitFor()
            val success = process != null && process.exitValue() == 0
            if (!success && process != null) {
                rclone.logErrorOutput(process)
            }
            success
        } catch (e: Exception) {
            FLog.e(TAG, "copyItem error", e)
            false
        }
    }

    /**
     * Duplicate a file or folder in its current directory with auto-incremented name.
     */
    suspend fun duplicateItem(
        rclone: Rclone,
        remote: RemoteItem,
        item: FileItem,
        existingNames: Set<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldName = item.name
            val newName = generateDuplicateName(oldName, item.isDir, existingNames)
            val parentPath = item.path.substringBeforeLast('/', "")
            val sourceRemotePath = buildRemotePath(remote, item.path)
            val newRelativePath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            val destRemotePath = buildRemotePath(remote, newRelativePath)

            val command = if (item.isDir) {
                arrayOf("copy", sourceRemotePath, destRemotePath)
            } else {
                arrayOf("copyto", sourceRemotePath, destRemotePath)
            }

            val process = executeRcloneCommand(rclone, *command)
            process?.waitFor()
            val success = process != null && process.exitValue() == 0
            if (!success && process != null) {
                rclone.logErrorOutput(process)
            }
            success
        } catch (e: Exception) {
            FLog.e(TAG, "duplicateItem error", e)
            false
        }
    }

    /**
     * Rename a batch of files according to a BatchRenameRule.
     */
    suspend fun batchRename(
        rclone: Rclone,
        remote: RemoteItem,
        items: List<FileItem>,
        rule: BatchRenameRule
    ): List<Pair<FileItem, Boolean>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<FileItem, Boolean>>()
        for ((index, item) in items.withIndex()) {
            val newName = applyRenameRule(item.name, item.isDir, rule, index)
            if (newName == item.name) {
                results.add(item to true)
                continue
            }
            val parentPath = item.path.substringBeforeLast('/', "")
            val oldRemotePath = buildRemotePath(remote, item.path)
            val newRelativePath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            val newRemotePath = buildRemotePath(remote, newRelativePath)

            val process = executeRcloneCommand(rclone, "moveto", oldRemotePath, newRemotePath)
            process?.waitFor()
            val success = process != null && process.exitValue() == 0
            results.add(item to success)
        }
        results
    }

    /**
     * Scan current directory or subtree for duplicates by comparing file sizes and names / hashes.
     */
    suspend fun scanDuplicates(
        rclone: Rclone,
        remote: RemoteItem,
        path: String
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        try {
            val remotePath = buildRemotePath(remote, path.removePrefix("//" + remote.name))
            val process = executeRcloneCommand(rclone, "lsjson", "-R", "--max-depth", "4", remotePath) ?: return@withContext emptyList()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line)
            }
            process.waitFor()
            if (process.exitValue() != 0) return@withContext emptyList()

            val jsonArray = JSONArray(output.toString())
            val fileItems = mutableListOf<FileItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isDir = obj.optBoolean("IsDir", false)
                if (isDir) continue // Duplicates scanned on files

                val itemPath = obj.optString("Path")
                val name = obj.optString("Name")
                val size = obj.optLong("Size", 0)
                val modTime = obj.optString("ModTime", "")
                val mimeType = obj.optString("MimeType", "")

                val fullPath = if (path == "//" + remote.name || path.isEmpty()) itemPath else "${path.trimEnd('/')}/$itemPath"
                fileItems.add(FileItem(remote, fullPath, name, size, modTime, mimeType, false, false))
            }

            // Group by size & name or content length
            val groups = fileItems.groupBy { "${it.name}_${it.size}" }
                .filter { it.value.size > 1 }
                .map { (key, list) ->
                    DuplicateGroup(
                        hashOrKey = key,
                        size = list.first().size,
                        items = list
                    )
                }
            groups
        } catch (e: Exception) {
            FLog.e(TAG, "scanDuplicates error", e)
            emptyList()
        }
    }

    /**
     * Helper to run an arbitrary Rclone command with environment variables.
     */
    private fun executeRcloneCommand(rclone: Rclone, vararg args: String): Process? {
        return try {
            val method = Rclone::class.java.getDeclaredMethod("createCommandWithOptions", Array<String>::class.java)
            method.isAccessible = true
            val command = method.invoke(rclone, args) as Array<String>
            val env = rclone.getRcloneEnv()
            Runtime.getRuntime().exec(command, env)
        } catch (e: Exception) {
            FLog.e(TAG, "executeRcloneCommand failed", e)
            null
        }
    }

    private fun buildRemotePath(remote: RemoteItem, path: String): String {
        val cleanPath = path.removePrefix("//" + remote.name).trimStart('/')
        return if (cleanPath.isEmpty()) "${remote.name}:" else "${remote.name}:$cleanPath"
    }

    fun generateDuplicateName(originalName: String, isDir: Boolean, existingNames: Set<String>): String {
        val nameWithoutExt = if (isDir || !originalName.contains('.')) originalName else originalName.substringBeforeLast('.')
        val ext = if (isDir || !originalName.contains('.')) "" else "." + originalName.substringAfterLast('.')

        var count = 1
        var candidate = "$nameWithoutExt ($count)$ext"
        while (existingNames.contains(candidate)) {
            count++
            candidate = "$nameWithoutExt ($count)$ext"
        }
        return candidate
    }

    fun applyRenameRule(originalName: String, isDir: Boolean, rule: BatchRenameRule, index: Int): String {
        val nameWithoutExt = if (isDir || !originalName.contains('.')) originalName else originalName.substringBeforeLast('.')
        val ext = if (isDir || !originalName.contains('.')) "" else "." + originalName.substringAfterLast('.')

        var modifiedName = nameWithoutExt
        if (rule.findText.isNotEmpty()) {
            modifiedName = modifiedName.replace(rule.findText, rule.replaceText)
        }

        if (rule.useSequentialNumbering) {
            val num = rule.startNumber + index
            val numStr = String.format("%0${rule.numberPadding}d", num)
            modifiedName = "$modifiedName-$numStr"
        }

        return "${rule.prefix}$modifiedName${rule.suffix}$ext"
    }
}
