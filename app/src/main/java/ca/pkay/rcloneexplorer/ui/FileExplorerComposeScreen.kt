package ca.pkay.rcloneexplorer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.data.*
import ca.pkay.rcloneexplorer.ui.components.*
import ca.pkay.rcloneexplorer.ui.viewmodel.FileExplorerUiState
import ca.pkay.rcloneexplorer.ui.viewmodel.FileExplorerViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.x0b.safdav.SafAccessProvider

data class BreadcrumbItem(
    val title: String,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerComposeScreen(
    viewModel: FileExplorerViewModel,
    onFileClicked: (FileItem) -> Unit,
    onOpenAsClicked: (FileItem) -> Unit = {},
    onRenameClicked: (FileItem, String) -> Unit = { _, _ -> },
    onFilePropertiesClicked: (FileItem) -> Unit,
    onFileLinkShareClicked: (FileItem) -> Unit,
    onUploadFiles: () -> Unit,
    onDownloadSelected: (List<FileItem>) -> Unit,
    onMoveSelected: (List<FileItem>) -> Unit,
    onSortClicked: () -> Unit,
    onOpenServeDialog: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var itemForOptionSheet by remember { mutableStateOf<FileItem?>(null) }
    var fileToRename by remember { mutableStateOf<FileItem?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    var showOverflowMenu by remember { mutableStateOf(false) }

    val isInSelectMode = uiState.selectedItems.isNotEmpty()

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
                // Glassmorphic Top Bar & Breadcrumbs
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (uiState.isSearching) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp),
                                    placeholder = { Text("Search files & folders...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                    trailingIcon = {
                                        IconButton(onClick = { viewModel.toggleSearch() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Transparent
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // File Type Filter Chips
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(FileTypeFilter.values()) { filter ->
                                        FilterChip(
                                            selected = uiState.typeFilter == filter,
                                            onClick = { viewModel.onTypeFilterChanged(filter) },
                                            label = { Text(filter.displayName) },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Top Row: Title + Search + 3-Dot Overflow Menu
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (uiState.breadcrumbs.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.navigateUp() },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    Column {
                                        Text(
                                            text = uiState.remote?.displayName ?: "Remote Explorer",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (uiState.currentPath.isEmpty()) "Root Directory" else uiState.breadcrumbs.lastOrNull()?.title ?: uiState.currentPath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.toggleSearch() }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Box {
                                        IconButton(onClick = { showOverflowMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                                        }

                                        DropdownMenu(
                                            expanded = showOverflowMenu,
                                            onDismissRequest = { showOverflowMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if (uiState.isGridView) "Switch to List View" else "Switch to Grid View") },
                                                leadingIcon = {
                                                    Icon(
                                                        if (uiState.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.toggleViewMode()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Sort Files...") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    onSortClicked()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Quick Bookmarks") },
                                                leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.openBookmarks()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Bookmark Current Folder") },
                                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.bookmarkCurrentFolder()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Deduplicator Tool") },
                                                leadingIcon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.openDedupeSheet()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Serve HTTP / WebDAV...") },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    onOpenServeDialog()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (uiState.showHiddenFiles) "Hide hidden files" else "Show hidden files") },
                                                leadingIcon = {
                                                    Icon(
                                                        if (uiState.showHiddenFiles) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.toggleShowHiddenFiles()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Refresh") },
                                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.refresh()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Dedicated Accessible Full-Width Breadcrumbs Bar
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(uiState.breadcrumbs) { crumb ->
                                    val isCurrent = crumb.path == uiState.currentPath
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.navigateToBreadcrumb(crumb)
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            if (crumb == uiState.breadcrumbs.firstOrNull()) {
                                                Icon(
                                                    Icons.Default.Cloud,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = crumb.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    if (crumb != uiState.breadcrumbs.lastOrNull()) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Background Refresh Progress Bar
                    AnimatedVisibility(
                        visible = uiState.isRefreshing,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }

                // Clipboard Paste Banner
                AnimatedVisibility(
                    visible = clipboard.isNotEmpty && !isInSelectMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (clipboard.operation == ClipboardOp.COPY) Icons.Default.ContentCopy else Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${clipboard.count} item(s) ready to paste",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { viewModel.pasteClipboard() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Paste Here", fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { FileClipboardManager.clear() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear clipboard",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Pull to Refresh Container & File Content List / Grid
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    if (uiState.isLoading && uiState.displayFiles.isEmpty()) {
                        BrandLoader(message = "Loading folder contents...")
                    } else if (uiState.displayFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isSearching) Icons.Outlined.SearchOff else Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(20.dp)
                                            .fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (uiState.isSearching) "No files matching \"${uiState.searchQuery}\"" else "This folder is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (uiState.isSearching) "Try adjusting search or filters" else "Tap + to upload files or create a directory",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = if (uiState.isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 10.dp,
                                bottom = if (isInSelectMode) 130.dp else 90.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.displayFiles, key = { "${it.path}:${it.name}" }) { fileItem ->
                                val isSelected = uiState.selectedItems.contains(fileItem)
                                if (uiState.isGridView) {
                                    GridFileCard(
                                        fileItem = fileItem,
                                        isSelected = isSelected,
                                        showThumbnails = uiState.showThumbnails,
                                        thumbnailServerAuth = uiState.thumbnailServerAuth,
                                        thumbnailServerPort = uiState.thumbnailServerPort,
                                        onClick = {
                                            if (isInSelectMode) {
                                                viewModel.toggleSelection(fileItem)
                                            } else if (fileItem.isDir) {
                                                viewModel.navigateInto(fileItem)
                                            } else {
                                                onFileClicked(fileItem)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(fileItem) },
                                        onOptionsClick = { itemForOptionSheet = fileItem }
                                    )
                                } else {
                                    ListFileCard(
                                        fileItem = fileItem,
                                        isSelected = isSelected,
                                        showThumbnails = uiState.showThumbnails,
                                        thumbnailServerAuth = uiState.thumbnailServerAuth,
                                        thumbnailServerPort = uiState.thumbnailServerPort,
                                        onClick = {
                                            if (isInSelectMode) {
                                                viewModel.toggleSelection(fileItem)
                                            } else if (fileItem.isDir) {
                                                viewModel.navigateInto(fileItem)
                                            } else {
                                                onFileClicked(fileItem)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(fileItem) },
                                        onOptionsClick = { itemForOptionSheet = fileItem }
                                    )
                                }
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

            // Floating Transfer Activity Pill
            TransferPill(
                activeTransfers = uiState.activeTransfers,
                onCancelTransfer = { /* Cancel transfer */ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isInSelectMode || uiState.moveModeItems.isNotEmpty()) 96.dp else 84.dp)
            )

            // Move Mode Floating Bottom Bar
            if (uiState.moveModeItems.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Moving ${uiState.moveModeItems.size} item(s)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Navigate to destination folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.cancelMoveMode() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { viewModel.executeMoveHere() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Move Here")
                            }
                        }
                    }
                }
            }

            // FAB for Adding (Upload & New Folder)
            if (!isInSelectMode && uiState.moveModeItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showFabMenu) {
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                viewModel.openCreateFolderDialog()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                onUploadFiles()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Upload")
                        }
                    }

                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Add Actions"
                        )
                    }
                }
            }

            // Glassmorphic Bottom Selection Action Bar
            AnimatedVisibility(
                visible = isInSelectMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.25f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                    shadowElevation = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selected Count & Select All
                            Column {
                                Text(
                                    text = "${uiState.selectedItems.size} Selected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (uiState.selectedItems.size == uiState.displayFiles.size) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.clickable {
                                        if (uiState.selectedItems.size == uiState.displayFiles.size) viewModel.deselectAll() else viewModel.selectAll()
                                    }
                                )
                            }

                            // Actions Row (Copy, Cut, Duplicate, Batch Rename, Download, Move, Delete)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.copySelected() }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { viewModel.cutSelected() }) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { viewModel.duplicateSelected() }) {
                                    Icon(Icons.Default.CopyAll, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { viewModel.openBatchRename() }) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Batch Rename", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { onDownloadSelected(uiState.selectedItems.toList()) }) {
                                    Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { onMoveSelected(uiState.selectedItems.toList()) }) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { viewModel.deleteSelected() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = { viewModel.deselectAll() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Modals & Sheets ---

    // Batch Rename Dialog
    if (uiState.isBatchRenameOpen) {
        BatchRenameDialog(
            selectedItems = uiState.selectedItems.toList(),
            onDismiss = { viewModel.closeBatchRename() },
            onConfirm = { rule -> viewModel.executeBatchRename(rule) }
        )
    }

    // Dedupe Sheet
    if (uiState.isDedupeSheetOpen) {
        DedupeComposeSheet(
            isScanning = uiState.isScanningDuplicates,
            duplicateGroups = uiState.duplicateGroups,
            onDismiss = { viewModel.closeDedupeSheet() },
            onDeleteDuplicates = { files -> viewModel.deleteDuplicates(files) }
        )
    }

    // Bookmarks Sheet
    if (uiState.isBookmarksOpen) {
        BookmarksSheet(
            bookmarks = uiState.bookmarks,
            currentRemoteName = uiState.remote?.name ?: "",
            currentPath = uiState.currentPath,
            onDismiss = { viewModel.closeBookmarks() },
            onBookmarkClick = { bookmark ->
                viewModel.closeBookmarks()
                viewModel.navigateToBreadcrumb(BreadcrumbItem(title = bookmark.label, path = bookmark.path))
            },
            onRemoveBookmark = { bookmark -> viewModel.removeBookmark(bookmark) },
            onAddCurrentAsBookmark = { viewModel.bookmarkCurrentFolder() }
        )
    }

    // Create New Folder Dialog
    if (uiState.isCreateFolderDialogOpen) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.closeCreateFolderDialog() },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName.trim())
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeCreateFolderDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single File Item Options Sheet
    itemForOptionSheet?.let { file ->
        ModalBottomSheet(
            onDismissRequest = { itemForOptionSheet = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (file.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (file.isDir) "Directory" else file.humanReadableSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Options list
                if (!file.isDir) {
                    ListItem(
                        headlineContent = { Text("Open with...") },
                        leadingContent = { Icon(Icons.Default.OpenWith, contentDescription = null) },
                        modifier = Modifier.clickable {
                            itemForOptionSheet = null
                            onOpenAsClicked(file)
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Rename") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val target = file
                        itemForOptionSheet = null
                        fileToRename = target
                    }
                )
                ListItem(
                    headlineContent = { Text("Properties & Hash") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        itemForOptionSheet = null
                        onFilePropertiesClicked(file)
                    }
                )
                ListItem(
                    headlineContent = { Text("Copy to Clipboard") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable {
                        itemForOptionSheet = null
                        viewModel.toggleSelection(file)
                        viewModel.copySelected()
                    }
                )
                ListItem(
                    headlineContent = { Text("Duplicate") },
                    leadingContent = { Icon(Icons.Default.CopyAll, contentDescription = null) },
                    modifier = Modifier.clickable {
                        itemForOptionSheet = null
                        viewModel.toggleSelection(file)
                        viewModel.duplicateSelected()
                    }
                )
                ListItem(
                    headlineContent = { Text("Share Link") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        itemForOptionSheet = null
                        onFileLinkShareClicked(file)
                    }
                )
                ListItem(
                    headlineContent = { Text("Download") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable {
                        itemForOptionSheet = null
                        onDownloadSelected(listOf(file))
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        itemForOptionSheet = null
                        viewModel.toggleSelection(file)
                        viewModel.deleteSelected()
                    }
                )
            }
        }
    }

    // Rename Single File / Folder Dialog
    fileToRename?.let { targetItem ->
        var newName by remember { mutableStateOf(targetItem.name) }
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newName != targetItem.name) {
                            onRenameClicked(targetItem, newName.trim())
                        }
                        fileToRename = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridFileCard(
    fileItem: FileItem,
    isSelected: Boolean,
    showThumbnails: Boolean,
    thumbnailServerAuth: String,
    thumbnailServerPort: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val context = LocalContext.current
    val mimeType = fileItem.mimeType
    val isPhoto = mimeType != null && mimeType.startsWith("image/")

    val isLocal = fileItem.remote.isRemoteType(RemoteItem.LOCAL) || fileItem.remote.isPathAlias
    val isSaf = fileItem.remote.isRemoteType(RemoteItem.SAFW)

    val imageModel: Any? = remember(fileItem, thumbnailServerAuth, thumbnailServerPort) {
        if (!showThumbnails || !isPhoto) {
            null
        } else if (isLocal) {
            java.io.File(fileItem.path)
        } else if (isSaf) {
            try {
                SafAccessProvider.getDirectServer(context).getDocumentUri('/' + fileItem.path)
            } catch (e: Exception) {
                if (thumbnailServerPort > 0 && thumbnailServerAuth.isNotEmpty()) {
                    "http://127.0.0.1:$thumbnailServerPort/$thumbnailServerAuth/${fileItem.remote.name}/${fileItem.path}"
                } else null
            }
        } else if (thumbnailServerPort > 0 && thumbnailServerAuth.isNotEmpty()) {
            "http://127.0.0.1:$thumbnailServerPort/$thumbnailServerAuth/${fileItem.remote.name}/${fileItem.path}"
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val visual = FileIconHelper.getVisualForFile(fileItem)

            // Icon or Photo Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (imageModel != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else visual.backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    val cacheSignature = "${fileItem.remote.name}:${fileItem.path}:${fileItem.modTime}:${fileItem.size}"
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .memoryCacheKey(cacheSignature)
                            .diskCacheKey(cacheSignature)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .size(180, 180)
                            .build(),
                        contentDescription = fileItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = visual.categoryLabel,
                        tint = visual.iconColor,
                        modifier = Modifier.size(46.dp)
                    )
                }

                // Selected Checkmark Badge
                if (isSelected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File Name
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Size and Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (fileItem.isDir) "Folder" else fileItem.humanReadableSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListFileCard(
    fileItem: FileItem,
    isSelected: Boolean,
    showThumbnails: Boolean,
    thumbnailServerAuth: String,
    thumbnailServerPort: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val context = LocalContext.current
    val mimeType = fileItem.mimeType
    val isPhoto = mimeType != null && mimeType.startsWith("image/")
    val isLocal = fileItem.remote.isRemoteType(RemoteItem.LOCAL) || fileItem.remote.isPathAlias
    val isSaf = fileItem.remote.isRemoteType(RemoteItem.SAFW)

    val imageModel: Any? = remember(fileItem, thumbnailServerAuth, thumbnailServerPort) {
        if (!showThumbnails || !isPhoto) {
            null
        } else if (isLocal) {
            java.io.File(fileItem.path)
        } else if (isSaf) {
            try {
                SafAccessProvider.getDirectServer(context).getDocumentUri('/' + fileItem.path)
            } catch (e: Exception) {
                if (thumbnailServerPort > 0 && thumbnailServerAuth.isNotEmpty()) {
                    "http://127.0.0.1:$thumbnailServerPort/$thumbnailServerAuth/${fileItem.remote.name}/${fileItem.path}"
                } else null
            }
        } else if (thumbnailServerPort > 0 && thumbnailServerAuth.isNotEmpty()) {
            "http://127.0.0.1:$thumbnailServerPort/$thumbnailServerAuth/${fileItem.remote.name}/${fileItem.path}"
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val visual = FileIconHelper.getVisualForFile(fileItem)

            // Icon or Photo Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (imageModel != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else visual.backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    val cacheSignature = "${fileItem.remote.name}:${fileItem.path}:${fileItem.modTime}:${fileItem.size}"
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .memoryCacheKey(cacheSignature)
                            .diskCacheKey(cacheSignature)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .size(180, 180)
                            .build(),
                        contentDescription = fileItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = visual.categoryLabel,
                        tint = visual.iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isSelected) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Name & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!fileItem.isDir) {
                        Text(
                            text = fileItem.humanReadableSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (fileItem.isDir) "Directory" else (fileItem.humanReadableModTime ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onOptionsClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
