package ca.pkay.rcloneexplorer.workmanager

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Parcel
import androidx.annotation.StringRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Log2File
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.notifications.prototypes.WorkerNotification
import ca.pkay.rcloneexplorer.notifications.support.StatusObject
import ca.pkay.rcloneexplorer.util.FLog
import ca.pkay.rcloneexplorer.util.NotificationUtils
import ca.pkay.rcloneexplorer.util.SyncLog
import de.felixnuesse.extract.extensions.tag
import de.felixnuesse.extract.notifications.implementations.DownloadWorkerNotification
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import kotlin.random.Random
import android.util.Log
import de.felixnuesse.extract.notifications.implementations.DeleteWorkerNotification
import de.felixnuesse.extract.notifications.implementations.MoveWorkerNotification
import de.felixnuesse.extract.notifications.implementations.UploadWorkerNotification


class EphemeralWorker (private var mContext: Context, workerParams: WorkerParameters): Worker(mContext, workerParams) {


    companion object {
        const val EPHEMERAL_TYPE = "TASK_EPHEMERAL_TYPE"
        const val REMOTE = "REMOTE"

        const val DOWNLOAD_TARGETPATH = "DOWNLOAD_TARGETPATH"
        const val DOWNLOAD_SOURCE = "DOWNLOAD_SOURCE"

        const val UPLOAD_FILE = "UPLOAD_FILE"
        const val UPLOAD_TARGETPATH = "UPLOAD_TARGETPATH"

        const val MOVE_FILE = "MOVE_FILE"
        const val MOVE_TARGETPATH = "MOVE_TARGETPATH"

        const val DELETE_FILE = "DELETE_FILE"
    }

    internal enum class FAILURE_REASON {
        NO_FAILURE, NO_UNMETERED, NO_CONNECTION, RCLONE_ERROR, CONNECTIVITY_CHANGED, CANCELLED, NO_TASK
    }

    // Objects
    private var mNotificationManager: WorkerNotification? = null
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
    private val ongoingNotificationID = Random.nextInt()


    private var mTitle: String = mNotificationManager?.initialTitle ?: ""

    override fun doWork(): Result {
        if (inputData.keyValueMap.containsKey(EPHEMERAL_TYPE)){
            val type = Type.valueOf(inputData.getString(EPHEMERAL_TYPE) ?: "")
            mNotificationManager = prepareNotificationManager(type)
            mTitle = mNotificationManager?.initialTitle ?: ""

            updateForegroundNotification(mNotificationManager?.updateNotification(
                mTitle,
                mTitle,
                ArrayList(),
                0,
                ongoingNotificationID
            ))

            val remoteItem = getRemoteitemFromParcel(REMOTE)
            if(remoteItem == null){
                log("$REMOTE: No valid remote was passed!")
                return Result.failure()
            }

            mNotificationManager?.setCancelId(id)
            // Execute task directly without any connection preconditions
            when(type){
                Type.DOWNLOAD -> {
                    val target = inputData.getString(DOWNLOAD_TARGETPATH)
                    val fileItem = getFileitemFromParcel(DOWNLOAD_SOURCE)

                    if(fileItem == null){
                        log("$DOWNLOAD_SOURCE: No valid target was passed!")
                        return Result.failure()
                    }

                    sRcloneProcess = Rclone(mContext).downloadFile(
                        remoteItem,
                        fileItem,
                        target
                    )
                }
                Type.UPLOAD -> {
                    val target = inputData.getString(UPLOAD_TARGETPATH)
                    val file = inputData.getString(UPLOAD_FILE)

                    sRcloneProcess = Rclone(mContext).uploadFile(
                        remoteItem,
                        target,
                        file
                    )
                }
                Type.MOVE -> {
                    val target = inputData.getString(MOVE_TARGETPATH)
                    val fileItem = getFileitemFromParcel(MOVE_FILE)

                    if(fileItem == null){
                        log("$MOVE_FILE: No valid target was passed!")
                        return Result.failure()
                    }

                    sRcloneProcess = Rclone(mContext).moveTo(
                        remoteItem,
                        fileItem,
                        target
                    )
                }
                Type.DELETE -> {
                    val fileItem = getFileitemFromParcel(DELETE_FILE)

                    if(fileItem == null){
                        log("$DELETE_FILE: No valid target was passed!")
                        return Result.failure()
                    }

                    sRcloneProcess = Rclone(mContext).deleteItems(
                        remoteItem,
                        fileItem
                    )
                }
            }
            handleSync(mTitle)
            postSync()
            return Result.success()
        }
        log("Critical: No valid ephemeral type passed!")
        return Result.failure()
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
        postSync()
    }

    fun prepareNotificationManager(type: Type): WorkerNotification {
        return when(type){
            Type.DOWNLOAD -> DownloadWorkerNotification(mContext)
            Type.UPLOAD -> UploadWorkerNotification(mContext)
            Type.DELETE -> DeleteWorkerNotification(mContext)
            Type.MOVE -> MoveWorkerNotification(mContext)
        }
    }

