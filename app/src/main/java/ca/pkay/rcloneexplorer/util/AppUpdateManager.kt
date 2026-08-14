package ca.pkay.rcloneexplorer.util

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val CHECK_INTERVAL_DAYS = 7L

    private const val GITHUB_API_URL = "https://api.github.com/repos/neubofy/Remote-Manager/releases"

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
                            val assetsArray = release.optJSONArray("assets") ?: continue
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
                            Toasty.info(context, "You are using the latest version", Toast.LENGTH_SHORT, true).show()
                        }
                    }
                }

                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                withContext(Dispatchers.Main) {
                    onCheckComplete?.invoke()
                    if (!silent) {
                        Toasty.error(context, "Failed to check for updates: ${e.localizedMessage}", Toast.LENGTH_SHORT, true).show()
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
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
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
            val latestClean = latest.split("-")[0].replace("v", "")
            val currentClean = current.split("-")[0].replace("v", "")
            val latestParts = latestClean.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentClean.split(".").map { it.toIntOrNull() ?: 0 }

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
            Log.w(TAG, "Context is not an Activity, using application context for update prompt")
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Update Available (v$version)")
            .setMessage("A new version of Remote Manager is available.\n\nRelease Notes:\n$notes\n\nWould you like to download and install it now?")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallApk(context, url, version)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(context: Context, url: String, version: String) {
        val fileName = "RemoteManager_v$version.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        Toasty.info(context, "Downloading update...", Toast.LENGTH_SHORT, true).show()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Remote Manager v$version")
            .setDescription("Downloading latest update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(browserIntent)
            return
        }

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        ctxt.unregisterReceiver(this)
                    } catch (ignored: Exception) {}

                    val apkUri = FileProvider.getUriForFile(
                        ctxt,
                        "${ctxt.packageName}.fileprovider",
                        destinationFile
                    )

                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctxt.startActivity(installIntent)
                }
            }
        }

        context.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }
}
