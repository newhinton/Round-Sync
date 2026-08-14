package ca.pkay.rcloneexplorer.notifications.support

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.notifications.GenericSyncNotification
import ca.pkay.rcloneexplorer.util.PermissionManager

@Deprecated("Use WorkerNotification in combination with workers.")
abstract class GenericNotification(var mContext: Context) {

    abstract val sChannelId: String
    abstract val sChannelName: String
    abstract val finishedGroup: String
    abstract val failedGroup: String
    abstract val persistentId: Int
    abstract val finishedNotificationId: Int
    abstract val failedNotificationId: Int
    abstract val connectivityChangeId: Int

    abstract val actionIcon: Int
    abstract val serviceClass: Class<*>
    abstract val serviceCancelClass: Class<*>

    abstract val completeString: Int
    abstract val failedString: Int
    abstract val canceledString: Int

    /**
     * Create initial Notification to be build for the service
     */
    fun createNotification(
        title: String,
        bigTextArray: java.util.ArrayList<String>
    ): NotificationCompat.Builder {

        val cancelIntent = Intent(mContext, serviceCancelClass)
        val cancelPendingIntent = PendingIntent.getBroadcast(mContext, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)

        return GenericSyncNotification(mContext).updateGenericNotification(
            title,
            title,
            actionIcon,
            bigTextArray,
            0,
            serviceClass,
            cancelPendingIntent,
            sChannelId
        )
    }

    /**
     * Update Service notification
     */
    fun updateNotification(
        title: String,
        content: String,
        bigTextArray: java.util.ArrayList<String>,
        percent: Int
    ): Notification? {
        if(content.isBlank()){
            return null
        }

        val cancelIntent = Intent(mContext, serviceCancelClass)
        val cancelPendingIntent = PendingIntent.getBroadcast(mContext, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)

        var builder = GenericSyncNotification(mContext).updateGenericNotification(
            title,
            content,
            actionIcon,
            bigTextArray,
            percent,
            serviceClass,
            cancelPendingIntent,
            sChannelId
        )
        return builder.build()
    }

    fun showFinishedNotification(notificationID: Int, contentText: String) {
        createSummaryNotificationForFinished()
        val builder = NotificationCompat.Builder(mContext, sChannelId)
            .setSmallIcon(R.drawable.ic_twotone_cloud_done_24)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(mContext.resources, R.drawable.app_logo))
            .setContentTitle(mContext.getString(completeString))
            .setContentText(contentText)
            .setGroup(finishedGroup)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        notify(builder, notificationID)
    }

    private fun createSummaryNotificationForFinished() {
        val summaryNotification = NotificationCompat.Builder(mContext,
            sChannelId
        )
            .setContentTitle(mContext.getString(completeString)) //set content text to support devices running API level < 24
            .setContentText(mContext.getString(completeString))
            .setSmallIcon(R.drawable.ic_twotone_cloud_done_24)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(mContext.resources, R.drawable.app_logo))
            .setGroup(finishedGroup)
            .setGroupSummary(true)
            .setAutoCancel(true)
        notify(summaryNotification, finishedNotificationId)
    }

    fun showFailedNotification(notificationID: Int, contentText: String) {
        createSummaryNotificationForFailed()
        val builder = NotificationCompat.Builder(mContext, sChannelId)
            .setSmallIcon(R.drawable.ic_twotone_cloud_error_24)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(mContext.resources, R.drawable.app_logo))
            .setContentTitle(mContext.getString(failedString))
            .setContentText(contentText)
            .setGroup(failedGroup)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        notify(builder, notificationID)
    }

    fun createSummaryNotificationForFailed() {
        val summaryNotification = NotificationCompat.Builder(mContext, sChannelId)
            .setContentTitle(mContext.getString(failedString)) //set content text to support devices running API level < 24
            .setContentText(mContext.getString(failedString))
            .setSmallIcon(R.drawable.ic_twotone_cloud_error_24)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(mContext.resources, R.drawable.app_logo))
            .setGroup(failedGroup)
            .setGroupSummary(true)
            .setAutoCancel(true)
        notify(summaryNotification, failedNotificationId)
    }

    fun showConnectivityChangedNotification() {
        val builder = NotificationCompat.Builder(mContext, sChannelId)
            .setSmallIcon(R.drawable.ic_twotone_cloud_error_24)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(mContext.resources, R.drawable.app_logo))
            .setContentTitle(mContext.getString(canceledString))
            .setContentText(mContext.getString(R.string.wifi_connections_isnt_available))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notify(builder, connectivityChangeId)
    }

    fun cancelPersistent() {
        val notificationManagerCompat = NotificationManagerCompat.from(mContext)
        notificationManagerCompat.cancel(this.persistentId)
    }

    @SuppressLint("MissingPermission")
    private fun notify(builder: NotificationCompat.Builder, id: Int) {
        val notificationManager = NotificationManagerCompat.from(mContext)

        if(PermissionManager(mContext).grantedNotifications()) {
            notificationManager.notify(id, builder.build())
        } else {
            Log.e("GenericNotification", "We dont have Notification Permission!")
        }
    }
}