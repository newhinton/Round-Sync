package ca.pkay.rcloneexplorer.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import ca.pkay.rcloneexplorer.BroadcastReceivers.SyncRestartAction
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.FLog
import ca.pkay.rcloneexplorer.util.NotificationUtils
import ca.pkay.rcloneexplorer.workmanager.SyncWorker
import ca.pkay.rcloneexplorer.workmanager.SyncWorker.Companion.EXTRA_TASK_ID
import java.util.UUID

class SyncServiceNotifications(var mContext: Context) {


    companion object {
        const val CHANNEL_ID = "ca.pkay.rcexplorer.sync_service"
        const val CHANNEL_SUCCESS_ID = "ca.pkay.rcexplorer.sync_service_success"
        const val CHANNEL_FAIL_ID = "ca.pkay.rcexplorer.sync_service_fail"

        const val GROUP_ID = "ca.pkay.rcexplorer.sync_service.group"

        const val PERSISTENT_NOTIFICATION_ID_FOR_SYNC = 162
        const val CANCEL_ID_NOTSET = "CANCEL_ID_NOTSET"
        const val TAG = "SyncServiceNotifications"

    }

    private var mReportManager = ReportNotifications(mContext)

    private val OPERATION_FAILED_GROUP = "ca.pkay.rcexplorer.OPERATION_FAILED_GROUP"
    private val OPERATION_SUCCESS_GROUP = "ca.pkay.rcexplorer.OPERATION_SUCCESS_GROUP"


    private var mCancelUnsetId: UUID = UUID.randomUUID()
    private var mCancelId: UUID = mCancelUnsetId

    fun setCancelId(id: UUID) {
        mCancelId = id
    }

    private fun useReports(): Boolean {
        val mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
        return mSharedPreferences.getBoolean(mContext.getString(R.string.pref_key_app_notification_reports), true)
    }

    fun showFailedNotificationOrReport(
        title: String,
        content: String,
        notificationId: Int,
        taskid: Long
    ) {
        if(!useReports()){
            showFailedNotification(content, notificationId, taskid)
            return
        }
        if(mReportManager.getFailures()<=1) {
            showFailedNotification(content, notificationId, taskid)
            mReportManager.lastFailedNotification(notificationId)
            mReportManager.addToFailureReport(title, content)
        } else {
            mReportManager.cancelLastFailedNotification()
            mReportManager.showFailReport(title, content)
        }
    }

    fun showFailedNotification(
        content: String,
        notificationId: Int,
        taskid: Long
    ) {
        val i = Intent(mContext, SyncRestartAction::class.java)
        i.putExtra(EXTRA_TASK_ID, taskid)

        val retryPendingIntent = PendingIntent.getBroadcast(mContext, taskid.toInt(), i, GenericSyncNotification.getFlags())
        val builder = NotificationCompat.Builder(mContext, CHANNEL_FAIL_ID)
            .setSmallIcon(R.drawable.ic_twotone_cloud_error_24)
            .setContentTitle(mContext.getString(R.string.operation_failed))
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(content)
            )
            .setGroup(OPERATION_FAILED_GROUP)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_refresh,
                mContext.getString(R.string.retry_failed_sync),
                retryPendingIntent
            )
        NotificationUtils.createNotification(mContext, notificationId, builder.build())
    }
    fun showCancelledNotificationOrReport(
        content: String,
        notificationId: Int,
        taskid: Long) {

        if(!useReports()){
            showCancelledNotification(content, notificationId, taskid)
            return
        }
        var title = mContext.getString(R.string.operation_failed_cancelled)
        if(mReportManager.getFailures()<=1) {
            showCancelledNotification(content, notificationId, taskid)
            mReportManager.lastFailedNotification(notificationId)
            mReportManager.addToFailureReport(title, content)
        } else {
            mReportManager.cancelLastFailedNotification()
            mReportManager.showFailReport(title, content)
        }
    }

    fun showCancelledNotification(
        content: String,
        notificationId: Int,
        taskid: Long
    ) {
        val i = Intent(mContext, SyncRestartAction::class.java)
        i.putExtra(EXTRA_TASK_ID, taskid)

        val retryPendingIntent = PendingIntent.getService(mContext, taskid.toInt(), i, GenericSyncNotification.getFlags())
        val builder = NotificationCompat.Builder(mContext, CHANNEL_FAIL_ID)
            .setSmallIcon(R.drawable.ic_twotone_cloud_error_24)
            .setContentTitle(mContext.getString(R.string.operation_failed_cancelled))
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(content)
            )
            .setGroup(OPERATION_FAILED_GROUP)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_refresh,
                mContext.getString(R.string.retry_failed_sync),
                retryPendingIntent
            )
        NotificationUtils.createNotification(mContext, notificationId, builder.build())
    }

    fun showSuccessNotificationOrReport(
        title: String,
        content: String,
        notificationId: Int
    ) {

        if(!useReports()){
            showSuccessNotification(title, content, notificationId)
            return
        }

        if(mReportManager.getSucesses()<=1) {
            showSuccessNotification(title, content, notificationId)
            mReportManager.lastSuccededNotification(notificationId)
            mReportManager.addToSuccessReport(title, content)
        } else {
            mReportManager.cancelLastSuccededNotification()
            mReportManager.showSuccessReport(title, content)
        }
    }
    fun showSuccessNotification(title: String, content: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(mContext, CHANNEL_SUCCESS_ID)
            .setSmallIcon(R.drawable.ic_twotone_cloud_done_24)
            .setContentTitle(mContext.getString(R.string.operation_success, title))
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    content
                )
            )
            .setGroup(OPERATION_SUCCESS_GROUP)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationUtils.createNotification(mContext, notificationId, builder.build())
    }

    @Deprecated("Use with specific notification id")
    fun updateSyncNotification(
        title: String,
        content: String,
        bigTextArray: ArrayList<String>,
        percent: Int
    ): Notification? {
        return updateSyncNotification(
            title,
            content,
            bigTextArray,
            percent,
            PERSISTENT_NOTIFICATION_ID_FOR_SYNC
        )
    }

    fun updateSyncNotification(
        title: String,
        content: String,
        bigTextArray: ArrayList<String>,
        percent: Int,
        _notificationId: Int
    ): Notification? {
        if(content.isBlank()){
            FLog.e(TAG, "Missing notification content!")
            return null
        }

        val builder = GenericSyncNotification(mContext).updateGenericNotification(
            mContext.getString(R.string.syncing_service, title),
            content,
            R.drawable.ic_twotone_rounded_cloud_sync_24,
            bigTextArray,
            percent,
            SyncWorker::class.java,
            null,
            CHANNEL_ID
        )

        if(mCancelId != mCancelUnsetId) {

            val intent = WorkManager.getInstance(mContext)
                .createCancelPendingIntent(mCancelId)

            builder.clearActions()
            builder.addAction(
                R.drawable.ic_cancel_download,
                mContext.getString(R.string.cancel),
                intent
            )
        }

        return builder.build()
    }

    fun cancelSyncNotification(notificationId: Int) {
        val notificationManagerCompat = NotificationManagerCompat.from(mContext)
        notificationManagerCompat.cancel(notificationId)
    }
}
