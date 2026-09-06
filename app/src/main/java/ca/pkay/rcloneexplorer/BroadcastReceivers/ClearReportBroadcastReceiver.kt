package ca.pkay.rcloneexplorer.BroadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import ca.pkay.rcloneexplorer.notifications.ReportNotifications
import ca.pkay.rcloneexplorer.notifications.dataStore
import kotlinx.coroutines.runBlocking


class ClearReportBroadcastReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {
        val action = intent.action
        if (action == ReportNotifications.REPORT_SUCCESS_DELETE_INTENT) {
            if(context != null){
                runBlocking {
                    context.dataStore.edit { settings ->
                        settings[ReportNotifications.NOTIFICATION_CACHE_SUCCESS_PREFERENCE] = ""
                    }
                }
            }
        }
        if (action == ReportNotifications.REPORT_FAIL_DELETE_INTENT) {
            if(context != null){
                runBlocking {
                    context.dataStore.edit { settings ->
                        settings[ReportNotifications.NOTIFICATION_CACHE_FAIL_PREFERENCE] = ""
                    }
                }
            }
        }
    }
}