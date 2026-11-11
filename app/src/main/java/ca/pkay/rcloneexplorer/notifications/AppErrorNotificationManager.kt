package ca.pkay.rcloneexplorer.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ca.pkay.rcloneexplorer.Activities.InternxtReauthEntryActivity
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.PermissionManager
import ca.pkay.rcloneexplorer.util.SyncLog

class AppErrorNotificationManager(var mContext: Context) {

    companion object {
        private const val APP_ERROR_CHANNEL_ID =
            "ca.pkay.rcloneexplorer.notifications.AppErrorNotificationManager"
        private const val APP_ERROR_ID = 51913
        private const val SESSION_EXPIRED_ID = 51914

        @JvmStatic
        fun clearSessionExpiredNotification(context: Context, remoteName: String) {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(remoteName, SESSION_EXPIRED_ID)
            manager.cancel(SESSION_EXPIRED_ID + remoteName.hashCode())
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            val channel = NotificationChannel(
                APP_ERROR_CHANNEL_ID,
                mContext.getString(R.string.app_error_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description =
                mContext.getString(R.string.app_error_notification_channel_description)
            // Register the channel with the system
            val notificationManager =
                mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun showNotification() {


        /*val contentIntent = PendingIntent.getActivity(
            mContext,
            APP_ERROR_ID,
            PermissionManager.getNotificationSettingsIntent(mContext), FLAG_IMMUTABLE
        )*/

        val b = NotificationCompat.Builder(mContext, APP_ERROR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twotone_error_24)
            .setContentTitle(mContext.getString(R.string.app_error_notification_alarmpermission_missing))
            .setContentText(mContext.getString(R.string.app_error_notification_alarmpermission_missing_description))
            /*.addAction(
                R.drawable.ic_cancel_download,
                mContext.getString(R.string.cancel),
                contentIntent
            )*/
            .setOnlyAlertOnce(true)

        val notificationManager = NotificationManagerCompat.from(mContext)

        if(PermissionManager(mContext).grantedNotifications()) {
            notificationManager.notify(APP_ERROR_ID, b.build())
        } else {
            Log.e("AppErrorNotificationManager", "We dont have Notification Permission!")
        }
    }

    @SuppressLint("MissingPermission")
    fun showSessionExpiredNotification(remoteName: String) {
        val contentIntent = PendingIntent.getActivity(
            mContext,
            0,
            Intent(mContext, InternxtReauthEntryActivity::class.java).apply {
                // PendingIntent identity includes data, so each remote gets its own action.
                data = Uri.Builder().scheme("cloudbridge-internxt").authority("reauth")
                    .appendPath(remoteName).build()
            },
            FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
        )

        val notificationText = mContext.getString(
            R.string.session_expired_notification_text,
            remoteName
        )

        val b = NotificationCompat.Builder(mContext, APP_ERROR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twotone_error_24)
            .setContentTitle(mContext.getString(R.string.session_expired_notification_title))
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_twotone_error_24, mContext.getString(R.string.reauthenticate), contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        val notificationManager = NotificationManagerCompat.from(mContext)

        if(PermissionManager(mContext).grantedNotifications()) {
            notificationManager.cancel(SESSION_EXPIRED_ID + remoteName.hashCode())
            notificationManager.notify(remoteName, SESSION_EXPIRED_ID, b.build())
        } else {
            Log.e("AppErrorNotificationManager", "We dont have Notification Permission!")
        }
    }
}
