package ca.pkay.rcloneexplorer.data

import android.content.Context
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Global Directory Cache Repository with In-Memory & Persistent Disk Tier.
 * Features smart LRU (Least Recently Used) cache pruning: when the cache limit is reached,
 * it automatically removes the oldest (least-recently-viewed) cached folders rather than
 * stopping new cache storage.
 */
object DirectoryCacheRepository {
    private const val MAX_MEMORY_DIRECTORIES = 300
    private const val MAX_DISK_CACHE_BYTES = 25 * 1024 * 1024L // 25 MB

    private val memoryCache = ConcurrentHashMap<String, List<FileItem>>()
    private val accessTimestamps = ConcurrentHashMap<String, Long>()

    private fun buildKey(remoteName: String, path: String): String = "$remoteName:$path"

    fun get(remoteName: String, path: String): List<FileItem>? {
        val key = buildKey(remoteName, path)
        val items = memoryCache[key]
        if (items != null) {
            accessTimestamps[key] = System.currentTimeMillis()
        }
        return items
    }

    fun getWithDiskFallback(context: Context, remote: RemoteItem, path: String): List<FileItem>? {
        val key = buildKey(remote.name, path)
        val inMem = memoryCache[key]
        if (inMem != null) {
            accessTimestamps[key] = System.currentTimeMillis()
            return inMem
        }

        // Fallback to disk cache if available
        try {
            val cacheDir = File(context.cacheDir, "directory_metadata_cache")
            val sanitized = key.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val file = File(cacheDir, "$sanitized.json")
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArr = JSONArray(jsonStr)
                val items = mutableListOf<FileItem>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val name = obj.getString("name")
                    val itemPath = obj.getString("path")
                    val size = obj.getLong("size")
                    val mimeType = obj.optString("mimeType", "")
                    val isDir = obj.getBoolean("isDir")
                    val modTime = obj.optLong("modTime", 0L)
                    val rfcTime = if (modTime > 0) java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date(modTime)) else ""
                    val item = FileItem(remote, itemPath, name, size, rfcTime, mimeType, isDir, false)
                    items.add(item)
                }
                memoryCache[key] = items
                accessTimestamps[key] = System.currentTimeMillis()
                file.setLastModified(System.currentTimeMillis())
                return items
            }
        } catch (ignored: Exception) {}
        return null
    }

    fun put(remoteName: String, path: String, files: List<FileItem>) {
        val key = buildKey(remoteName, path)
        ensureMemoryQuota()
        memoryCache[key] = files
        accessTimestamps[key] = System.currentTimeMillis()
    }

    fun putWithDiskPersist(context: Context, remoteName: String, path: String, files: List<FileItem>) {
        val key = buildKey(remoteName, path)
        ensureMemoryQuota()
        memoryCache[key] = files
        accessTimestamps[key] = System.currentTimeMillis()

        try {
            val cacheDir = File(context.cacheDir, "directory_metadata_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            ensureDiskQuota(context, cacheDir)

            val sanitized = key.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val file = File(cacheDir, "$sanitized.json")
            val jsonArr = JSONArray()
            for (f in files) {
                val obj = JSONObject().apply {
                    put("name", f.name)
                    put("path", f.path)
                    put("size", f.size)
                    put("mimeType", f.mimeType ?: "")
                    put("isDir", f.isDir)
                    put("modTime", f.modTime)
                }
                jsonArr.put(obj)
            }
            file.writeText(jsonArr.toString())
        } catch (ignored: Exception) {}
    }

    private fun ensureMemoryQuota() {
        if (memoryCache.size >= MAX_MEMORY_DIRECTORIES) {
            // Sort by access time ascending (oldest first) and remove 25% oldest
            val entriesToPrune = accessTimestamps.entries
                .sortedBy { it.value }
                .take(MAX_MEMORY_DIRECTORIES / 4)
            for (entry in entriesToPrune) {
                memoryCache.remove(entry.key)
                accessTimestamps.remove(entry.key)
            }
        }
    }

    private fun ensureDiskQuota(context: Context, cacheDir: File) {
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        val maxBudget = try {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getLong("pref_key_telemetry_cache_budget", 52428800L)
        } catch (e: Exception) {
            52428800L
        }
        if (totalSize > maxBudget) {
            // Sort disk cache files by lastModified ascending (oldest viewed first)
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (f in sortedFiles) {
                totalSize -= f.length()
                f.delete()
                if (totalSize <= maxBudget * 0.75) {
                    break
                }
            }
        }
    }

    fun remove(remoteName: String, path: String) {
        val key = buildKey(remoteName, path)
        memoryCache.remove(key)
        accessTimestamps.remove(key)
    }

    fun removeWithDisk(context: Context, remoteName: String, path: String) {
        val key = buildKey(remoteName, path)
        memoryCache.remove(key)
        accessTimestamps.remove(key)
        try {
            val cacheDir = File(context.cacheDir, "directory_metadata_cache")
            val sanitized = key.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val file = File(cacheDir, "$sanitized.json")
            if (file.exists()) file.delete()
        } catch (ignored: Exception) {}
    }

    fun invalidateRemote(remoteName: String) {
        val keysToRemove = memoryCache.keys.filter { it.startsWith("$remoteName:") }
        keysToRemove.forEach {
            memoryCache.remove(it)
            accessTimestamps.remove(it)
        }
    }

    fun clear(context: Context? = null) {
        memoryCache.clear()
        accessTimestamps.clear()
        if (context != null) {
            try {
                val cacheDir = File(context.cacheDir, "directory_metadata_cache")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
            } catch (ignored: Exception) {}
        }
    }
}
