package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Session Guardian Worker - Proactively checks session health for OAuth-enabled remotes.
 *
 * This worker runs periodically to detect expired tokens before the user needs them.
 * It uses rclone config dump to identify remotes with token or totp_secret fields,
 * then probes their health using rclone lsd. If the token is expired, the Go backend's
 * reAuthorize logic will automatically attempt to refresh it during the lsd command.
 */
class SessionGuardianWorker(
    private val mContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(mContext, workerParams) {

    companion object {
        private const val TAG = "SessionGuardian"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rclone = Rclone(mContext)

        try {
            Log.d(TAG, "Session Guardian started")
            FLog.d(TAG, "Checking session health for all remotes")

            // Get all remotes
            val remotes = rclone.getRemotes()
            if (remotes.isEmpty()) {
                Log.d(TAG, "No remotes configured, skipping health check")
                return@withContext Result.success()
            }

            var oauthRemotesChecked = 0
            var failedHealthChecks = 0

            // Dump config to find OAuth-enabled remotes
            val configDump = rclone.configDump()
            if (configDump == null || configDump.isEmpty()) {
                Log.e(TAG, "Failed to dump rclone config")
                return@withContext Result.success()
            }

            val configJson = JSONObject(configDump)

            // Iterate through all remotes
            for (remote in remotes) {
                val remoteName = remote.name
                try {
                    val remoteConfig = configJson.optJSONObject(remoteName)
                    if (remoteConfig == null) {
                        continue
                    }

                    // Check if remote has OAuth token or TOTP secret
                    val hasToken = remoteConfig.has("token") ||
                                  remoteConfig.has("access_token") ||
                                  remoteConfig.has("totp_secret")

                    if (!hasToken) {
                        // Not an OAuth/2FA remote, skip health check
                        continue
                    }

                    oauthRemotesChecked++
                    Log.d(TAG, "Checking session health for remote: $remoteName")

                    // Probe health using lsd with max-depth 1
                    // This is a lightweight operation that will trigger reAuthorize in Go backend if needed
                    val exitCode = rclone.listDirectories(remoteName, 1)

                    if (exitCode == 0) {
                        Log.d(TAG, "Session healthy for remote: $remoteName")
                    } else {
                        // rclone returns process exit codes (0/1/...) rather than HTTP status codes.
                        // A non-zero result means the probe failed after backend retry/re-auth attempts.
                        Log.w(TAG, "Health check failed for remote: $remoteName (exit code: $exitCode). Manual reconnect may be required.")
                        failedHealthChecks++
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error checking remote ${remote.name}: ${e.message}", e)
                    FLog.e(TAG, "Error checking remote ${remote.name}", e)
                }
            }

            Log.d(TAG, "Session Guardian completed. Checked $oauthRemotesChecked OAuth remotes, failed checks: $failedHealthChecks")
            FLog.d(TAG, "Session Guardian completed. Checked: $oauthRemotesChecked, Failed: $failedHealthChecks")

        } catch (e: Exception) {
            Log.e(TAG, "Session Guardian failed: ${e.message}", e)
            FLog.e(TAG, "Session Guardian failed", e)
            // Don't return failure - we want the worker to continue scheduling
        }

        return@withContext Result.success()
    }
}
