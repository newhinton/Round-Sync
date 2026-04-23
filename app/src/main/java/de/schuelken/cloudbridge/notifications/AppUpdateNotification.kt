package de.schuelken.cloudbridge.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.PermissionManager
import de.schuelken.cloudbridge.extensions.tag
import de.schuelken.cloudbridge.updates.UpdateUserchoiceReceiver
import de.schuelken.cloudbridge.updates.UpdateUserchoiceReceiver.Companion.ACTION_DOWNLOAD
import de.schuelken.cloudbridge.updates.UpdateUserchoiceReceiver.Companion.ACTION_IGNORE
import de.schuelken.cloudbridge.updates.UpdateUserchoiceReceiver.Companion.IGNORE_VERSION_EXTRA


class AppUpdateNotification(private var mContext: Context) {

    companion object {
        var NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL_ID"
        var NOTIFICATION_ID = 63598
    }

    @SuppressLint("MissingPermission") // Handled by PermissionManager
    fun showNotification(version: String) {
        createNotificationChannel()
        val builder = NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.appicon)
            .setContentTitle(mContext.getString(R.string.app_update_notification_title))
            .setContentText(mContext.getString(R.string.app_update_notification_description, version))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_twotone_cancel_24, mContext.getString(R.string.app_updates_notification_action_ignore), getIgnoreVersionIntent(version))
            .addAction(R.drawable.ic_check, mContext.getString(R.string.app_updates_notification_action_apply), getApplyIntent())
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(mContext)

        if(PermissionManager(mContext).grantedNotifications()) {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } else {
            Log.e(tag(), "We don't have notification permission!")
        }
    }

    private fun getIgnoreVersionIntent(version: String): PendingIntent {
        val ignoreIntent = Intent(mContext, UpdateUserchoiceReceiver::class.java)
        ignoreIntent.setAction(ACTION_IGNORE)
        ignoreIntent.putExtra(IGNORE_VERSION_EXTRA, version)
        return PendingIntent.getBroadcast(mContext, 0, ignoreIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getApplyIntent(): PendingIntent {
        val downloadIntent = Intent(mContext, UpdateUserchoiceReceiver::class.java)
        downloadIntent.setAction(ACTION_DOWNLOAD)
        return PendingIntent.getBroadcast(mContext, 0, downloadIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = mContext.getString(R.string.app_update_notification_channel_name)
            val descriptionText = mContext.getString(R.string.app_update_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun cancelNotification(){
        val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

}