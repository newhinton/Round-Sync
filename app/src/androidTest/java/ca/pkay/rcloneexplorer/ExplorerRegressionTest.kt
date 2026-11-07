package ca.pkay.rcloneexplorer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.RecyclerViewAdapters.FileExplorerRecyclerViewAdapter
import ca.pkay.rcloneexplorer.workmanager.EphemeralTaskManager
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import ca.pkay.rcloneexplorer.workmanager.SyncWorker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class BlockingTestWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        var started = CountDownLatch(1)
        var release = CountDownLatch(1)
        var fail = false
        var returnedFailure = false
    }
    override fun doWork(): Result {
        started.countDown()
        release.await(30, TimeUnit.SECONDS)
        if (fail) {
            returnedFailure = true
            return Result.failure()
        }
        return Result.success()
    }
}

class DispatchGateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        var started = CountDownLatch(1)
        var release = CountDownLatch(1)
    }
    override fun doWork(): Result {
        started.countDown()
        release.await(30, TimeUnit.SECONDS)
        return Result.success()
    }
}

@RunWith(AndroidJUnit4::class)
class ExplorerRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun item(remote: RemoteItem, path: String, mime: String = "text/plain") =
        FileItem(remote, path, path.substringAfterLast('/'), 1, "2026-01-01T00:00:00Z", mime, false, false)

    @Test
    fun recycledThumbnailRestoresFileIconTint() {
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.AppTheme)
            val listener = object : FileExplorerRecyclerViewAdapter.OnClickListener {
                override fun onFileClicked(fileItem: FileItem) {}
                override fun onDirectoryClicked(fileItem: FileItem, position: Int) {}
                override fun onFilesSelected() {}
                override fun onFileDeselected() {}
                override fun onFileOptionsClicked(view: View, fileItem: FileItem) {}
                override fun getThumbnailServerParams() = arrayOf("test", "1")
            }
            val adapter = FileExplorerRecyclerViewAdapter(themed, View(themed), View(themed), listener)
            val remote = RemoteItem("fixture", "alias")
            val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
            val tint = holder.fileIcon.imageTintList
            assertNotNull("The theme must tint file icons for this regression", tint)
            adapter.newData(listOf(item(remote, "photo.jpg", "image/jpeg"), item(remote, "document.txt")))
            adapter.showThumbnails(true)
            adapter.onBindViewHolder(holder, 0)
            assertNull("Thumbnails must display their original colors", holder.fileIcon.imageTintList)
            adapter.onBindViewHolder(holder, 1)
            assertEquals("Recycled ordinary icons need their theme tint", tint, holder.fileIcon.imageTintList)
            adapter.onBindViewHolder(holder, 0)
            adapter.showThumbnails(false)
            adapter.onBindViewHolder(holder, 0)
            assertEquals("Turning thumbnails off restores the icon tint", tint, holder.fileIcon.imageTintList)
        }
    }

    @Test
    fun followupQueuesBehindCurrentTaskAndRemainsCancellable() {
        val manager = WorkManager.getInstance(context)
        val taskId = Random.nextLong(1_000_000_000L, Long.MAX_VALUE)
        val name = "task-$taskId"
        BlockingTestWorker.started = CountDownLatch(1)
        BlockingTestWorker.release = CountDownLatch(1)
        BlockingTestWorker.fail = false
        val blocker = OneTimeWorkRequestBuilder<BlockingTestWorker>().build()
        try {
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, blocker).result.get(10, TimeUnit.SECONDS)
            assertTrue("Fixture task should be running", BlockingTestWorker.started.await(10, TimeUnit.SECONDS))
            SyncManager(context).queueFollowup(taskId)
            SyncManager(context).queueFollowup(taskId)
            val queued = manager.getWorkInfosByTag(taskId.toString()).get(10, TimeUnit.SECONDS)
            assertEquals(2, queued.size)
            assertTrue(queued.all { it.tags.contains("sync_work") })
            SyncManager(context).cancel(taskId.toString())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var states = emptyList<WorkInfo.State>()
            while (System.nanoTime() < deadline) {
                states = queued.map { manager.getWorkInfoById(it.id).get(5, TimeUnit.SECONDS).state }
                if (states.all { it == WorkInfo.State.CANCELLED }) break
                Thread.sleep(50)
            }
            assertTrue("All follow-up dispatchers must be cancellable: $states", states.all { it == WorkInfo.State.CANCELLED })
        } finally {
            BlockingTestWorker.release.countDown()
            manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            manager.cancelAllWorkByTag(taskId.toString()).result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun followupsDoNotDuplicateAlreadyPendingTask() {
        val manager = WorkManager.getInstance(context)
        val taskId = Random.nextLong(1_000_000_000L, Long.MAX_VALUE)
        val name = "task-$taskId"
        val blocker = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build()).build()
        try {
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, blocker).result.get(10, TimeUnit.SECONDS)
            SyncManager(context).queueFollowup(taskId)
            SyncManager(context).queueFollowup(taskId)
            assertTrue(manager.getWorkInfosForUniqueWork("task-followup-$taskId").get(5, TimeUnit.SECONDS).isEmpty())
            val main = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            assertEquals("A pending task satisfies both follow-ups", 1, main.size)
        } finally {
            manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun nextGenerationIsNotLostWhilePreviousDispatcherRuns() {
        val manager = WorkManager.getInstance(context)
        val taskId = Random.nextLong(1_000_000_000L, Long.MAX_VALUE)
        val name = "task-$taskId"
        val dispatchName = "task-followup-$taskId"
        DispatchGateWorker.started = CountDownLatch(1)
        DispatchGateWorker.release = CountDownLatch(1)
        BlockingTestWorker.started = CountDownLatch(1)
        BlockingTestWorker.release = CountDownLatch(1)
        BlockingTestWorker.fail = false
        val gate = OneTimeWorkRequestBuilder<DispatchGateWorker>().build()
        val target = OneTimeWorkRequestBuilder<BlockingTestWorker>().build()
        try {
            manager.enqueueUniqueWork(dispatchName, ExistingWorkPolicy.KEEP, gate).result.get(10, TimeUnit.SECONDS)
            assertTrue(DispatchGateWorker.started.await(10, TimeUnit.SECONDS))
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, target).result.get(10, TimeUnit.SECONDS)
            assertTrue(BlockingTestWorker.started.await(10, TimeUnit.SECONDS))
            SyncManager(context).queueFollowup(taskId)
            assertEquals("A new generation must append behind the existing dispatcher", 2,
                manager.getWorkInfosForUniqueWork(dispatchName).get(5, TimeUnit.SECONDS).size)
            BlockingTestWorker.release.countDown()
            DispatchGateWorker.release.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var main = emptyList<WorkInfo>()
            while (System.nanoTime() < deadline) {
                main = manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
                if (main.any { it.id != target.id }) break
                Thread.sleep(100)
            }
            assertTrue("The next generation must execute: $main", main.any { it.id != target.id })
        } finally {
            BlockingTestWorker.release.countDown()
            DispatchGateWorker.release.countDown()
            manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            manager.cancelUniqueWork(dispatchName).result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun followupRunsAfterPredecessorFails() {
        val manager = WorkManager.getInstance(context)
        val taskId = Random.nextLong(1_000_000_000L, Long.MAX_VALUE)
        val name = "task-$taskId"
        BlockingTestWorker.started = CountDownLatch(1)
        BlockingTestWorker.release = CountDownLatch(1)
        BlockingTestWorker.fail = true
        BlockingTestWorker.returnedFailure = false
        val blocker = OneTimeWorkRequestBuilder<BlockingTestWorker>().build()
        try {
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, blocker).result.get(10, TimeUnit.SECONDS)
            assertTrue(BlockingTestWorker.started.await(10, TimeUnit.SECONDS))
            SyncManager(context).queueFollowup(taskId)
            SyncManager(context).queueFollowup(taskId)
            BlockingTestWorker.release.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var work = emptyList<WorkInfo>()
            var dispatch = emptyList<WorkInfo>()
            while (System.nanoTime() < deadline) {
                work = manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
                dispatch = manager.getWorkInfosForUniqueWork("task-followup-$taskId").get(5, TimeUnit.SECONDS)
                if (work.any { it.id != blocker.id } && dispatch.size == 2 && dispatch.all { it.state == WorkInfo.State.SUCCEEDED }) break
                Thread.sleep(100)
            }
            assertTrue("Predecessor must return failure", BlockingTestWorker.returnedFailure)
            assertTrue("Both dispatchers must finish after predecessor fails: $dispatch", dispatch.size == 2 && dispatch.all { it.state == WorkInfo.State.SUCCEEDED })
            assertEquals("Duplicate follow-ups must dispatch only one saved task: $work", 1, work.count { it.id != blocker.id })
        } finally {
            BlockingTestWorker.release.countDown()
            BlockingTestWorker.fail = false
            manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            manager.cancelUniqueWork("task-followup-$taskId").result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun localMoveAndBatchDeleteBroadcastAffectedDirectories() {
        val root = File(context.cacheDir, "explorer-test-${UUID.randomUUID()}").apply { mkdirs() }
        val name = "fixture${UUID.randomUUID().toString().replace("-", "")}"
        val remote = RemoteItem(name, "alias")
        val rclone = Rclone(context)
        val events = LinkedBlockingQueue<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { events.offer(intent) }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        try {
            val create = rclone.configCreateNoInteract(arrayListOf(name, "alias", "remote", root.absolutePath))!!
            assertTrue(create.waitFor(30, TimeUnit.SECONDS))
            assertEquals("Local alias fixture creation", 0, create.exitValue())
            broadcasts.registerReceiver(receiver, IntentFilter(context.getString(R.string.background_service_broadcast)))
            File(root, "destination").mkdirs()
            File(root, "moving.txt").writeText("move")
            EphemeralTaskManager.queueMove(context, remote, "destination", item(remote, "moving.txt"), "//$name")
            val move = events.poll(30, TimeUnit.SECONDS)
            assertNotNull("Move should broadcast completion", move)
            assertEquals("destination", move!!.getStringExtra(context.getString(R.string.background_service_broadcast_data_path)))
            assertEquals("//$name", move.getStringExtra(context.getString(R.string.background_service_broadcast_data_path2)))
            assertTrue("The CLI should move the file", File(root, "destination/moving.txt").isFile)
            assertFalse(File(root, "moving.txt").exists())

            File(root, "destination/other.txt").writeText("delete")
            EphemeralTaskManager.queueDelete(context, remote, listOf(item(remote, "destination/moving.txt"), item(remote, "destination/other.txt")), "destination")
            val deletion = events.poll(30, TimeUnit.SECONDS)
            assertNotNull("Batch delete should broadcast completion", deletion)
            assertEquals("destination", deletion!!.getStringExtra(context.getString(R.string.background_service_broadcast_data_path)))
            assertFalse(File(root, "destination/moving.txt").exists())
            assertFalse(File(root, "destination/other.txt").exists())

            File(root, "destination/partial.txt").writeText("delete")
            val missing = FileItem(remote, "destination/missing", "missing", 0, "2026-01-01T00:00:00Z", "", true, false)
            EphemeralTaskManager.queueDelete(context, remote, listOf(item(remote, "destination/partial.txt"), missing), "destination")
            val partial = events.poll(30, TimeUnit.SECONDS)
            assertNotNull("Partial delete failure must still invalidate the directory", partial)
            assertEquals("destination", partial!!.getStringExtra(context.getString(R.string.background_service_broadcast_data_path)))
            assertFalse(File(root, "destination/partial.txt").exists())
        } finally {
            val manager = WorkManager.getInstance(context)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < deadline && manager.getWorkInfosByTag("ephemeral_work").get(5, TimeUnit.SECONDS).any { !it.state.isFinished }) {
                Thread.sleep(50)
            }
            broadcasts.unregisterReceiver(receiver)
            rclone.config("delete", arrayListOf(name))?.waitFor(30, TimeUnit.SECONDS)
            root.deleteRecursively()
        }
    }
}
