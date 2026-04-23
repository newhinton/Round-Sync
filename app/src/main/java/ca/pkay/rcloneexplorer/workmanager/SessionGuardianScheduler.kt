package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Scheduler for Session Guardian Worker.
 * Schedules periodic health checks for OAuth-enabled remotes.
 */
object SessionGuardianScheduler {

    private const val WORK_NAME = "session_guardian_worker"
    private const val TAG = "SessionGuardianScheduler"

    /**
     * Schedule the Session Guardian Worker to run every 8 hours.
     */
    @JvmStatic
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SessionGuardianWorker>(
            8, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15,
                TimeUnit.MINUTES
            )
            .setInitialDelay(1, TimeUnit.HOURS) // Start after 1 hour on first install
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    /**
     * Cancel the Session Guardian Worker.
     */
    @JvmStatic
    fun cancel(context: Context) {
        WorkManager.getInstance(context)
            .cancelAllWorkByTag(WORK_NAME)
    }

    /**
     * Check if the worker is scheduled.
     */
    @JvmStatic
    fun isScheduled(context: Context): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(WORK_NAME)
            .getOrAwait()
        return workInfos.isNotEmpty()
    }
}
