package ca.pkay.rcloneexplorer.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.pkay.rcloneexplorer.Activities.TriggerActivity
import ca.pkay.rcloneexplorer.Database.DatabaseHandler
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Services.TriggerService
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskWithTriggers(
    val task: Task,
    val triggers: List<Trigger>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksComposeScreen(
    onNewTaskClick: () -> Unit,
    onEditTaskClick: (Task) -> Unit,
    onManageTriggersClick: (Task) -> Unit
) {
    val context = LocalContext.current
    val dbHandler = remember { DatabaseHandler(context) }
    var taskDataList by remember { mutableStateOf<List<TaskWithTriggers>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun loadTasksAndTriggers() {
        scope.launch {
            isLoading = true
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
            taskDataList = items
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadTasksAndTriggers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sync Tasks & Schedules",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = { loadTasksAndTriggers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh tasks")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTaskClick,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Task", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && taskDataList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (taskDataList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SyncAlt,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(22.dp)
                                .fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "No Sync Tasks Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Automate background transfers between local storage and cloud remotes with custom triggers and schedules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onNewTaskClick,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Task")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                items(taskDataList, key = { it.task.id }) { item ->
                    TaskCard(
                        task = item.task,
                        triggers = item.triggers,
                        onRunTask = {
                            SyncManager(context).queue(item.task)
                            scope.launch {
                                snackbarHostState.showSnackbar("Started sync task: ${item.task.title}")
                            }
                        },
                        onToggleTrigger = { trigger, isEnabled ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    trigger.isEnabled = isEnabled
                                    dbHandler.updateTrigger(trigger)
                                    val triggerService = TriggerService(context)
                                    if (isEnabled) {
                                        triggerService.queueSingleTrigger(trigger)
                                    } else {
                                        triggerService.cancelTrigger(trigger.id)
                                    }
                                }
                                loadTasksAndTriggers()
                            }
                        },
                        onEditTask = { onEditTaskClick(item.task) },
                        onManageTriggers = { onManageTriggersClick(item.task) },
                        onDeleteTask = { taskToDelete = item.task }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    taskToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to delete sync task \"${target.title}\"? Associated schedules will also be cancelled.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val triggers = dbHandler.allTrigger.filter { it.triggerTarget == target.id }
                                val triggerService = TriggerService(context)
                                for (t in triggers) {
                                    triggerService.cancelTrigger(t.id)
                                    dbHandler.deleteTrigger(t.id)
                                }
                                dbHandler.deleteTask(target.id)
                            }
                            loadTasksAndTriggers()
                            taskToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TaskCard(
    task: Task,
    triggers: List<Trigger>,
    onRunTask: () -> Unit,
    onToggleTrigger: (Trigger, Boolean) -> Unit,
    onEditTask: () -> Unit,
    onManageTriggers: () -> Unit,
    onDeleteTask: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val remote = remember(task) {
        RemoteItem(task.remoteId, task.remoteType.toString())
    }

    val directionLabel = when (task.direction) {
        SyncDirectionObject.SYNC_LOCAL_TO_REMOTE -> "Upload (Local → Cloud)"
        SyncDirectionObject.SYNC_REMOTE_TO_LOCAL -> "Download (Cloud → Local)"
        SyncDirectionObject.COPY_LOCAL_TO_REMOTE -> "Copy to Cloud"
        SyncDirectionObject.COPY_REMOTE_TO_LOCAL -> "Copy to Local"
        SyncDirectionObject.MOVE_LOCAL_TO_REMOTE -> "Move to Cloud"
        SyncDirectionObject.MOVE_REMOTE_TO_LOCAL -> "Move to Local"
        SyncDirectionObject.SYNC_BIDIRECTIONAL, SyncDirectionObject.SYNC_BIDIRECTIONAL_INITIAL -> "Two-Way BiSync"
        else -> "Sync"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEditTask),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Remote Icon, Task Title, Run & 3-Dot Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = remote.remoteIcon),
                        contentDescription = task.remoteId,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (task.title.isNullOrBlank()) "${task.remoteId}: ${task.remotePath}" else task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = directionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRunTask,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Run task now",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Run Task Now") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRunTask()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Task") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEditTask()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add / Manage Triggers") },
                                leadingIcon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onManageTriggers()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Task") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteTask()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Path Details Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Local: ${task.localPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remote: ${task.remoteId}:${task.remotePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Trigger & Schedule Section
            if (triggers.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    triggers.forEach { trigger ->
                        val scheduleSummary = if (trigger.type == Trigger.TRIGGER_TYPE_INTERVAL) {
                            "Interval: Every ${trigger.time}m"
                        } else {
                            val hour = trigger.time / 60
                            val minute = trigger.time % 60
                            val timeStr = String.format("%02d:%02d", hour, minute)
                            "Scheduled: $timeStr"
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (trigger.type == Trigger.TRIGGER_TYPE_INTERVAL) Icons.Outlined.Update else Icons.Outlined.Alarm,
                                        contentDescription = null,
                                        tint = if (trigger.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (trigger.title.isNotBlank()) trigger.title else scheduleSummary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = scheduleSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = trigger.isEnabled,
                                    onCheckedChange = { isChecked ->
                                        onToggleTrigger(trigger, isChecked)
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onManageTriggers,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.AlarmAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Automatic Schedule / Trigger",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