    private fun handleSync(title: String) {
        if (sRcloneProcess != null) {
            val localProcessReference = sRcloneProcess!!
            try {
                val reader = BufferedReader(InputStreamReader(localProcessReference.errorStream))
                val iterator = reader.lineSequence().iterator()
                while(iterator.hasNext()) {
                    val line = iterator.next()
                    try {
                        val logline = JSONObject(line)
                        if (logline.getString("level") == "error") {
                            if (sIsLoggingEnabled) {
                                log2File?.log(line)
                            }
                            statusObject.parseLoglineToStatusObject(logline)
                        } else if (logline.getString("level") == "warning" || logline.has("stats")) {
                            statusObject.parseLoglineToStatusObject(logline)
                        }

                        val updatedNotification = mNotificationManager?.updateNotification(
                            title,
                            statusObject.notificationContent,
                            statusObject.notificationBigText,
                            statusObject.notificationPercent,
                            ongoingNotificationID
                        )
                        updatedNotification?.let {
                            NotificationUtils.createNotification(mContext, ongoingNotificationID, it)
                        }
                    } catch (e: JSONException) {
                        Log.e(tag(), "Error: the offending line: $line")
                    }
                }
            } catch (e: InterruptedIOException) {
                FLog.e(tag(), "onHandleIntent: I/O interrupted, stream closed", e)
            } catch (e: IOException) {
                FLog.e(tag(), "onHandleIntent: error reading stdout", e)
            }
            try {
                localProcessReference.waitFor()
            } catch (e: InterruptedException) {
                FLog.e(tag(), "onHandleIntent: error waiting for process", e)
            }
        } else {
            log("Sync: No Rclone Process!")
        }
        mNotificationManager?.cancelSyncNotification(ongoingNotificationID)
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
        showFailNotification(notificationId, content)
        endNotificationAlreadyPosted = true
        finishWork()
    }

    private fun showCancelledNotification(notificationId: Int) {
        val content = mContext.getString(R.string.operation_failed_cancelled)
        SyncLog.info(mContext, mTitle, content)
        mNotificationManager?.showCancelledNotification(
            mTitle,
            content,
            notificationId,
            0
        )
    }

    private fun showSuccessNotification(notificationId: Int) {
        //Todo: Show sync-errors in notification. Also see line 169
        //Todo: This should be context dependend on the type. It is currently not!


        var message = mNotificationManager?.generateSuccessMessage(statusObject, getCurrentFile())?: "error"

        mNotificationManager?.showSuccessNotification(
            mTitle,
            message,
            notificationId
        )

        message += """
                        
        Est. Speed: ${statusObject.getEstimatedAverageSpeed()}
        Avg. Speed: ${statusObject.getLastItemAverageSpeed()}
                        """.trimIndent()
        //SyncLog.info(mContext, mContext.getString(R.string.operation_success, mTitle), message)
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
        mNotificationManager?.showFailedNotification(
            mTitle,
            text,
            notificationId,
           0
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            notification?.let {
                setForegroundAsync(ForegroundInfo(ongoingNotificationID, it, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            }
        }
    }

    private fun log(message: String) {
        FLog.e(tag(), "EphemeralWorker: $message")
    }

    private fun getString(@StringRes resId: Int?): String {
        return if (resId == null) {
            "Error"
        } else {
            mContext.getString(resId)
        }
    }

    private fun getFileitemFromParcel(key: String): FileItem? {
        val sourceParcelByteArray = inputData.getByteArray(key) ?: return null
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(sourceParcelByteArray, 0, sourceParcelByteArray.size)
            parcel.setDataPosition(0)
            FileItem.CREATOR.createFromParcel(parcel)
        } catch (e: Exception) {
            log("Error unmarshalling FileItem: ${e.message}")
            null
        } finally {
            parcel.recycle()
        }
    }

    private fun getRemoteitemFromParcel(key: String): RemoteItem? {
        val sourceParcelByteArray = inputData.getByteArray(key)
        if (sourceParcelByteArray == null) {
            log("No valid target was passed!")
            return null
        }

        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(sourceParcelByteArray, 0, sourceParcelByteArray.size)
            parcel.setDataPosition(0)
            RemoteItem.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private fun getCurrentFile(): FileItem {
        val fallback = FileItem(RemoteItem("", ""), "", "", 0L, "modTime", "mimeType", false, false)
        return when (Type.valueOf(inputData.getString(EPHEMERAL_TYPE) ?: Type.DOWNLOAD.name)) {
            Type.DOWNLOAD -> getFileitemFromParcel(DOWNLOAD_SOURCE) ?: fallback
            Type.UPLOAD -> {
                val pathAndName = inputData.getString(UPLOAD_FILE) ?: ""
                val name = pathAndName.substring(pathAndName.lastIndexOf("/") + 1)
                val path = pathAndName.substring(0, pathAndName.lastIndexOf("/") + 1)
                FileItem(RemoteItem("", ""), path, name, 0L, "modTime", "mimeType", false, false)
            }
            Type.MOVE -> getFileitemFromParcel(MOVE_FILE) ?: fallback
            Type.DELETE -> getFileitemFromParcel(DELETE_FILE) ?: fallback
        }
    }
}
