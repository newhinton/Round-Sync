package de.felixnuesse.extract.updates.workmanager

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.AppUpdateManager
import de.felixnuesse.extract.extensions.tag

class UpdateWorker(private var mContext: Context, workerParams: WorkerParameters) : Worker(mContext, workerParams) {

    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(mContext)
    private var checkForUpdates = preferenceManager.getBoolean(mContext.getString(R.string.pref_key_app_updates), true)

    override fun doWork(): Result {
        Log.d(tag(), "UpdateWorker checking for background updates...")

        if (!checkForUpdates) {
            return Result.success()
        }

        try {
            AppUpdateManager.checkForUpdates(
                context = mContext,
                silent = true,
                isBeta = false
            )
        } catch (e: Exception) {
            Log.e(tag(), "Error checking for updates in background", e)
        }

        return Result.success()
    }
}
