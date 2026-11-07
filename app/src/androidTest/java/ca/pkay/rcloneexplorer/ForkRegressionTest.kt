package ca.pkay.rcloneexplorer

import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.BroadcastReceivers.ClearReportBroadcastReceiver
import ca.pkay.rcloneexplorer.Database.DatabaseInfo
import ca.pkay.rcloneexplorer.Database.json.Importer
import ca.pkay.rcloneexplorer.Database.json.SharedPreferencesBackup
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.notifications.ReportNotifications
import ca.pkay.rcloneexplorer.notifications.SyncServiceNotifications
import ca.pkay.rcloneexplorer.notifications.dataStore
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import ca.pkay.rcloneexplorer.workmanager.SyncWorker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Uses disposable target-app data; run only on a test device. */
@RunWith(AndroidJUnit4::class)
class ForkRegressionTest {
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        testContext = InstrumentationRegistry.getInstrumentation().targetContext
        testContext.deleteDatabase(DatabaseInfo.DATABASE_NAME)
        runBlocking { testContext.dataStore.edit { it.clear() } }
    }

    @After
    fun tearDown() {
        testContext.deleteDatabase(DatabaseInfo.DATABASE_NAME)
        runBlocking { testContext.dataStore.edit { it.clear() } }
    }

    @Test
    fun multilineSuccessDescriptionsCountAsTwoTasks() {
        val reports = ReportNotifications(testContext)
        reports.addToSuccessReport("first", "transferred\n\n2 deletions")
        reports.addToSuccessReport("second", "transferred\n\n3 deletions")
        assertEquals(2, reports.getSucesses())
    }

    @Test
    fun displayedReportCountsTasksRatherThanDescriptionLines() {
        val manager = testContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            ReportNotifications.CHANNEL_REPORT_ID, "Test reports", NotificationManager.IMPORTANCE_LOW
        ))
        try {
            val reports = ReportNotifications(testContext)
            reports.addToSuccessReport("first", "transferred\n\n2 deletions")
            reports.showSuccessReport("second", "transferred\n3 deletions")
            val notification = manager.activeNotifications.firstOrNull { it.id == 90 }
            assertTrue("Expected success report notification", notification != null)
            assertEquals(
                testContext.getString(R.string.operation_report_success_short_content, 2),
                notification!!.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            )
        } finally {
            manager.cancel(90)
        }
    }

    @Test
    fun concurrentResultsPreserveEveryEntry() {
        val threads = (0 until 20).map { index ->
            Thread { ReportNotifications(testContext).addToSuccessReport("task $index", "done\nmore detail") }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(20, ReportNotifications(testContext).getSucesses())
    }

    @Test
    fun failureReportAlsoCountsEntriesAndDismisses() {
        val manager = testContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(ReportNotifications.CHANNEL_REPORT_ID, "Test reports", NotificationManager.IMPORTANCE_LOW))
        val reports = ReportNotifications(testContext)
        try {
            reports.addToFailureReport("first", "error\nstack\nmore")
            reports.showFailReport("second", "error\nmore")
            assertEquals(2, reports.getFailures())
            val summary = manager.activeNotifications.first { it.id == 91 }.notification
            assertEquals(testContext.getString(R.string.operation_report_fail_short_content, 2),
                summary.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            val token = runBlocking { testContext.dataStore.data.first()[ReportNotifications.FAILURE_GENERATION] }
            ClearReportBroadcastReceiver().onReceive(testContext, Intent().apply {
                action = ReportNotifications.REPORT_FAIL_DELETE_INTENT
                putExtra(ReportNotifications.EXTRA_GENERATION, token)
            })
            assertEquals(0, reports.getFailures())
        } finally {
            manager.cancel(91)
        }
    }

    @Test
    fun migrationDropsUncountableLegacyCacheAndCancelsStaleNotifications() {
        val manager = testContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(ReportNotifications.CHANNEL_REPORT_ID, "Test reports", NotificationManager.IMPORTANCE_LOW))
        val legacy = Notification.Builder(testContext, ReportNotifications.CHANNEL_REPORT_ID)
            .setSmallIcon(R.drawable.ic_twotone_cloud_done_24).setContentTitle("old").build()
        manager.notify(90, legacy)
        manager.notify(91240, legacy)
        runBlocking { testContext.dataStore.edit {
            it[stringPreferencesKey("NOTIFICATION_CACHE_SUCCESS")] = "old\nambiguous\n"
            it[intPreferencesKey("NOTIFICATION_LAST_SUCCESS_ID")] = 91240
        } }
        val reports = ReportNotifications(testContext)
        reports.addToSuccessReport("new", "done")
        assertEquals(1, reports.getSucesses())
        assertTrue(manager.activeNotifications.none { it.id == 90 || it.id == 91240 })
        assertTrue(runBlocking { testContext.dataStore.data.first()[stringPreferencesKey("NOTIFICATION_CACHE_SUCCESS")] } == null)
    }

    @Test
    fun firstAndSummaryDismissalsClearOnlyTheirOwnGeneration() {
        val manager = testContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(SyncServiceNotifications.CHANNEL_SUCCESS_ID, "Test success", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(ReportNotifications.CHANNEL_REPORT_ID, "Test reports", NotificationManager.IMPORTANCE_LOW))
        val reports = ReportNotifications(testContext)
        val notifications = SyncServiceNotifications(testContext)
        val receiver = ClearReportBroadcastReceiver()
        fun token() = runBlocking { testContext.dataStore.data.first()[ReportNotifications.SUCCESS_GENERATION] }
        fun dismiss(value: String?) = receiver.onReceive(testContext, Intent().apply {
            action = ReportNotifications.REPORT_SUCCESS_DELETE_INTENT
            putExtra(ReportNotifications.EXTRA_GENERATION, value)
        })
        try {
            notifications.showSuccessNotificationOrReport("first", "done", 91230)
            val first = manager.activeNotifications.first { it.id == 91230 }.notification
            assertTrue("First notification needs a delete intent", first.deleteIntent != null)
            val firstToken = token()
            dismiss(firstToken)
            assertEquals(0, reports.getSucesses())

            notifications.showSuccessNotificationOrReport("first", "done", 91231)
            val staleToken = token()
            notifications.showSuccessNotificationOrReport("second", "done", 91232)
            val summary = manager.activeNotifications.first { it.id == 90 }.notification
            assertTrue("Summary needs a delete intent", summary.deleteIntent != null)
            val currentToken = token()
            dismiss(staleToken)
            assertEquals(2, reports.getSucesses())
            dismiss(currentToken)
            assertEquals(0, reports.getSucesses())
        } finally {
            manager.cancel(91230)
            manager.cancel(91231)
            manager.cancel(91232)
            manager.cancel(90)
        }
    }

    @Test
    fun malformedBackupLeavesExistingTask() {
        val database = DatabaseHandler(testContext)
        database.createTask(Task(0).apply { title = "existing" })
        var rejected = false
        try {
            Importer.importJson("{\"tasks\":\"invalid\",\"trigger\":[],\"filters\":[]}", testContext)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Malformed backup must be rejected", rejected)
        assertEquals(listOf("existing"), database.allTasks.map { it.title })
    }

    @Test
    fun oldBackupWithoutOptionalArraysImportsTasks() {
        val task = Task(123).apply { title = "legacy" }
        Importer.importJson("{\"tasks\":[${task.asJSON()}]}", testContext)
        assertEquals(listOf("legacy"), DatabaseHandler(testContext).allTasks.map { it.title })
    }

    @Test
    fun duplicateTaskIdsFailPreflightAndPreserveExistingTask() {
        val database = DatabaseHandler(testContext)
        database.createTask(Task(0).apply { title = "existing" })
        val duplicate = Task(123).apply { title = "duplicate" }.asJSON()
        val json = "{\"tasks\":[$duplicate,$duplicate]}"
        var rejected = false
        try {
            Importer.validateJson(json)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Duplicate IDs must fail before ZIP configuration replacement", rejected)
        assertEquals(listOf("existing"), database.allTasks.map { it.title })
    }

    @Test
    fun malformedSectionsAndPreferencesFailPreflight() {
        for (json in listOf("{}", "{\"tasks\":null}", "{\"tasks\":{}}", "{\"tasks\":[{}]}")) {
            var rejected = false
            try { Importer.validateJson(json) } catch (_: Exception) { rejected = true }
            assertTrue("Must reject $json", rejected)
        }
        val preferences = JSONObject(SharedPreferencesBackup.export(testContext))
        preferences.put("isDarkTheme", "invalid")
        var rejected = false
        try { SharedPreferencesBackup.validateJson(preferences.toString()) } catch (_: Exception) { rejected = true }
        assertTrue("Malformed preferences must fail before config replacement", rejected)
    }

    @Test
    fun savedTaskAlreadyPendingIsNotQueuedAgain() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(appContext)
        val taskId = Random.nextLong(1_000_000_000L, Long.MAX_VALUE)
        val name = "task-$taskId"
        // A device-idle constraint prevents the placeholder from running on the active emulator.
        val blocker = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()
        try {
            workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, blocker)
                .result.get(10, TimeUnit.SECONDS)
            SyncManager(appContext).queue(taskId)
            SyncManager(appContext).queue(taskId)
            val extraRuns = workManager.getWorkInfosByTag(taskId.toString())
                .get(10, TimeUnit.SECONDS)
            assertTrue("A pending saved task must not enqueue duplicate runs: $extraRuns", extraRuns.isEmpty())
        } finally {
            workManager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            workManager.cancelAllWorkByTag(taskId.toString()).result.get(10, TimeUnit.SECONDS)
        }
    }
}
