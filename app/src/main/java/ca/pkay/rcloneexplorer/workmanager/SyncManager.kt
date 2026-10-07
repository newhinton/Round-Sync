package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import java.util.Random

class SyncManager(private var mContext: Context) {


    fun queue(trigger: Trigger) {
        queue(trigger.triggerTarget)
    }

    fun queue(task: Task) {
        queue(task.id)
    }

    /**
     * Queue a run of a stored task. A task only ever has one run pending or running:
     * if it is already queued or running (manual start, trigger, boot catch-up or
     * WorkManager resuming interrupted work), this request is dropped.
     */
    fun queue(taskID: Long) {
        WorkManager.getInstance(mContext)
            .enqueueUniqueWork(uniqueName(taskID), ExistingWorkPolicy.KEEP, buildTaskRequest(taskID))
    }

    /**
     * Queue a follow-up task from inside a running SyncWorker. If the follow-up task
     * is already pending or running (including a task that follows up on itself),
     * it runs once more after that run finishes instead of being dropped.
     */
    fun queueFollowup(taskID: Long) {
        WorkManager.getInstance(mContext)
            .enqueueUniqueWork(uniqueName(taskID), ExistingWorkPolicy.APPEND_OR_REPLACE, buildTaskRequest(taskID))
    }

    private fun buildTaskRequest(taskID: Long): OneTimeWorkRequest {
        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()

        val data = Data.Builder()
        data.putLong(SyncWorker.TASK_ID, taskID)

        uploadWorkRequest.setInputData(data.build())
        uploadWorkRequest.addTag(taskID.toString())
        return uploadWorkRequest.build()
    }

    private fun uniqueName(taskID: Long): String {
        return "task-$taskID"
    }

    fun queueEphemeral(task: Task) {

        task.id = Random().nextLong()
        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()

        val data = Data.Builder()
        data.putString(SyncWorker.TASK_EPHEMERAL, task.asJSON().toString())

        uploadWorkRequest.setInputData(data.build())
        uploadWorkRequest.addTag(task.id.toString())
        work(uploadWorkRequest.build())
    }

    private fun work(request: WorkRequest) {
        WorkManager.getInstance(mContext)
            .enqueue(request)
    }

    fun cancel() {
        WorkManager.getInstance(mContext)
            .cancelAllWork()
    }
    fun cancel(tag: String) {

        //Intent syncIntent = new Intent(context, SyncService.class);
        //syncIntent.setAction(TASK_CANCEL_ACTION);
        //syncIntent.putExtra(EXTRA_TASK_ID, intent.getLongExtra(EXTRA_TASK_ID, -1));
        //context.startService(syncIntent);
        Log.e("TAG", "CANCEL"+tag)
        WorkManager
            .getInstance(mContext)
            .cancelAllWorkByTag(tag)
    }
}