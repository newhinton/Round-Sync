package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.notifications.AppErrorNotificationManager
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Probe Internxt through rclone so automatic recovery runs before we ask the user to sign in. */
class SessionGuardianWorker(
    private val mContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(mContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val rclone = Rclone(mContext)
            val config = JSONObject(rclone.configDump() ?: return@withContext Result.success())
            val notifications = AppErrorNotificationManager(mContext)
            for (name in config.keys()) {
                ensureActive()
                if (config.optJSONObject(name)?.optString("type") != "internxt") {
                    AppErrorNotificationManager.clearSessionExpiredNotification(mContext, name)
                    continue
                }
                val probe = rclone.listDirectories(name, 1)
                if (probe.isSuccess) {
                    AppErrorNotificationManager.clearSessionExpiredNotification(mContext, name)
                } else if (probe.requiresInternxtReauthentication()) {
                    notifications.showSessionExpiredNotification(name)
                } else {
                    FLog.d("SessionGuardian", "Inconclusive Internxt probe (exit %d)", probe.exitCode)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FLog.e("SessionGuardian", "Unable to check Internxt sessions", e)
        }
        Result.success()
    }
}
