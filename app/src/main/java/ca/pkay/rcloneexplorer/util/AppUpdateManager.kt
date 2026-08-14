package ca.pkay.rcloneexplorer.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R
import com.azhon.appupdate.manager.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Clean, modern app updater via GitHub Releases matching Neubofy Watch app.
 * Features: Timestamp detection against BuildConfig.BUILD_TIMESTAMP, semantic version comparison,
 * and Azhon DownloadManager with system notification progress and automatic package install prompt.
 */
object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val CHECK_INTERVAL_DAYS = 7L

    private val GITHUB_API_URLS = listOf(
        "https://api.github.com/repos/neubofy/Remote-Manager/releases"
    )

    /**
     * Check for updates.
     * @param silent If true, only checks if 7 days have passed since last check.
     */
    fun checkForUpdates(
        context: Context,
        silent: Boolean = true,
        isBeta: Boolean = false,
        onCheckComplete: (() -> Unit)? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        if (silent && now - lastCheck < TimeUnit.DAYS.toMillis(CHECK_INTERVAL_DAYS)) {
            Log.d(TAG, "Update check skipped (7-day throttle active)")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val releases = fetchGitHubReleases()
                var updateFound = false

                if (releases != null) {
                    var bestRelease: JSONObject? = null
                    var bestApkUrl = ""
                    var maxTimestamp = 0L
                    var bestVersion = ""
                    var bestNotes = ""

                    val regex = Regex(".*?(\\d{13,}).*?\\.apk")

                    for (r in 0 until releases.length()) {
                        val release = releases.getJSONObject(r)
                        val isPrerelease = release.optBoolean("prerelease", false)

                        if (isPrerelease == isBeta) {
                            val assetsArray = release.optJSONArray("assets") ?: JSONArray()
                            val version = release.optString("tag_name", "Unknown").replace("v", "")

                            for (i in 0 until assetsArray.length()) {
                                val asset = assetsArray.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    val match = regex.find(name)
                                    if (match != null) {
                                        val timestamp = match.groupValues[1].toLongOrNull() ?: 0L
                                        if (timestamp > maxTimestamp) {
                                            maxTimestamp = timestamp
                                            bestApkUrl = asset.getString("browser_download_url")
                                            bestRelease = release
                                            bestVersion = version
                                            bestNotes = release.optString("body", "No release notes provided")
                                        }
                                    } else {
                                        if (bestApkUrl.isEmpty() && bestRelease == null) {
                                            bestApkUrl = asset.getString("browser_download_url")
                                            bestRelease = release
                                            bestVersion = version
                                            bestNotes = release.optString("body", "No release notes provided")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (bestRelease != null) {
                        val isEligible = (maxTimestamp > 0 && maxTimestamp > BuildConfig.BUILD_TIMESTAMP) ||
                                (maxTimestamp == 0L && isNewerVersion(bestVersion, BuildConfig.VERSION_NAME))

                        if (isEligible && bestApkUrl.isNotEmpty()) {
                            updateFound = true
                            withContext(Dispatchers.Main) {
                                onCheckComplete?.invoke()
                                showUpdateDialog(context, bestVersion, bestApkUrl, bestNotes)
                            }
                        }
                    }
                }

                if (!updateFound) {
                    withContext(Dispatchers.Main) {
                        onCheckComplete?.invoke()
                        if (!silent) {
                            Toast.makeText(context, "You are using the latest version", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                withContext(Dispatchers.Main) {
                    onCheckComplete?.invoke()
                    if (!silent) {
                        Toast.makeText(context, "Failed to check for updates: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun fetchGitHubReleases(): JSONArray? {
        for (apiUrl in GITHUB_API_URLS) {
            try {
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "RemoteManager-Android")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(content)
                    if (array.length() > 0) {
                        return array
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching from $apiUrl: ${e.message}")
            }
        }
        return null
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val cleanLatest = latest.replace(Regex("[^0-9.]"), "")
            val cleanCurrent = current.replace(Regex("[^0-9.]"), "")
            val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until minOf(latestParts.size, currentParts.size)) {
                if (latestParts[i] > currentParts[i]) return true
                if (latestParts[i] < currentParts[i]) return false
            }
            latestParts.size > currentParts.size
        } catch (e: Exception) {
            latest != current
        }
    }

    private fun showUpdateDialog(context: Context, version: String, url: String, notes: String) {
        val activity = when (context) {
            is Activity -> context
            else -> null
        }

        if (activity == null || activity.isFinishing) {
            Log.w(TAG, "Activity not available, cannot display update dialog")
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage("Version $version is available.\n\nRelease Notes:\n$notes\n\nDo you want to update now?")
            .setPositiveButton("Update") { _, _ ->
                val manager = DownloadManager.Builder(activity).apply {
                    apkUrl(url)
                    apkName("RemoteManager-v$version.apk")
                    smallIcon(R.mipmap.ic_launcher)
                    showNewerToast(true)
                    showNotification(true)
                    apkVersionName(version)
                    apkDescription(notes)
                    jumpInstallPage(true)
                }.build()
                manager.download()
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
