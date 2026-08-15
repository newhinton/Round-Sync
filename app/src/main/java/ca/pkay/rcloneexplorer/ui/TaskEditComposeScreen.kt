package ca.pkay.rcloneexplorer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.pkay.rcloneexplorer.Items.Filter
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject
import ca.pkay.rcloneexplorer.Items.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditComposeScreen(
    existingTask: Task?,
    remotes: List<RemoteItem>,
    filters: List<Filter>,
    allTasks: List<Task>,
    onSaveTask: (Task) -> Unit,
    onDeleteTask: ((Task) -> Unit)?,
    onPickLocalPath: () -> Unit,
    onPickRemotePath: (RemoteItem, String) -> Unit,
    onCreateFilter: () -> Unit,
    onBack: () -> Unit,
    localPathOverride: String? = null,
    remotePathOverride: String? = null
) {
    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var selectedRemoteName by remember {
        mutableStateOf(existingTask?.remoteId ?: remotes.firstOrNull()?.name ?: "")
    }
    var remotePath by remember { mutableStateOf(existingTask?.remotePath ?: "") }
    var localPath by remember { mutableStateOf(existingTask?.localPath ?: "") }
    var direction by remember {
        mutableStateOf(existingTask?.direction ?: SyncDirectionObject.SYNC_LOCAL_TO_REMOTE)
    }
    var md5sum by remember { mutableStateOf(existingTask?.md5sum ?: false) }
    var deleteExcluded by remember { mutableStateOf(existingTask?.deleteExcluded ?: false) }
    var selectedFilterId by remember { mutableStateOf(existingTask?.filterId) }
    var onSuccessFollowup by remember { mutableStateOf(existingTask?.onSuccessFollowup ?: -1L) }
    var onFailFollowup by remember { mutableStateOf(existingTask?.onFailFollowup ?: -1L) }

    var remoteDropdownExpanded by remember { mutableStateOf(false) }
    var directionDropdownExpanded by remember { mutableStateOf(false) }
    var filterDropdownExpanded by remember { mutableStateOf(false) }
    var onSuccessDropdownExpanded by remember { mutableStateOf(false) }
    var onFailDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // React to path pickers returning values
    LaunchedEffect(localPathOverride) {
        if (!localPathOverride.isNullOrBlank()) {
            localPath = localPathOverride
        }
    }
    LaunchedEffect(remotePathOverride) {
        if (!remotePathOverride.isNullOrBlank()) {
            remotePath = remotePathOverride
        }
    }

    val selectedRemote = remember(selectedRemoteName, remotes) {
        remotes.find { it.name == selectedRemoteName } ?: remotes.firstOrNull()
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingTask == null) "Create Sync Task" else "Edit Sync Task",
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
                            if (localPath.isBlank()) {
                                validationError = "Please choose a local storage folder."
                                return@TextButton
                            }
                            if (remotePath.isBlank()) {
                                validationError = "Please choose a remote cloud folder."
                                return@TextButton
                            }
                            if (selectedRemote == null) {
                                validationError = "Please select a cloud remote."
                                return@TextButton
                            }

                            val taskToSave = Task(existingTask?.id ?: 0L).apply {
                                this.title = title.ifBlank { "${selectedRemote.name}: $remotePath" }
                                this.remoteId = selectedRemote.name
                                this.remoteType = selectedRemote.type
                                this.remotePath = remotePath
                                this.localPath = localPath
                                this.direction = direction
                                this.md5sum = md5sum
                                this.deleteExcluded = deleteExcluded
                                this.filterId = selectedFilterId
                                this.onSuccessFollowup = onSuccessFollowup
                                this.onFailFollowup = onFailFollowup
                            }
                            onSaveTask(taskToSave)
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
            // Validation Error Alert
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

            // SECTION 1: Task Identity & Paths
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
                        text = "Task Details & Locations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Name / Label (Optional)") },
                        placeholder = { Text("e.g., Daily Camera Backup") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
                    )

                    // Remote Selector Dropdown
                    ExposedDropdownMenuBox(
                        expanded = remoteDropdownExpanded,
                        onExpandedChange = { remoteDropdownExpanded = !remoteDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRemote?.name ?: "No Remote Selected",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cloud Remote") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = remoteDropdownExpanded) },
                            leadingIcon = {
                                selectedRemote?.let {
                                    Icon(
                                        painter = painterResource(id = it.remoteIcon),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } ?: Icon(Icons.Default.Cloud, contentDescription = null)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = remoteDropdownExpanded,
                            onDismissRequest = { remoteDropdownExpanded = false }
                        ) {
                            remotes.forEach { remote ->
                                DropdownMenuItem(
                                    text = { Text(remote.name, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = remote.remoteIcon),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    onClick = {
                                        selectedRemoteName = remote.name
                                        remoteDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Remote Path Picker
                    OutlinedTextField(
                        value = remotePath,
                        onValueChange = { remotePath = it },
                        label = { Text("Remote Cloud Directory") },
                        placeholder = { Text("/photos/backup") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    selectedRemote?.let {
                                        onPickRemotePath(it, remotePath.ifBlank { "/" })
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Browse Cloud Folders", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )

                    // Local Path Picker
                    OutlinedTextField(
                        value = localPath,
                        onValueChange = { localPath = it },
                        label = { Text("Local Storage Directory") },
                        placeholder = { Text("/storage/emulated/0/DCIM") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = onPickLocalPath) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Browse Local Storage", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }

            // SECTION 2: Transfer Direction
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Direction & Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val directionOptions = listOf(
                        SyncDirectionObject.SYNC_LOCAL_TO_REMOTE to "Sync: Local → Cloud (Upload changes)",
                        SyncDirectionObject.SYNC_REMOTE_TO_LOCAL to "Sync: Cloud → Local (Download changes)",
                        SyncDirectionObject.SYNC_BIDIRECTIONAL to "Two-Way BiSync (Keep both sides identical)",
                        SyncDirectionObject.COPY_LOCAL_TO_REMOTE to "Copy: Local → Cloud (Upload new files only)",
                        SyncDirectionObject.COPY_REMOTE_TO_LOCAL to "Copy: Cloud → Local (Download new files only)",
                        SyncDirectionObject.MOVE_LOCAL_TO_REMOTE to "Move: Local → Cloud (Upload and delete local)",
                        SyncDirectionObject.MOVE_REMOTE_TO_LOCAL to "Move: Cloud → Local (Download and delete remote)"
                    )

                    ExposedDropdownMenuBox(
                        expanded = directionDropdownExpanded,
                        onExpandedChange = { directionDropdownExpanded = !directionDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = directionOptions.find { it.first == direction }?.second ?: "Sync",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Transfer Action") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = directionDropdownExpanded) },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = directionDropdownExpanded,
                            onDismissRequest = { directionDropdownExpanded = false }
                        ) {
                            directionOptions.forEach { (dirValue, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        direction = dirValue
                                        directionDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: Advanced Options & Filters
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
                        text = "Advanced Transfer Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // MD5 Checksum Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verify MD5 Checksums", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Compare file contents rather than timestamps and sizes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = md5sum,
                            onCheckedChange = { md5sum = it }
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.06f))

                    // Delete Excluded Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delete Excluded Files", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Delete files on destination that are excluded by filter rules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = deleteExcluded,
                            onCheckedChange = { deleteExcluded = it }
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.06f))

                    // Filter Profile Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = filterDropdownExpanded,
                            onExpandedChange = { filterDropdownExpanded = !filterDropdownExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val currentFilterName = filters.find { it.id == selectedFilterId }?.title ?: "No Filter (Transfer All)"
                            OutlinedTextField(
                                value = currentFilterName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Filter Rules") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterDropdownExpanded) },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = filterDropdownExpanded,
                                onDismissRequest = { filterDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("No Filter (Transfer All)") },
                                    onClick = {
                                        selectedFilterId = null
                                        filterDropdownExpanded = false
                                    }
                                )
                                filters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.title) },
                                        onClick = {
                                            selectedFilterId = filter.id
                                            filterDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onCreateFilter,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create new filter", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Followup Tasks (Success / Failure)
                    val taskOptions = remember(allTasks, existingTask) {
                        val list = mutableListOf<Pair<Long, String>>()
                        list.add(-1L to "None")
                        allTasks.filter { it.id != (existingTask?.id ?: 0L) }.forEach {
                            list.add(it.id to it.title)
                        }
                        list
                    }

                    // On Success Follow-up
                    ExposedDropdownMenuBox(
                        expanded = onSuccessDropdownExpanded,
                        onExpandedChange = { onSuccessDropdownExpanded = !onSuccessDropdownExpanded }
                    ) {
                        val currentSuccess = taskOptions.find { it.first == onSuccessFollowup }?.second ?: "None"
                        OutlinedTextField(
                            value = currentSuccess,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("On Success Follow-up Task") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = onSuccessDropdownExpanded) },
                            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = onSuccessDropdownExpanded,
                            onDismissRequest = { onSuccessDropdownExpanded = false }
                        ) {
                            taskOptions.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onSuccessFollowup = id
                                        onSuccessDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // On Failure Follow-up
                    ExposedDropdownMenuBox(
                        expanded = onFailDropdownExpanded,
                        onExpandedChange = { onFailDropdownExpanded = !onFailDropdownExpanded }
                    ) {
                        val currentFail = taskOptions.find { it.first == onFailFollowup }?.second ?: "None"
                        OutlinedTextField(
                            value = currentFail,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("On Failure Follow-up Task") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = onFailDropdownExpanded) },
                            leadingIcon = { Icon(Icons.Default.HighlightOff, contentDescription = null) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = onFailDropdownExpanded,
                            onDismissRequest = { onFailDropdownExpanded = false }
                        ) {
                            taskOptions.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onFailFollowup = id
                                        onFailDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 4: Delete Task Button (only when editing)
            if (existingTask != null && onDeleteTask != null) {
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
                    Text("Delete Sync Task", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && existingTask != null && onDeleteTask != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to delete \"${existingTask.title}\"? Associated triggers will also be cancelled and deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteTask(existingTask)
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
