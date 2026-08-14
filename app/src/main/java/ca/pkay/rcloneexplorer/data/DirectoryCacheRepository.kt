package ca.pkay.rcloneexplorer.data

import ca.pkay.rcloneexplorer.Items.FileItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Global in-memory Directory Cache Repository.
 * Keeps directory listings across fragment navigations and remote switching
 * so that opening any visited remote or folder is always instant (0ms latency).
 * Updated and replaced silently when background/manual refresh finishes or on mutations.
 */
object DirectoryCacheRepository {
    private val cache = ConcurrentHashMap<String, List<FileItem>>()

    private fun buildKey(remoteName: String, path: String): String = "$remoteName:$path"

    fun get(remoteName: String, path: String): List<FileItem>? {
        return cache[buildKey(remoteName, path)]
    }

    fun put(remoteName: String, path: String, files: List<FileItem>) {
        cache[buildKey(remoteName, path)] = files
    }

    fun remove(remoteName: String, path: String) {
        cache.remove(buildKey(remoteName, path))
    }

    fun invalidateRemote(remoteName: String) {
        val keysToRemove = cache.keys.filter { it.startsWith("$remoteName:") }
        keysToRemove.forEach { cache.remove(it) }
    }

    fun clear() {
        cache.clear()
    }
}
