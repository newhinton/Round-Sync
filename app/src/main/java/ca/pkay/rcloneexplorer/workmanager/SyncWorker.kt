package ca.pkay.rcloneexplorer.workmanager

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.wifi.WifiManager
import androidx.annotation.StringRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Log2File
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.notifications.GenericSyncNotification
import ca.pkay.rcloneexplorer.notifications.ReportNotifications
import ca.pkay.rcloneexplorer.notifications.SyncServiceNotifications
import ca.pkay.rcloneexplorer.notifications.SyncServiceNotifications.Companion.GROUP_ID
import ca.pkay.rcloneexplorer.notifications.support.StatusObject
import ca.pkay.rcloneexplorer.util.FLog
import ca.pkay.rcloneexplorer.util.SyncLog
import ca.pkay.rcloneexplorer.util.WifiConnectivitiyUtil
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.Random

class SyncWorker (private var mContext: Context, workerParams: WorkerParameters): Worker(mContext, workerParams) {

    companion object {
        const val TASK_ID = "TASK_ID"
        const val TASK_EPHEMERAL = "TASK_EPHEMERAL"
        private const val TAG = "SyncWorker"

        //those Extras do not follow the above schema, because they are exposed to external applications
        //That means shorter values make it easier to use. There is no other technical reason
        const val TASK_SYNC_ACTION = "START_TASK"
        const val TASK_CANCEL_ACTION = "CANCEL_TASK"
        const val EXTRA_TASK_ID = "task"

        // Todo: Allow SyncWorker to run in silent mode, or remove this!
        const val EXTRA_TASK_SILENT = "notification"
    }



    internal enum class FAILURE_REASON {
        NO_FAILURE, NO_UNMETERED, NO_CONNECTION, RCLONE_ERROR, CONNECTIVITY_CHANGED, CANCELLED, NO_TASK
    }

