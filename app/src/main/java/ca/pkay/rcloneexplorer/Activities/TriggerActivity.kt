package ca.pkay.rcloneexplorer.Activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.Trigger
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Services.TriggerService
import ca.pkay.rcloneexplorer.ui.TriggerEditComposeScreen
import ca.pkay.rcloneexplorer.util.ActivityHelper
import es.dmoral.toasty.Toasty

class TriggerActivity : AppCompatActivity() {

    companion object {
        const val ID_EXTRA = "TRIGGER_EDIT_ID"
        const val TARGET_TASK_ID_EXTRA = "TARGET_TASK_ID"
    }

    private lateinit var dbHandler: DatabaseHandler
    private var existingTrigger: Trigger? = null
    private var targetTaskId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)

        dbHandler = DatabaseHandler(this)

        val extras = intent.extras
        if (extras != null && extras.containsKey(ID_EXTRA)) {
            val triggerId = extras.getLong(ID_EXTRA)
            if (triggerId != 0L) {
                existingTrigger = dbHandler.getTrigger(triggerId)
                if (existingTrigger == null) {
                    Toasty.error(this, getString(R.string.triggeractivity_trigger_not_found)).show()
                    finish()
                    return
                }
            }
        }

        targetTaskId = intent.getLongExtra(TARGET_TASK_ID_EXTRA, -1L)

        val allTasks = dbHandler.allTasks
        if (allTasks.isEmpty()) {
            Toasty.error(this, getString(R.string.trigger_save_notasks)).show()
            finish()
            return
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
                TriggerEditComposeScreen(
                    existingTrigger = existingTrigger,
                    initialTargetTaskId = targetTaskId,
                    allTasks = allTasks,
                    onSaveTrigger = { triggerToSave ->
                        val savedTrigger = if (existingTrigger == null || existingTrigger?.id == Trigger.TRIGGER_ID_DOESNTEXIST) {
                            dbHandler.createTrigger(triggerToSave)
                        } else {
                            dbHandler.updateTrigger(triggerToSave)
                            triggerToSave
                        }
                        TriggerService(this).queueSingleTrigger(savedTrigger)
                        finish()
                    },
                    onDeleteTrigger = if (existingTrigger != null) {
                        { triggerToDelete ->
                            TriggerService(this).cancelTrigger(triggerToDelete.id)
                            dbHandler.deleteTrigger(triggerToDelete.id)
                            finish()
                        }
                    } else null,
                    onBack = { finish() }
                )
            }
        }
    }
}