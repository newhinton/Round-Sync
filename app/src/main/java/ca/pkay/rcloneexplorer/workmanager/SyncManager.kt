package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import ca.pkay.rcloneexplorer.Activities.TriggerActivity
import ca.pkay.rcloneexplorer.Activities.TriggerActivity.Companion.ID_ALL_TASKS
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import java.util.Random

class SyncManager(private var mContext: Context) {

    private var mDatabase = DatabaseHandler(mContext)

    fun queue(trigger: Trigger) {
        if (trigger.triggerTarget == ID_ALL_TASKS) {
            queueAllTasks(null)
        } else if (TriggerActivity.UNICODE_CHAR_RANGE - trigger.triggerTarget >= 0) {
            queueAllTasks((TriggerActivity.UNICODE_CHAR_RANGE - trigger.triggerTarget).toInt().toChar().toString())
        } else{
            queue(trigger.triggerTarget)
        }
    }

    fun queue(task: Task) {
        queue(task.id)
    }

    fun queue(taskID: Long) {
        work(getOneTimeWorkRequest(taskID))
    }

    private fun queueAllTasks(prefixFilter: String?) {
        val mTaskList = mDatabase.allTasks
        mTaskList.sortedBy { it.title }
        for (i in mTaskList.indices) {
            if (prefixFilter == null) {
                workOneByOne(getOneTimeWorkRequest(mTaskList[i].id))
            } else {
                if (mTaskList[i].title.trim().uppercase().startsWith(prefixFilter)) {
                    workOneByOne(getOneTimeWorkRequest(mTaskList[i].id))
                }
            }
        }
    }

    private fun getOneTimeWorkRequest(taskID: Long): OneTimeWorkRequest {
        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()

        val data = Data.Builder()
        data.putLong(SyncWorker.TASK_ID, taskID)

        uploadWorkRequest.setInputData(data.build())
        uploadWorkRequest.addTag(taskID.toString())
        return uploadWorkRequest.build()
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

    private fun workOneByOne(request: OneTimeWorkRequest) {
        WorkManager.getInstance(mContext).enqueueUniqueWork("all_tasks_one_by_one", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
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