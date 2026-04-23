package de.schuelken.cloudbridge.updates.workmanager

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R
import com.sharkaboi.appupdatechecker.AppUpdateChecker
import com.sharkaboi.appupdatechecker.models.AppUpdateCheckerException
import com.sharkaboi.appupdatechecker.models.UpdateResult
import com.sharkaboi.appupdatechecker.sources.github.GithubTagSource
import com.sharkaboi.appupdatechecker.versions.DefaultStringVersionComparator
import com.sharkaboi.appupdatechecker.versions.VersionComparator
import de.schuelken.cloudbridge.extensions.tag
import de.schuelken.cloudbridge.notifications.AppUpdateNotification
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateWorker (private var mContext: Context, workerParams: WorkerParameters): Worker(mContext, workerParams) {

    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(mContext)

    private var checkForUpdates = preferenceManager.getBoolean(mContext.getString(R.string.pref_key_app_updates), false)
    private val ignoredVersion = preferenceManager.getString(mContext.getString(R.string.pref_key_app_update_dismiss_current_update), "")
    private val lastFoundVersion = preferenceManager.getString(mContext.getString(R.string.pref_key_app_updates_found_update_for_version), BuildConfig.VERSION_NAME)?:BuildConfig.VERSION_NAME


    override fun doWork(): Result {

        Log.e(tag(), "Try to check updates...")

        // this is supposed to only run on startup and once a week.
        if(!checkForUpdates) {
            return Result.success()
        }

        // if we have a new version stored in the preference, only show a notification
        if(BuildConfig.VERSION_NAME != lastFoundVersion) {
            notifyIfRequired()
            // If the last found version is ignored, still do the check
            if (ignoredVersion != lastFoundVersion) {
                return Result.success()
            }
        }

        val source =  GithubTagSource(
            ownerUsername = "newhinton",
            repoName = "CloudBridge",
            currentVersion = BuildConfig.VERSION_NAME
        )

        val customVersionComparator = object : VersionComparator<String> {
            override fun isNewerVersion(currentVersion: String, newVersion: String): Boolean {
                return DefaultStringVersionComparator.isNewerVersion(
                    currentVersion.substringBefore('-'),
                    newVersion.substringBefore('-')
                )
            }
        }
        source.setCustomVersionComparator(customVersionComparator)


        CoroutineScope(Dispatchers.IO).launch( CoroutineExceptionHandler { _, throwable ->
            if (throwable is AppUpdateCheckerException) {
                Log.e(tag(), "Error: ${throwable.message}")
            }
        }) {
            when (val result = AppUpdateChecker(source).checkUpdate()) {
                UpdateResult.NoUpdate -> setFoundVersion(BuildConfig.VERSION_NAME)
                is UpdateResult.UpdateAvailable<*> -> {
                    Log.e(tag(), "Update found : " + result.versionDetails.latestVersion.toString())
                    setFoundVersion(result.versionDetails.latestVersion.toString())
                    setChangelog(result.versionDetails.releaseNotes.toString())
                    notifyIfRequired()
                }
            }
        }

        // Indicate whether the work finished successfully with the Result
        return Result.success()
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
        val key = mContext.getString(R.string.pref_key_app_updates_found_update_for_version)
        preferenceManager.edit().putString(key, version).apply()
    }
}
