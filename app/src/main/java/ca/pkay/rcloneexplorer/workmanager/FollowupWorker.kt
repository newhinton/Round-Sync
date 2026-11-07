package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Dispatches a deduplicated follow-up without depending on another worker's success. */
class FollowupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        const val KNOWN_WORK_IDS = "KNOWN_WORK_IDS"
    }

    override fun doWork(): Result {
        return try {
            dispatch()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun dispatch(): Result {
        val taskId = inputData.getLong(SyncWorker.TASK_ID, -1L)
        if (taskId < 0) return Result.failure()
        val known = inputData.getStringArray(KNOWN_WORK_IDS)?.toSet() ?: emptySet()
        val work = WorkManager.getInstance(applicationContext)
            .getWorkInfosForUniqueWork("task-$taskId").get()
        // A newer run already satisfies this generation's request, even if it finished quickly.
        if (work.any { it.id.toString() !in known }) return Result.success()
        // A pending run already fulfills the follow-up. A running one needs a repeat, so retry
        // after WorkManager's minimum backoff rather than occupying a thread while it finishes.
        if (work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) return Result.success()
        if (work.any { it.state == WorkInfo.State.RUNNING }) return Result.retry()
        SyncManager(applicationContext).queue(taskId).result.get()
        return Result.success()
    }
}
