package de.schuelken.cloudbridge.updates.workmanager

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R
import de.schuelken.cloudbridge.extensions.tag
import de.schuelken.cloudbridge.notifications.AppUpdateNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

class UpdateWorker (private var mContext: Context, workerParams: WorkerParameters): CoroutineWorker(mContext, workerParams) {

    data class LatestRelease(val tagName: String, val changelog: String)

    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(mContext)

    private var checkForUpdates = preferenceManager.getBoolean(mContext.getString(R.string.pref_key_app_updates), false)
    private val ignoredVersion = preferenceManager.getString(mContext.getString(R.string.pref_key_app_update_dismiss_current_update), "")
    private var lastFoundVersion = preferenceManager.getString(mContext.getString(R.string.pref_key_app_updates_found_update_for_version), BuildConfig.VERSION_NAME)?:BuildConfig.VERSION_NAME


    override suspend fun doWork(): Result {

        Log.e(tag(), "Try to check updates...")

        // this is supposed to only run on startup and once a week.
        if(!checkForUpdates) {
            return Result.success()
        }

        // if we have a new version stored in the preference, only show a notification
        if(isNewerVersion(BuildConfig.VERSION_NAME, lastFoundVersion)) {
            notifyIfRequired()
            // If the last found version is ignored, still do the check
            if (ignoredVersion != lastFoundVersion) {
                return Result.success()
            }
        } else {
            // Clear cached tags for an installed/older release, including v-prefixed
            // tags and legacy beta names, before checking GitHub again.
            setFoundVersion(BuildConfig.VERSION_NAME)
        }

        try {
            checkGithubReleases()
        } catch (e: Exception) {
            Log.e(tag(), "Error: ${e.message}")
        }

        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }

    companion object {
        private const val REPO_OWNER = "thies2005"
        private const val REPO_NAME = "CloudBridge"

        @JvmStatic
        fun latestReleaseRequest(): Request = Request.Builder()
            .url("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CloudBridge-Updater")
            .build()

        /**
         * Parses the object returned by /releases/latest. The endpoint is
         * stable-only; draft and prerelease values are checked defensively so
         * an unexpected API response cannot notify users of a development build.
         */
        @JvmStatic
        fun parseLatestRelease(responseBody: String?): LatestRelease? {
            if (responseBody.isNullOrBlank()) {
                return null
            }
            return try {
                val release = JSONObject(responseBody)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    null
                } else {
                    val tagName = release.optString("tag_name").trim()
                    if (tagName.isEmpty()) null else LatestRelease(tagName, release.optString("body"))
                }
            } catch (_: JSONException) {
                null
            }
        }

        @JvmStatic
        fun isNewerVersion(currentVersion: String, tagName: String): Boolean {
            val currentParts = numericVersionParts(currentVersion) ?: return false
            val tagParts = numericVersionParts(tagName) ?: return false
            for (i in 0 until maxOf(currentParts.size, tagParts.size)) {
                val current = currentParts.getOrElse(i) { 0 }
                val tag = tagParts.getOrElse(i) { 0 }
                if (tag != current) {
                    return tag > current
                }
            }
            // A stable release supersedes a prerelease at the same numeric version.
            return currentVersion.contains('-') && !tagName.contains('-')
        }

        private fun numericVersionParts(version: String): List<Int>? {
            val cleaned = version.substringBefore('-').removePrefix("v").removePrefix("V")
            if (cleaned.isEmpty() || !cleaned[0].isDigit()) {
                return null
            }
            return cleaned.split('.').map { segment -> segment.toIntOrNull() ?: return null }
        }
    }

    /**
     * Notification-only update check: fetches the latest stable GitHub release and
     * compares numeric version prefixes. Inlined to replace the AppUpdateChecker
     * library (flagged NonFreeNet by the FOSS scan).
     */
    private suspend fun checkGithubReleases() = withContext(Dispatchers.IO) {
        val request = latestReleaseRequest()

        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(tag(), "Release API returned HTTP ${response.code}")
                return@withContext
            }
            val latestRelease = parseLatestRelease(response.body?.string())
            if (latestRelease == null) {
                Log.e(tag(), "Latest release API returned an empty or malformed response")
                return@withContext
            }
            val tagName = latestRelease.tagName
            if (isNewerVersion(BuildConfig.VERSION_NAME, tagName)) {
                Log.e(tag(), "Update found: $tagName")
                setFoundVersion(tagName)
                setChangelog(latestRelease.changelog)
                notifyIfRequired()
            } else {
                setFoundVersion(BuildConfig.VERSION_NAME)
            }
        }
    }

    /**
     * Does not notify the user when the user skipped this update.
     */
    private fun notifyIfRequired(){
        if (ignoredVersion != lastFoundVersion){
            AppUpdateNotification(mContext).showNotification(lastFoundVersion)
        } else {
            Log.e(tag(), "Hide this version, because it is ignored.")
        }
    }

    private fun setChangelog(changelog: String){
        val key = mContext.getString(R.string.pref_key_app_updates_changelog)
        preferenceManager.edit().putString(key, changelog).apply()
    }

    fun getChangelog(): String{
        return preferenceManager.getString(mContext.getString(R.string.pref_key_app_updates_changelog), "") ?: ""
    }

    private fun setFoundVersion(version: String){
        lastFoundVersion = version
        val key = mContext.getString(R.string.pref_key_app_updates_found_update_for_version)
        preferenceManager.edit().putString(key, version).apply()
    }
}