    // Objects
    private var mRclone = Rclone(mContext)
    private var mDatabase = DatabaseHandler(mContext)
    private var mNotificationManager = SyncServiceNotifications(mContext)
    private val mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)


    private var log2File: Log2File? = null



    // States
    private val sIsLoggingEnabled = mPreferences.getBoolean(getString(R.string.pref_key_logs), false)
    private var sConnectivityChanged = false

    private var sRcloneProcess: Process? = null
    private val statusObject = StatusObject(mContext)
    private var failureReason = FAILURE_REASON.NO_FAILURE
    private var endNotificationAlreadyPosted = false
    private var silentRun = false
    private val ongoingNotificationID = Random().nextInt()


    // Task
    private lateinit var mTask: Task
    private var mTitle: String = mContext.getString(R.string.sync_service_notification_startingsync)



    override fun doWork(): Result {

        prepareNotifications()
        registerBroadcastReceivers()

        updateForegroundNotification(mNotificationManager.updateSyncNotification(
            mTitle,
            mTitle,
            ArrayList(),
            0,
            ongoingNotificationID
        ))


        var ephemeralTask: Task? = null

        if(inputData.keyValueMap.containsKey(TASK_ID)){
            val id = inputData.getLong(TASK_ID, -1)
            ephemeralTask = mDatabase.getTask(id)
        }

        if(inputData.keyValueMap.containsKey(TASK_EPHEMERAL)){
            val taskString = inputData.getString(TASK_EPHEMERAL) ?: ""
            if(taskString.isNotEmpty()) {
                try {
                    ephemeralTask = Json.decodeFromString<Task>(taskString)
                } catch (e: Exception) {
                    log("Could not deserialize")
                }
            }
        }

        if (ephemeralTask != null) {
            mTask = ephemeralTask
            handleTask()
            postSync()
        } else {
            postSync()
            return Result.failure()
        }

        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()
        SyncLog.info(mContext, mTitle, mContext.getString(R.string.operation_sync_cancelled))
        SyncLog.info(mContext, mTitle, statusObject.toString())
        failureReason = FAILURE_REASON.CANCELLED
        finishWork()
    }

    private fun finishWork() {
        sRcloneProcess?.destroy()
        mContext.unregisterReceiver(connectivityChangeBroadcastReceiver)
        postSync()
    }

    private fun handleTask() {
        mTitle = mTask.title
        mNotificationManager.setCancelId(id)
        val remoteItem = RemoteItem(mTask.remoteId, mTask.remoteType, "")

        if (mTask.title == "") {
            mTitle = mTask.remotePath
        }
        statusObject.syncDirection = mTask.direction
        if(arePreconditionsMet()) {
            val taskFilter = if(mTask.filterId != null ) mDatabase.getFilter(mTask.filterId!!) else null;
            val taskFilterList = taskFilter?.getFilters() ?: ArrayList()
            sRcloneProcess = mRclone.sync(
                remoteItem,
                mTask.localPath,
                mTask.remotePath,
                mTask.direction,
                mTask.md5sum,
                taskFilterList,
                mTask.deleteExcluded
            )
            handleSync(mTitle)
            sendUploadFinishedBroadcast(remoteItem.name, mTask.remotePath)
        }
    }

    private fun handleSync(title: String) {
        SyncLog.info(mContext, mTitle, mContext.getString(R.string.operation_start_sync))
        if (sRcloneProcess != null) {
            val localProcessReference = sRcloneProcess!!
            try {
                val reader = BufferedReader(InputStreamReader(localProcessReference.errorStream))
                val iterator = reader.lineSequence().iterator()
                while(iterator.hasNext()) {
                    val line = iterator.next()
                    try {
                        val logline = JSONObject(line)
                        //todo: migrate this to StatusObject, so that we can handle everything properly.
                        if (logline.getString("level") == "error") {
                            if (sIsLoggingEnabled) {
                                log2File?.log(line)
                            }
                        }
                        
                        // Process all log lines for stats/progress updates, not just error/warning
                        // This fixes the notification being stuck on "starting sync"
                        statusObject.parseLoglineToStatusObject(logline)

                        // Only update notification if we have content to show
                        if (statusObject.notificationContent.isNotEmpty()) {
                            updateForegroundNotification(mNotificationManager.updateSyncNotification(
                                title,
                                statusObject.notificationContent,
                                statusObject.notificationBigText,
                                statusObject.notificationPercent,
                                ongoingNotificationID
                            ))
                        }
                    } catch (e: JSONException) {
                        FLog.e(TAG, "SyncService-Error: the offending line: $line")
                        //FLog.e(TAG, "onHandleIntent: error reading json", e)
                    }
                }
            } catch (e: InterruptedIOException) {
                FLog.e(TAG, "onHandleIntent: I/O interrupted, stream closed", e)
            } catch (e: IOException) {
                FLog.e(TAG, "onHandleIntent: error reading stdout", e)
            }
            try {
                localProcessReference.waitFor()
            } catch (e: InterruptedException) {
                FLog.e(TAG, "onHandleIntent: error waiting for process", e)
            }
        } else {
            log("Sync: No Rclone Process!")
        }
        mNotificationManager.cancelSyncNotification(ongoingNotificationID)
    }

    private fun postSync() {
        if (endNotificationAlreadyPosted) {
            return
        }
        if (silentRun) {
            return
        }

        val notificationId = System.currentTimeMillis().toInt()

        var content = mContext.getString(R.string.operation_failed_unknown, mTitle)
        when (failureReason) {
            FAILURE_REASON.NO_FAILURE -> {
                showSuccessNotification(notificationId)
                followupTask(mTask.onSuccessFollowup)
                return
            }
            FAILURE_REASON.CANCELLED -> {
                showCancelledNotification(notificationId)
                endNotificationAlreadyPosted = true
                return
            }
            FAILURE_REASON.NO_TASK -> {
                content = getString(R.string.operation_failed_notask)
            }
            FAILURE_REASON.CONNECTIVITY_CHANGED -> {
                content = mContext.getString(R.string.operation_failed_data_change, mTitle)
            }
            FAILURE_REASON.NO_UNMETERED -> {
                content = mContext.getString(R.string.operation_failed_no_unmetered, mTitle)
            }
            FAILURE_REASON.NO_CONNECTION -> {
                content = mContext.getString(R.string.operation_failed_no_connection, mTitle)
            }
            FAILURE_REASON.RCLONE_ERROR -> {
                content = mContext.getString(R.string.operation_failed_unknown_rclone_error, mTitle)
            }
        }
        followupTask(mTask.onFailFollowup)
        showFailNotification(notificationId, content)
        endNotificationAlreadyPosted = true
        finishWork()
    }

    private fun showCancelledNotification(notificationId: Int) {
        SyncLog.info(mContext, mTitle, mContext.getString(R.string.operation_failed_cancelled))
        mNotificationManager.showCancelledNotificationOrReport(
            mTitle,
            notificationId,
            mTask.id
        )
    }

    private fun showSuccessNotification(notificationId: Int) {
        //Todo: Show sync-errors in notification. Also see line 169

        var message = generateSuccessMessage(statusObject)
        mNotificationManager.showSuccessNotificationOrReport(
            mTitle,
            message,
            notificationId
        )

        message += """
                        
        Est. Speed: ${statusObject.getEstimatedAverageSpeed()}
        Avg. Speed: ${statusObject.getLastItemAverageSpeed()}
                        """.trimIndent()
        SyncLog.info(mContext, mContext.getString(R.string.operation_success, mTitle), message)
    }

    // this is currently only a useless mapper. It is supposed to keep this worker in sync with the ephemeral one.
    // when they are merged eventually, this can be easily extracted.
    private fun generateSuccessMessage(statusObject: StatusObject): String {
        var message = mContext.resources.getQuantityString(
                R.plurals.operation_success_description,
                statusObject.getTotalTransfers(),
                mTitle,
                statusObject.getTotalSize(),
                statusObject.getTotalTransfers()
        )
        if (statusObject.getTotalTransfers() == 0) {
            message = mContext.resources.getString(R.string.operation_success_description_zero)
        }
        if (statusObject.getDeletions() > 0) {
            message += """
                        
                        ${
                mContext.getString(
                        R.string.operation_success_description_deletions_prefix,
                        statusObject.getDeletions()
                )
            }
                        """.trimIndent()
        }
        return message
    }

    private fun showFailNotification(notificationId: Int, content: String, wasCancelled: Boolean = false) {
        var text = content
        //Todo: check if we should also add errors on success
        statusObject.printErrors()
        val errors = statusObject.getAllErrorMessages()
        if (errors.isNotEmpty()) {
            text += """
                        
                        
                        
                        ${statusObject.getAllErrorMessages()}
                        """.trimIndent()
        }

        var notifyTitle = mContext.getString(R.string.operation_failed)
        if (wasCancelled) {
            notifyTitle = mContext.getString(R.string.operation_failed_cancelled)
        }
        SyncLog.error(mContext, notifyTitle, "$mTitle: $text")
        mNotificationManager.showFailedNotificationOrReport(
            mTitle,
            text,
            notificationId,
            mTask.id
        )
    }

    private fun arePreconditionsMet(): Boolean {
        val connection = WifiConnectivitiyUtil.dataConnection(this.applicationContext)
        if (mTask.wifionly && connection === WifiConnectivitiyUtil.Connection.METERED) {
            failureReason = FAILURE_REASON.NO_UNMETERED
            return false
        } else if (connection === WifiConnectivitiyUtil.Connection.DISCONNECTED || connection === WifiConnectivitiyUtil.Connection.NOT_AVAILABLE) {
            failureReason = FAILURE_REASON.NO_CONNECTION
            return false
        }

        return true
    }

    private fun prepareNotifications() {

        GenericSyncNotification(mContext).setNotificationChannel(
                SyncServiceNotifications.CHANNEL_ID,
                getString(R.string.sync_service_notification_channel_title),
                getString(R.string.sync_service_notification_channel_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            SyncServiceNotifications.CHANNEL_SUCCESS_ID,
            getString(R.string.sync_service_notification_channel_success_title),
            getString(R.string.sync_service_notification_channel_success_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            SyncServiceNotifications.CHANNEL_FAIL_ID,
            getString(R.string.sync_service_notification_channel_fail_title),
            getString(R.string.sync_service_notification_channel_fail_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            ReportNotifications.CHANNEL_REPORT_ID,
            getString(R.string.sync_service_notification_channel_report_title),
            getString(R.string.sync_service_notification_channel_report_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )

    }

    private fun sendUploadFinishedBroadcast(remote: String, path: String?) {
        val intent = Intent()
        intent.action = getString(R.string.background_service_broadcast)
        intent.putExtra(getString(R.string.background_service_broadcast_data_remote), remote)
        intent.putExtra(getString(R.string.background_service_broadcast_data_path), path)
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun updateForegroundNotification(notification: Notification?) {
        notification?.let {
            setForegroundAsync(ForegroundInfo(ongoingNotificationID, it, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        }
    }


    private fun log(message: String) {
        FLog.e(TAG, "SyncWorker: $message")
    }

    private fun getString(@StringRes resId: Int): String {
        return mContext.getString(resId)
    }

    private fun registerBroadcastReceivers() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        mContext.registerReceiver(connectivityChangeBroadcastReceiver, intentFilter)
    }

    private val connectivityChangeBroadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if(endNotificationAlreadyPosted){
                    return
                }
                sConnectivityChanged = true
                failureReason = FAILURE_REASON.CONNECTIVITY_CHANGED
            }
        }

    private fun followupTask(followUpTaskID: Long?) {
        if (followUpTaskID == null || followUpTaskID == -1L) {
            return
        }
        Thread.sleep(1000)
        SyncManager(mContext).queue(followUpTaskID)
    }
}
