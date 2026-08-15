package ca.pkay.rcloneexplorer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Rclone
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent Remote Telemetry & Storage Quota Repository.
 * Stores remote storage statistics (used, total, free, trashed) persistently
 * so remote cards, properties, and explorer screens load with 0ms delay even across app restarts or offline.
 * Implements LRU cache eviction when total remote entries reach quota limit.
 */
object RemoteTelemetryCacheRepository {
    private const val PREFS_NAME = "ca.pkay.rcloneexplorer.remote_telemetry_cache"
    private const val MAX_REMOTES = 150

    private val memoryCache = ConcurrentHashMap<String, Rclone.AboutResult>()
    private val accessTimestamps = ConcurrentHashMap<String, Long>()
    private var prefs: SharedPreferences? = null

    private fun ensureInitialized(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadAllFromDisk()
        }
    }

    private fun loadAllFromDisk() {
        val sp = prefs ?: return
        for ((key, value) in sp.all) {
            if (value is String) {
                try {
                    val json = JSONObject(value)
                    val result = Rclone.AboutResult(
                        json.optLong("used", -1L),
                        json.optLong("total", -1L),
                        json.optLong("free", -1L),
                        json.optLong("trashed", -1L)
                    )
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    memoryCache[key] = result
                    accessTimestamps[key] = timestamp
                } catch (ignored: Exception) {}
            }
        }
    }

    fun getAll(context: Context): Map<String, Rclone.AboutResult> {
        ensureInitialized(context)
        val isCacheEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("pref_key_enable_telemetry_cache", true)
        if (!isCacheEnabled) return emptyMap()
        return memoryCache.toMap()
    }

    fun get(context: Context, remoteName: String): Rclone.AboutResult? {
        ensureInitialized(context)
        val isCacheEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("pref_key_enable_telemetry_cache", true)
        if (!isCacheEnabled) return null
        val res = memoryCache[remoteName]
        if (res != null) {
            accessTimestamps[remoteName] = System.currentTimeMillis()
        }
        return res
    }

    fun put(context: Context, remoteName: String, result: Rclone.AboutResult) {
        ensureInitialized(context)
        ensureQuota()
        memoryCache[remoteName] = result
        accessTimestamps[remoteName] = System.currentTimeMillis()
        val sp = prefs ?: return
        try {
            val json = JSONObject().apply {
                put("used", result.used)
                put("total", result.total)
                put("free", result.free)
                put("trashed", result.trashed)
                put("timestamp", System.currentTimeMillis())
            }
            sp.edit().putString(remoteName, json.toString()).apply()
        } catch (ignored: Exception) {}
    }

    private fun ensureQuota() {
        if (memoryCache.size >= MAX_REMOTES) {
            val entriesToPrune = accessTimestamps.entries
                .sortedBy { it.value }
                .take(MAX_REMOTES / 4)
            val editor = prefs?.edit()
            for (entry in entriesToPrune) {
                memoryCache.remove(entry.key)
                accessTimestamps.remove(entry.key)
                editor?.remove(entry.key)
            }
            editor?.apply()
        }
    }

    fun remove(context: Context, remoteName: String) {
        ensureInitialized(context)
        memoryCache.remove(remoteName)
        accessTimestamps.remove(remoteName)
        prefs?.edit()?.remove(remoteName)?.apply()
    }

    fun clear(context: Context) {
        ensureInitialized(context)
        memoryCache.clear()
        accessTimestamps.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
