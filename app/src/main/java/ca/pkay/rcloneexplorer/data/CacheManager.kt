package ca.pkay.rcloneexplorer.data

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {
    private const val TAG = "CacheManager"
    const val PREF_KEY_CACHE_LIMIT_MB = "pref_key_cache_limit_mb"
    const val DEFAULT_CACHE_LIMIT_MB = 1024 // 1 GB default

    suspend fun getCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        var total: Long = 0
        val cacheDirs = ContextCompat.getExternalCacheDirs(context)
        for (dir in cacheDirs) {
            if (dir != null && dir.exists()) {
                total += calculateDirSize(dir)
            }
        }
        val internalCache = context.cacheDir
        if (internalCache != null && internalCache.exists()) {
            total += calculateDirSize(internalCache)
        }
        total
    }

    private fun calculateDirSize(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    suspend fun clearCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheDirs = ContextCompat.getExternalCacheDirs(context)
            for (dir in cacheDirs) {
                if (dir != null && dir.exists()) {
                    deleteContents(dir)
                }
            }
            val internalCache = context.cacheDir
            if (internalCache != null && internalCache.exists()) {
                deleteContents(internalCache)
            }
            true
        } catch (e: Exception) {
            FLog.e(TAG, "Error clearing cache", e)
            false
        }
    }

    private fun deleteContents(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteContents(file)
            }
            file.delete()
        }
    }

    suspend fun pruneCacheIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val limitMb = prefs.getInt(PREF_KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
        if (limitMb <= 0) return@withContext // Unlimited

        val limitBytes = limitMb * 1024L * 1024L
        val currentSize = getCacheSizeBytes(context)
        if (currentSize <= limitBytes) return@withContext

        // LRU Eviction: Collect all files and sort by lastModified ascending (oldest first)
        val allFiles = mutableListOf<File>()
        val cacheDirs = ContextCompat.getExternalCacheDirs(context)
        for (dir in cacheDirs) {
            if (dir != null && dir.exists()) {
                collectFiles(dir, allFiles)
            }
        }

        allFiles.sortBy { it.lastModified() }

        var sizeToFree = currentSize - limitBytes
        for (file in allFiles) {
            if (sizeToFree <= 0) break
            val length = file.length()
            if (file.delete()) {
                sizeToFree -= length
            }
        }
    }

    private fun collectFiles(dir: File, list: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                collectFiles(file, list)
            } else {
                list.add(file)
            }
        }
    }
}
