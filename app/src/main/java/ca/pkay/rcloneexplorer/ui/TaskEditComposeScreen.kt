package ca.pkay.rcloneexplorer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.Filter
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Items.SyncDirectionObject
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showRemoteFolderPickerDialog by remember { mutableStateOf(false) }
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
                                    if (selectedRemote != null) {
                                        showRemoteFolderPickerDialog = true
                                    } else {
                                        validationError = "Please select a cloud remote first."
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

    // Remote Cloud Folder Picker Dialog
    if (showRemoteFolderPickerDialog && selectedRemote != null) {
        RemoteFolderPickerComposeDialog(
            remote = selectedRemote,
            initialPath = remotePath,
            onDismiss = { showRemoteFolderPickerDialog = false },
            onFolderSelected = { selectedPath ->
                remotePath = selectedPath
                validationError = null
            }
        )
    }
}

@Composable
fun RemoteFolderPickerComposeDialog(
    remote: RemoteItem,
    initialPath: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val rclone = remember { Rclone(context) }
    var currentPath by remember {
        mutableStateOf(
            if (initialPath.isBlank() || initialPath == "/") "//${remote.name}"
            else if (!initialPath.startsWith("//")) "//${remote.name}/${initialPath.trimStart('/')}"
            else initialPath
        )
    }
    var folders by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun loadFolders(path: String) {
        isLoading = true
        errorMessage = null
        val rclonePath = if (path == "//${remote.name}") "" else path.removePrefix("//${remote.name}/").removePrefix("//${remote.name}")
        coroutineScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    rclone.getDirectoryContent(remote, rclonePath, false)
                } catch (e: Exception) {
                    FLog.e("RemoteFolderPicker", "Error fetching folders", e)
                    null
                }
            }
            if (items != null) {
                folders = items.filter { it.isDir }.sortedBy { it.name.lowercase() }
            } else {
                errorMessage = "Failed to load cloud directory contents"
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentPath) {
        loadFolders(currentPath)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = remote.remoteIcon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Select Cloud Folder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = remote.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Current Path Indicator & Navigation
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isRoot = currentPath == "//${remote.name}" || currentPath.isEmpty()
                        IconButton(
                            onClick = {
                                if (!isRoot) {
                                    val parent = currentPath.substringBeforeLast('/')
                                    currentPath = if (parent.isEmpty() || parent == "//${remote.name}") "//${remote.name}" else parent
                                }
                            },
                            enabled = !isRoot,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Up",
                                tint = if (!isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRoot) "/ (Root)" else "/" + currentPath.removePrefix("//${remote.name}/").removePrefix("//${remote.name}"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Folder List Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading folders...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { loadFolders(currentPath) }) {
                                Text("Retry")
                            }
                        }
                    } else if (folders.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No subdirectories found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("You can select this folder or create a new one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(folders) { folder ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val root = "//${remote.name}"
                                            val folderRel = folder.path.removePrefix(root).trimStart('/')
                                            currentPath = if (folderRel.isEmpty()) root else "$root/$folderRel"
                                        },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val clean = currentPath.removePrefix("//${remote.name}/").removePrefix("//${remote.name}")
                            val chosenPath = if (clean.isBlank()) "/" else if (clean.startsWith("/")) clean else "/$clean"
                            onFolderSelected(chosenPath)
                            onDismiss()
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select This Folder")
                    }
                }
            }
        }
    }

    // Create New Folder Sub-Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                newFolderName = ""
            },
            title = { Text("New Cloud Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Backups") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isNotEmpty()) {
                            coroutineScope.launch {
                                val currentClean = currentPath.removePrefix("//${remote.name}/").removePrefix("//${remote.name}").trim('/')
                                val targetDir = if (currentClean.isEmpty()) name else "$currentClean/$name"
                                withContext(Dispatchers.IO) {
                                    try {
                                        rclone.makeDirectory(remote, targetDir)
                                    } catch (e: Exception) {
                                        FLog.e("RemoteFolderPicker", "Error creating folder", e)
                                    }
                                }
                                showCreateFolderDialog = false
                                newFolderName = ""
                                loadFolders(currentPath)
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    newFolderName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
