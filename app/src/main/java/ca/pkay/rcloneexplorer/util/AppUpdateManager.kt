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
 * Professional app updater via GitHub Releases matching Neubofy Watch app.
 * Features: Timestamp detection against BuildConfig.BUILD_TIMESTAMP, semantic version fallback,
 * and Azhon DownloadManager with notification progress & package installer integration.
 */
object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val CHECK_INTERVAL_DAYS = 7L

    private const val GITHUB_API_URL = "https://api.github.com/repos/neubofy/Remote-Manager/releases"

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
                        val isPrerelease = release.getBoolean("prerelease")

                        if (isPrerelease == isBeta) {
                            val assetsArray = release.getJSONArray("assets")
                            val version = release.optString("tag_name", "Unknown").replace("v", "")

                            for (i in 0 until assetsArray.length()) {
                                val asset = assetsArray.getJSONObject(i)
                                val name = asset.getString("name")
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
        return try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val content = connection.inputStream.bufferedReader().use { it.readText() }
            JSONArray(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch GitHub releases", e)
            null
        }
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
        if (context !is Activity) {
            Log.e(TAG, "Context must be an Activity to show update dialog")
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("Version $version is available.\n\nRelease Notes:\n$notes\n\nDo you want to update now?")
            .setPositiveButton("Update") { _, _ ->
                val manager = DownloadManager.Builder(context).apply {
                    apkUrl(url)
                    apkName("RemoteManager-v$version.apk")
                    smallIcon(R.mipmap.ic_launcher)
                    apkVersionName(version)
                    apkDescription(notes)
                }.build()
                manager.download()
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
