package ca.pkay.rcloneexplorer.Services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.workmanager.SyncManager

/**
 * This service is only meant to provide other apps
 * the ability to start a task.
 * Do not actually implement any sync changes, they only belong in the SyncManager/Worker!
 */
class SyncService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            val taskId = intent.getIntExtra("task", -1)

            if ("START_TASK" == action && taskId != -1) {
                Thread {
                    val db = DatabaseHandler(this)
                    val task = db.getTask(taskId.toLong())
                    if (task != null) {
                        SyncManager(this).queue(task)
                    }
                    stopSelf(startId)
                }.start()
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}