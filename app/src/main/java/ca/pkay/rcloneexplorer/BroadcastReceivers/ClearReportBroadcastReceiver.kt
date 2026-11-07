package ca.pkay.rcloneexplorer.BroadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.pkay.rcloneexplorer.notifications.ReportNotifications

class ClearReportBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        if (context == null) return
        val success = when (intent.action) {
            ReportNotifications.REPORT_SUCCESS_DELETE_INTENT -> true
            ReportNotifications.REPORT_FAIL_DELETE_INTENT -> false
            else -> return
        }
        ReportNotifications(context).clearIfCurrent(success, intent.getStringExtra(ReportNotifications.EXTRA_GENERATION))
    }
}
