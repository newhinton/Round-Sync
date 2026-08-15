package ca.pkay.rcloneexplorer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import ca.pkay.rcloneexplorer.Services.TriggerService
import ca.pkay.rcloneexplorer.ui.TaskWithTriggers
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHandler = DatabaseHandler(application)
    private val triggerService = TriggerService(application)
    private val syncManager = SyncManager(application)

    private val _tasksWithTriggers = MutableStateFlow<List<TaskWithTriggers>>(emptyList())
    val tasksWithTriggers: StateFlow<List<TaskWithTriggers>> = _tasksWithTriggers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTasks()
    }

    fun refresh() {
        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) {
                val tasks = dbHandler.allTasks
                val triggers = dbHandler.allTrigger
                val triggersByTask = triggers.groupBy { it.triggerTarget }
                tasks.map { task ->
                    TaskWithTriggers(
                        task = task,
                        triggers = triggersByTask[task.id] ?: emptyList()
                    )
                }
            }
            _tasksWithTriggers.value = items
            _isLoading.value = false
        }
    }

    fun runTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            syncManager.queue(task)
        }
    }

    fun toggleTrigger(trigger: Trigger, isEnabled: Boolean) {
        // Optimistic UI update
        val currentList = _tasksWithTriggers.value
        _tasksWithTriggers.value = currentList.map { item ->
            if (item.task.id == trigger.triggerTarget) {
                val updatedTriggers = item.triggers.map { t ->
                    if (t.id == trigger.id) {
                        Trigger(t.id).apply {
                            title = t.title
                            this.isEnabled = isEnabled
                            time = t.time
                            setWeekdays(t.getWeekdays().toByte())
                            triggerTarget = t.triggerTarget
                            type = t.type
                        }
                    } else t
                }
                item.copy(triggers = updatedTriggers)
            } else item
        }

        viewModelScope.launch(Dispatchers.IO) {
            trigger.isEnabled = isEnabled
            dbHandler.updateTrigger(trigger)
            if (isEnabled) {
                triggerService.queueSingleTrigger(trigger)
            } else {
                triggerService.cancelTrigger(trigger.id)
            }
        }
    }

    fun deleteTask(taskId: Long) {
        // Optimistic UI removal
        val currentList = _tasksWithTriggers.value
        val taskToDelete = currentList.find { it.task.id == taskId }
        _tasksWithTriggers.value = currentList.filter { it.task.id != taskId }

        viewModelScope.launch(Dispatchers.IO) {
            // Cancel all triggers first
            taskToDelete?.triggers?.forEach { trigger ->
                triggerService.cancelTrigger(trigger.id)
            }
            // Cascading delete in database
            dbHandler.deleteTask(taskId)
        }
    }

    fun deleteTrigger(triggerId: Long, taskId: Long) {
        // Optimistic UI removal
        val currentList = _tasksWithTriggers.value
        _tasksWithTriggers.value = currentList.map { item ->
            if (item.task.id == taskId) {
                item.copy(triggers = item.triggers.filter { it.id != triggerId })
            } else item
        }

        viewModelScope.launch(Dispatchers.IO) {
            triggerService.cancelTrigger(triggerId)
            dbHandler.deleteTrigger(triggerId)
        }
    }
}
