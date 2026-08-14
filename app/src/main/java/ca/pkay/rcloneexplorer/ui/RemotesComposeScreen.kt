package ca.pkay.rcloneexplorer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.ui.components.BrandLoader
import ca.pkay.rcloneexplorer.ui.viewmodel.RemotesViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotesComposeScreen(
    viewModel: RemotesViewModel,
    onRemoteClick: (RemoteItem) -> Unit,
    onAddNewRemote: () -> Unit,
    onEditRemoteConfig: (RemoteItem) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) {
            pullRefreshState.endRefresh()
        }
    }

    var selectedRemoteForProperties by remember { mutableStateOf<RemoteItem?>(null) }
    var remoteToDelete by remember { mutableStateOf<RemoteItem?>(null) }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfoMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar / Search
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (uiState.isSearching) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                placeholder = { Text("Search remotes...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.toggleSearch() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search")
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Remote Manager",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${uiState.remotes.size} Cloud Connection${if (uiState.remotes.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.toggleSearch() }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search remotes")
                                    }
                                    IconButton(onClick = { viewModel.loadRemotes() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh remotes")
                                    }
                                }
                            }
                        }
                    }
                }

                // Pull-to-Refresh & Remotes List
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    if (uiState.isLoading && uiState.displayRemotes.isEmpty()) {
                        BrandLoader(message = "Loading your cloud remotes...")
                    } else if (uiState.displayRemotes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudQueue,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(20.dp)
                                            .fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = if (uiState.isSearching) "No matching remotes found" else "No Cloud Remotes Configured",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (uiState.isSearching) "Try a different search term" else "Connect your Google Drive, Dropbox, SFTP, OneDrive, S3, or WebDAV storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!uiState.isSearching) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = onAddNewRemote,
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Remote")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 300.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(uiState.displayRemotes, key = { it.name }) { remote ->
                                RemoteCard(
                                    remote = remote,
                                    quota = uiState.storageQuotas[remote.name],
                                    isLoadingQuota = uiState.loadingQuotas.contains(remote.name),
                                    onClick = { onRemoteClick(remote) },
                                    onFetchQuota = { viewModel.fetchStorageQuota(remote) },
                                    onPropertiesClick = { selectedRemoteForProperties = remote },
                                    onTogglePin = { viewModel.togglePinRemote(remote) },
                                    onDeleteClick = { remoteToDelete = remote },
                                    onEditClick = { onEditRemoteConfig(remote) },
                                    onReconnectClick = { viewModel.reconnectRemote(remote, context) }
                                )
                            }
                        }
                    }

                    if (pullRefreshState.isRefreshing || pullRefreshState.verticalOffset > 0.5f) {
                        PullToRefreshContainer(
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Dedicated Single Floating Action Button for Adding Remotes
            FloatingActionButton(
                onClick = onAddNewRemote,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Remote"
                )
            }
        }
    }

    // Remote Properties BottomSheet
    selectedRemoteForProperties?.let { remote ->
        RemotePropertiesSheet(
            remote = remote,
            quotaResult = uiState.storageQuotas[remote.name],
            isLoadingQuota = uiState.loadingQuotas.contains(remote.name),
            onFetchQuota = { viewModel.fetchStorageQuota(remote) },
            onEditConfig = {
                selectedRemoteForProperties = null
                onEditRemoteConfig(remote)
            },
            onReconnect = {
                selectedRemoteForProperties = null
                viewModel.reconnectRemote(remote, context)
            },
            onDismiss = { selectedRemoteForProperties = null }
        )
    }

    // Delete Confirmation Dialog
    remoteToDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { remoteToDelete = null },
            title = { Text("Delete Remote") },
            text = { Text("Are you sure you want to remove \"${remote.displayName}\" from your configured remotes?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRemote(remote)
                        remoteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { remoteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RemoteCard(
    remote: RemoteItem,
    quota: Rclone.AboutResult?,
    isLoadingQuota: Boolean,
    onClick: () -> Unit,
    onFetchQuota: () -> Unit,
    onPropertiesClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onReconnectClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = if (remote.isPinned) 2.dp else 1.dp,
            color = if (remote.isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = remote.remoteIcon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = remote.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = remote.typeReadable?.uppercase(Locale.ROOT) ?: "REMOTE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Properties & Quota") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPropertiesClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (remote.isPinned) "Unpin" else "Pin to Top") },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Configuration") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            }
                        )
                        if (remote.isOAuth) {
                            DropdownMenuItem(
                                text = { Text("Reconnect OAuth") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onReconnectClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            // Badges Row (Crypt, Alias, Pinned)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (remote.isPinned) {
                    BadgeChip(label = "PINNED", containerColor = MaterialTheme.colorScheme.primaryContainer)
                }
                if (remote.isCrypt) {
                    BadgeChip(label = "ENCRYPTED", containerColor = MaterialTheme.colorScheme.secondaryContainer)
                }
                if (remote.isAlias || remote.isPathAlias) {
                    BadgeChip(label = "ALIAS", containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Meter or View Storage Action
            if (quota != null && !quota.hasFailed()) {
                val used = quota.used
                val total = quota.total
                if (total > 0) {
                    val fraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    Column {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatSizeShort(used)} / ${formatSizeShort(total)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (used >= 0) {
                    Text(
                        text = "${formatSizeShort(used)} used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Storage usage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLoadingQuota) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "View Storage",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onFetchQuota() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeChip(label: String, containerColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatSizeShort(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1000.0)).toInt()
    val pre = "kMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1000.0, exp.toDouble()), pre)
}
