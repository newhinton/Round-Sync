package ca.pkay.rcloneexplorer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Items.Trigger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditComposeScreen(
    existingTrigger: Trigger?,
    initialTargetTaskId: Long?,
    allTasks: List<Task>,
    onSaveTrigger: (Trigger) -> Unit,
    onDeleteTrigger: ((Trigger) -> Unit)?,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(existingTrigger?.title ?: "") }
    var isEnabled by remember { mutableStateOf(existingTrigger?.isEnabled ?: true) }
    var triggerType by remember {
        mutableStateOf(existingTrigger?.type ?: Trigger.TRIGGER_TYPE_INTERVAL)
    }
    var selectedTaskId by remember {
        mutableStateOf(
            existingTrigger?.triggerTarget
                ?: initialTargetTaskId?.takeIf { it != -1L }
                ?: allTasks.firstOrNull()?.id
                ?: -1L
        )
    }

    // Interval state (minutes)
    var intervalMinutes by remember {
        mutableStateOf(
            if (existingTrigger?.type == Trigger.TRIGGER_TYPE_INTERVAL && (existingTrigger.time > 0)) {
                existingTrigger.time
            } else 60
        )
    }

    // Schedule state (time of day in minutes, weekdays)
    val initialTime = if (existingTrigger?.type == Trigger.TRIGGER_TYPE_SCHEDULE) existingTrigger.time else 480 // 08:00 AM default
    var scheduleHour by remember { mutableStateOf(initialTime / 60) }
    var scheduleMinute by remember { mutableStateOf(initialTime % 60) }

    var weekdayMon by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_MON) ?: true) }
    var weekdayTue by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_TUE) ?: true) }
    var weekdayWed by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_WED) ?: true) }
    var weekdayThu by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_THU) ?: true) }
    var weekdayFri by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_FRI) ?: true) }
    var weekdaySat by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_SAT) ?: true) }
    var weekdaySun by remember { mutableStateOf(existingTrigger?.isEnabledAtDay(Trigger.TRIGGER_DAY_SUN) ?: true) }

    var taskDropdownExpanded by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingTrigger == null) "Create Schedule / Trigger" else "Edit Trigger",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (allTasks.isEmpty() || selectedTaskId == -1L) {
                                validationError = "No target sync task available. Please create a task first."
                                return@TextButton
                            }

                            val triggerToSave = Trigger(existingTrigger?.id ?: Trigger.TRIGGER_ID_DOESNTEXIST).apply {
                                this.title = title.ifBlank {
                                    val targetTitle = allTasks.find { it.id == selectedTaskId }?.title ?: "Task"
                                    if (triggerType == Trigger.TRIGGER_TYPE_INTERVAL) "Auto Sync: $targetTitle (${intervalMinutes}m)"
                                    else "Daily Sync: $targetTitle (${String.format("%02d:%02d", scheduleHour, scheduleMinute)})"
                                }
                                this.isEnabled = isEnabled
                                this.triggerTarget = selectedTaskId
                                this.type = triggerType
                                if (triggerType == Trigger.TRIGGER_TYPE_INTERVAL) {
                                    this.time = intervalMinutes
                                } else {
                                    this.time = scheduleHour * 60 + scheduleMinute
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_MON, weekdayMon)
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_TUE, weekdayTue)
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_WED, weekdayWed)
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_THU, weekdayThu)
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_FRI, weekdayFri)
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_SAT, weekdaySat)
                                    this.setEnabledAtDay(Trigger.TRIGGER_DAY_SUN, weekdaySun)
                                }
                            }
                            onSaveTrigger(triggerToSave)
                        }
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Validation error alert
            validationError?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // SECTION 1: Target Task & Trigger Title
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Trigger Target & Label",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Target Task Dropdown
                    ExposedDropdownMenuBox(
                        expanded = taskDropdownExpanded,
                        onExpandedChange = { taskDropdownExpanded = !taskDropdownExpanded }
                    ) {
                        val currentTaskName = allTasks.find { it.id == selectedTaskId }?.title ?: "Select Target Task"
                        OutlinedTextField(
                            value = currentTaskName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target Sync Task") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskDropdownExpanded) },
                            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = taskDropdownExpanded,
                            onDismissRequest = { taskDropdownExpanded = false }
                        ) {
                            allTasks.forEach { task ->
                                DropdownMenuItem(
                                    text = { Text(task.title, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedTaskId = task.id
                                        taskDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Trigger Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Schedule Name (Optional)") },
                        placeholder = { Text("e.g., Every 30 Minutes Backup") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
                    )

                    // Enable Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Active / Enabled", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Run this trigger automatically in background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                    }
                }
            }

            // SECTION 2: Trigger Mode (Interval vs Scheduled Time)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Trigger Schedule Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Segmented Button Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { triggerType = Trigger.TRIGGER_TYPE_INTERVAL },
                            color = if (triggerType == Trigger.TRIGGER_TYPE_INTERVAL) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Update,
                                    contentDescription = null,
                                    tint = if (triggerType == Trigger.TRIGGER_TYPE_INTERVAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Periodic Interval",
                                    fontWeight = FontWeight.Bold,
                                    color = if (triggerType == Trigger.TRIGGER_TYPE_INTERVAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { triggerType = Trigger.TRIGGER_TYPE_SCHEDULE },
                            color = if (triggerType == Trigger.TRIGGER_TYPE_SCHEDULE) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Alarm,
                                    contentDescription = null,
                                    tint = if (triggerType == Trigger.TRIGGER_TYPE_SCHEDULE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Time of Day",
                                    fontWeight = FontWeight.Bold,
                                    color = if (triggerType == Trigger.TRIGGER_TYPE_SCHEDULE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // INTERVAL MODE DETAILS
                    if (triggerType == Trigger.TRIGGER_TYPE_INTERVAL) {
                        Text(
                            text = "Select Interval Frequency:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        val intervalOptions = listOf(
                            15 to "15 min",
                            30 to "30 min",
                            60 to "1 hour",
                            120 to "2 hours",
                            360 to "6 hours",
                            720 to "12 hours",
                            1440 to "24 hours"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                intervalOptions.take(4).forEach { (minutes, label) ->
                                    FilterChip(
                                        selected = intervalMinutes == minutes,
                                        onClick = { intervalMinutes = minutes },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                intervalOptions.drop(4).forEach { (minutes, label) ->
                                    FilterChip(
                                        selected = intervalMinutes == minutes,
                                        onClick = { intervalMinutes = minutes },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        // SCHEDULE TIME OF DAY MODE DETAILS
                        Text(
                            text = "Scheduled Time:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showTimePickerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = String.format("%02d:%02d", scheduleHour, scheduleMinute),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Change Time",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Active Days of Week:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Weekday Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf(
                                Triple("M", weekdayMon) { weekdayMon = !weekdayMon },
                                Triple("T", weekdayTue) { weekdayTue = !weekdayTue },
                                Triple("W", weekdayWed) { weekdayWed = !weekdayWed },
                                Triple("T", weekdayThu) { weekdayThu = !weekdayThu },
                                Triple("F", weekdayFri) { weekdayFri = !weekdayFri },
                                Triple("S", weekdaySat) { weekdaySat = !weekdaySat },
                                Triple("S", weekdaySun) { weekdaySun = !weekdaySun }
                            )

                            days.forEach { (label, isSelected, onToggle) ->
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onToggle)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Delete Trigger Button (only when editing)
            if (existingTrigger != null && onDeleteTrigger != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Schedule Trigger", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Time Picker Dialog
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduleHour,
            initialMinute = scheduleMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = { Text("Set Schedule Time") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scheduleHour = timePickerState.hour
                        scheduleMinute = timePickerState.minute
                        showTimePickerDialog = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && existingTrigger != null && onDeleteTrigger != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Trigger") },
            text = { Text("Are you sure you want to delete this schedule?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteTrigger(existingTrigger)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
