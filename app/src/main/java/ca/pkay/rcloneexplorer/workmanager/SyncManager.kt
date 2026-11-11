package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.BackoffPolicy
import androidx.work.WorkInfo
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import java.util.Random
import java.util.concurrent.TimeUnit

class SyncManager(private var mContext: Context) {

    companion object {
        internal const val SYNC_WORK_TAG = "sync_work"
    }

    fun queue(trigger: Trigger) {
        queue(trigger.triggerTarget)
    }

    fun queue(task: Task) {
        queue(task.id)
    }

    fun queue(taskID: Long) = queueTask(taskID, ExistingWorkPolicy.KEEP)

    fun queueFollowup(taskID: Long) {
        // Called by a worker: record the current generation so duplicate requests can be
        // coalesced even when another run starts before their dispatchers execute.
        val manager = WorkManager.getInstance(mContext)
        val current = manager.getWorkInfosForUniqueWork("task-$taskID").get()
        if (current.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) return
        val request = OneTimeWorkRequestBuilder<FollowupWorker>()
            .setInputData(Data.Builder().putLong(SyncWorker.TASK_ID, taskID)
                .putStringArray(FollowupWorker.KNOWN_WORK_IDS, current.map { it.id.toString() }.toTypedArray())
                .build())
            .addTag(taskID.toString())
            .addTag(SYNC_WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork("task-followup-$taskID", ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
    }

    private fun queueTask(taskID: Long, policy: ExistingWorkPolicy): androidx.work.Operation {
        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()

        val data = Data.Builder()
        data.putLong(SyncWorker.TASK_ID, taskID)

        uploadWorkRequest.setInputData(data.build())
        uploadWorkRequest.addTag(taskID.toString())
        uploadWorkRequest.addTag(SYNC_WORK_TAG)
        return WorkManager.getInstance(mContext).enqueueUniqueWork("task-$taskID", policy, uploadWorkRequest.build())
    }

    fun queueEphemeral(task: Task) {

        task.id = Random().nextLong()
        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()

        val data = Data.Builder()
        data.putString(SyncWorker.TASK_EPHEMERAL, task.asJSON().toString())

        uploadWorkRequest.setInputData(data.build())
        uploadWorkRequest.addTag(task.id.toString())
        uploadWorkRequest.addTag(SYNC_WORK_TAG)
        work(uploadWorkRequest.build())
    }

    private fun work(request: WorkRequest) {
        WorkManager.getInstance(mContext)
            .enqueue(request)
    }

    fun cancel() {
        WorkManager.getInstance(mContext)
            .cancelAllWorkByTag(SYNC_WORK_TAG)
    }
    fun cancel(tag: String) {
        WorkManager
            .getInstance(mContext)
            .cancelAllWorkByTag(tag)
    }
}
