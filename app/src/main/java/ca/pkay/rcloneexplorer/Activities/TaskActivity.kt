package ca.pkay.rcloneexplorer.Activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.FilePicker
import ca.pkay.rcloneexplorer.Fragments.FolderSelectorCallback
import ca.pkay.rcloneexplorer.Fragments.RemoteFolderPickerFragment
import ca.pkay.rcloneexplorer.Items.Filter
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.Services.TriggerService
import ca.pkay.rcloneexplorer.ui.TaskEditComposeScreen
import ca.pkay.rcloneexplorer.util.ActivityHelper
import es.dmoral.toasty.Toasty

class TaskActivity : AppCompatActivity(), FolderSelectorCallback {

    private lateinit var rcloneInstance: Rclone
    private lateinit var dbHandler: DatabaseHandler

    private var existingTask: Task? = null
    private var localPathOverride by mutableStateOf<String?>(null)
    private var remotePathOverride by mutableStateOf<String?>(null)

    companion object {
        const val ID_EXTRA = "TASK_EDIT_ID"
        const val REQUEST_CODE_FP_LOCAL = 500
        const val REQUEST_CODE_FILTER = 333
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_FP_LOCAL -> {
                if (data != null) {
                    val path = data.getStringExtra(FilePicker.FILE_PICKER_RESULT)
                    if (!path.isNullOrBlank()) {
                        localPathOverride = path
                    }
                }
            }
            REQUEST_CODE_FILTER -> {
                // Filter created/saved, recomposition will query dbHandler.allFilters
            }
        }
    }

    override fun selectFolder(path: String) {
        remotePathOverride = path
        supportFragmentManager.popBackStack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)

        rcloneInstance = Rclone(this)
        dbHandler = DatabaseHandler(this)

        val extras = intent.extras
        if (extras != null && extras.containsKey(ID_EXTRA)) {
            val taskId = extras.getLong(ID_EXTRA)
            if (taskId != 0L) {
                existingTask = dbHandler.getTask(taskId)
                if (existingTask == null) {
                    Toasty.error(this, getString(R.string.taskactivity_task_not_found)).show()
                    finish()
                    return
                }
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    secondary = Color(0xFF38BDF8),
                    surface = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFF1E293B),
                    background = Color(0xFF0B1120),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                TaskEditComposeScreen(
                    existingTask = existingTask,
                    remotes = rcloneInstance.remotes,
                    filters = dbHandler.allFilters,
                    allTasks = dbHandler.allTasks,
                    localPathOverride = localPathOverride,
                    remotePathOverride = remotePathOverride,
                    onSaveTask = { taskToSave ->
                        if (existingTask == null) {
                            dbHandler.createTask(taskToSave)
                        } else {
                            dbHandler.updateTask(taskToSave)
                        }
                        finish()
                    },
                    onDeleteTask = if (existingTask != null) {
                        { taskToDelete ->
                            // Cancel triggers in TriggerService
                            val triggers = dbHandler.getTriggersForTask(taskToDelete.id)
                            val triggerService = TriggerService(this)
                            for (t in triggers) {
                                triggerService.cancelTrigger(t.id)
                            }
                            dbHandler.deleteTask(taskToDelete.id)
                            finish()
                        }
                    } else null,
                    onPickLocalPath = {
                        val intent = Intent(applicationContext, FilePicker::class.java).apply {
                            putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
                        }
                        startActivityForResult(intent, REQUEST_CODE_FP_LOCAL)
                    },
                    onPickRemotePath = { remote, currentPath ->
                        val fragment = RemoteFolderPickerFragment.newInstance(remote, this, currentPath)
                        supportFragmentManager.beginTransaction()
                            .add(android.R.id.content, fragment, "REMOTE_FOLDER_PICKER")
                            .addToBackStack("REMOTE_FOLDER_PICKER")
                            .commit()
                    },
                    onCreateFilter = {
                        val intent = Intent(this, FilterActivity::class.java)
                        startActivityForResult(intent, REQUEST_CODE_FILTER)
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}