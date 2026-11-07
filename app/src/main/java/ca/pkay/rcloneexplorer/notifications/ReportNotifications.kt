package ca.pkay.rcloneexplorer.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.pkay.rcloneexplorer.BroadcastReceivers.ClearReportBroadcastReceiver
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.NotificationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notifications")

class ReportNotifications(private val context: Context) {
    companion object {
        const val CHANNEL_REPORT_ID = "ca.pkay.rcexplorer.sync_report"
        const val REPORT_SUCCESS_DELETE_INTENT = "REPORT_SUCCESS_DELETE_INTENT"
        const val REPORT_FAIL_DELETE_INTENT = "REPORT_FAIL_DELETE_INTENT"
        const val EXTRA_GENERATION = "report_generation"
        val lock = Any()
        // Old unstructured caches cannot be counted reliably when descriptions have newlines.
        val SUCCESS_ENTRIES = stringPreferencesKey("report_success_entries_v2")
        val FAILURE_ENTRIES = stringPreferencesKey("report_failure_entries_v2")
        val SUCCESS_GENERATION = stringPreferencesKey("report_success_generation")
        val FAILURE_GENERATION = stringPreferencesKey("report_failure_generation")
        private val LAST_SUCCESS = intPreferencesKey("NOTIFICATION_LAST_SUCCESS_ID")
        private val LAST_FAILURE = intPreferencesKey("NOTIFICATION_LAST_FAIL_ID")
        private val LEGACY_SUCCESS = stringPreferencesKey("NOTIFICATION_CACHE_SUCCESS")
        private val LEGACY_FAILURE = stringPreferencesKey("NOTIFICATION_CACHE_FAIL")
        private val MIGRATED = booleanPreferencesKey("report_entries_v2_migrated")
    }

    private fun entries(success: Boolean): Preferences.Key<String> = if (success) SUCCESS_ENTRIES else FAILURE_ENTRIES
    private fun generation(success: Boolean): Preferences.Key<String> = if (success) SUCCESS_GENERATION else FAILURE_GENERATION
    private fun migrateLegacy() {
        var oldSuccess: Int? = null
        var oldFailure: Int? = null
        var migrated = false
        runBlocking {
            context.dataStore.edit { settings ->
                if (settings[MIGRATED] != true) {
                    migrated = settings.contains(LEGACY_SUCCESS) || settings.contains(LEGACY_FAILURE) ||
                        settings.contains(LAST_SUCCESS) || settings.contains(LAST_FAILURE)
                    oldSuccess = settings[LAST_SUCCESS]
                    oldFailure = settings[LAST_FAILURE]
                    settings.remove(LEGACY_SUCCESS)
                    settings.remove(LEGACY_FAILURE)
                    settings.remove(LAST_SUCCESS)
                    settings.remove(LAST_FAILURE)
                    settings[MIGRATED] = true
                }
            }
        }
        if (migrated) {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(90)
            manager.cancel(91)
            oldSuccess?.let { manager.cancel(it) }
            oldFailure?.let { manager.cancel(it) }
        }
    }
    private fun read(success: Boolean): JSONArray {
        migrateLegacy()
        return runBlocking { JSONArray(context.dataStore.data.first()[entries(success)] ?: "[]") }
    }

    private fun append(success: Boolean, title: String, line: String): JSONArray = synchronized(lock) {
        migrateLegacy()
        var result = JSONArray()
        runBlocking {
            context.dataStore.edit { settings ->
                val old = JSONArray(settings[entries(success)] ?: "[]")
                result = JSONArray().put("$title: $line")
                for (i in 0 until old.length()) result.put(old.getString(i))
                settings[entries(success)] = result.toString()
                settings[generation(success)] = UUID.randomUUID().toString()
            }
        }
        result
    }

    fun addToSuccessReport(title: String, line: String) { append(true, title, line) }
    fun addToFailureReport(title: String, line: String) { append(false, title, line) }
    fun getSucesses(): Int = synchronized(lock) { read(true).length() }
    fun getFailures(): Int = synchronized(lock) { read(false).length() }
    fun lastSuccededNotification(id: Int) = setLast(true, id)
    fun lastFailedNotification(id: Int) = setLast(false, id)
    private fun setLast(success: Boolean, id: Int) = runBlocking {
        context.dataStore.edit { it[if (success) LAST_SUCCESS else LAST_FAILURE] = id }
        Unit
    }
    fun cancelLastSuccededNotification() = cancelLast(true)
    fun cancelLastFailedNotification() = cancelLast(false)
    private fun cancelLast(success: Boolean) {
        val id = runBlocking { context.dataStore.data.first()[if (success) LAST_SUCCESS else LAST_FAILURE] }
        if (id != null) NotificationManagerCompat.from(context).cancel(id)
    }

    fun deleteIntent(success: Boolean): PendingIntent {
        val token = runBlocking { context.dataStore.data.first()[generation(success)] } ?: ""
        val action = if (success) REPORT_SUCCESS_DELETE_INTENT else REPORT_FAIL_DELETE_INTENT
        val intent = Intent(context, ClearReportBroadcastReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("cloudbridge-report://dismiss/$token")
            putExtra(EXTRA_GENERATION, token)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
    }

    fun clearIfCurrent(success: Boolean, token: String?) = synchronized(lock) {
        runBlocking {
            context.dataStore.edit { settings ->
                if (token != null && token == settings[generation(success)]) {
                    settings.remove(entries(success))
                    settings.remove(generation(success))
                }
            }
        }
    }

    fun showSuccessReport(title: String, line: String) = showReport(true, title, line)
    fun showFailReport(title: String, line: String) = showReport(false, title, line)
    private fun showReport(success: Boolean, title: String, line: String) = synchronized(lock) {
        val values = append(success, title, line)
        val content = (0 until values.length()).joinToString("\n") { values.getString(it) }
        val builder = NotificationCompat.Builder(context, CHANNEL_REPORT_ID)
            .setSmallIcon(if (success) R.drawable.ic_twotone_cloud_done_24 else R.drawable.ic_twotone_cloud_error_24)
            .setContentTitle(context.getString(if (success) R.string.operation_report_success_title else R.string.operation_report_fail_title))
            .setContentText(context.getString(if (success) R.string.operation_report_success_short_content else R.string.operation_report_fail_short_content, values.length()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(deleteIntent(success))
        NotificationUtils.createNotification(context, if (success) 90 else 91, builder.build())
    }
}
