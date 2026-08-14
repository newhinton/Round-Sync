package ca.pkay.rcloneexplorer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.R
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class BreadcrumbItem(
    val title: String,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerComposeScreen(
    remoteName: String,
    currentPath: String,
    breadcrumbs: List<BreadcrumbItem>,
    files: List<FileItem>,
    selectedItems: Set<FileItem>,
    isGridView: Boolean,
    isSearching: Boolean,
    searchQuery: String,
    isLoading: Boolean,
    showThumbnails: Boolean,
    thumbnailServerAuth: String,
    thumbnailServerPort: Int,
    onToggleViewMode: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onBreadcrumbClicked: (String) -> Unit,
    onFileClicked: (FileItem) -> Unit,
    onDirectoryClicked: (FileItem) -> Unit,
    onItemLongClicked: (FileItem) -> Unit,
    onItemOptionsClicked: (FileItem) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCreateNewFolder: () -> Unit,
    onUploadFiles: () -> Unit,
    onRefresh: () -> Unit,
    onSortClicked: () -> Unit
) {
    val isInSelectMode = selectedItems.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Glassmorphic Top Bar & Breadcrumbs
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    // Search Bar or Breadcrumb Row
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            placeholder = { Text("Search files & folders...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                IconButton(onClick = onToggleSearch) {
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
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Breadcrumb chips
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(breadcrumbs) { crumb ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (crumb.path == currentPath) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                            onBreadcrumbClicked(crumb.path)
                                        }
                                    ) {
                                        Text(
                                            text = crumb.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (crumb.path == currentPath) FontWeight.Bold else FontWeight.Medium,
                                            color = if (crumb.path == currentPath) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // Action Icons
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onToggleSearch) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = onToggleViewMode) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = "Toggle Grid/List",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = onSortClicked) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // File Content List / Grid
            if (files.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                imageVector = if (isSearching) Icons.Outlined.SearchOff else Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isSearching) "No files matching \"$searchQuery\"" else "This folder is empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isSearching) "Try a different search term" else "Tap + to upload files or create a directory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = if (isInSelectMode) 120.dp else 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.path + ":" + it.name }) { fileItem ->
                        val isSelected = selectedItems.contains(fileItem)
                        if (isGridView) {
                            GridFileCard(
                                fileItem = fileItem,
                                isSelected = isSelected,
                                isInSelectMode = isInSelectMode,
                                showThumbnails = showThumbnails,
                                thumbnailServerAuth = thumbnailServerAuth,
                                thumbnailServerPort = thumbnailServerPort,
                                onClick = {
                                    if (isInSelectMode) {
                                        onItemLongClicked(fileItem)
                                    } else if (fileItem.isDir) {
                                        onDirectoryClicked(fileItem)
                                    } else {
                                        onFileClicked(fileItem)
                                    }
                                },
                                onLongClick = { onItemLongClicked(fileItem) },
                                onOptionsClick = { onItemOptionsClicked(fileItem) }
                            )
                        } else {
                            ListFileCard(
                                fileItem = fileItem,
                                isSelected = isSelected,
                                isInSelectMode = isInSelectMode,
                                showThumbnails = showThumbnails,
                                thumbnailServerAuth = thumbnailServerAuth,
                                thumbnailServerPort = thumbnailServerPort,
                                onClick = {
                                    if (isInSelectMode) {
                                        onItemLongClicked(fileItem)
                                    } else if (fileItem.isDir) {
                                        onDirectoryClicked(fileItem)
                                    } else {
                                        onFileClicked(fileItem)
                                    }
                                },
                                onLongClick = { onItemLongClicked(fileItem) },
                                onOptionsClick = { onItemOptionsClicked(fileItem) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button (New Folder & Upload)
        if (!isInSelectMode) {
            FloatingActionButton(
                onClick = onUploadFiles,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Files")
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selected Count & Select All
                    Column {
                        Text(
                            text = "${selectedItems.size} Selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (selectedItems.size == files.size) "Deselect All" else "Select All",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable {
                                if (selectedItems.size == files.size) onDeselectAll() else onSelectAll()
                            }
                        )
                    }

                    // Action Icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDownloadSelected) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onMoveSelected) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = onRenameSelected,
                            enabled = selectedItems.size == 1
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename",
                                tint = if (selectedItems.size == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = onDeselectAll) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridFileCard(
    fileItem: FileItem,
    isSelected: Boolean,
    isInSelectMode: Boolean,
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

    val thumbnailUrl = remember(fileItem, thumbnailServerAuth, thumbnailServerPort) {
        if (showThumbnails && isPhoto && thumbnailServerPort > 0) {
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
            // Icon or Photo Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (fileItem.isDir) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                } else if (thumbnailUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = fileItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = "File",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
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
    isInSelectMode: Boolean,
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

    val thumbnailUrl = remember(fileItem, thumbnailServerAuth, thumbnailServerPort) {
        if (showThumbnails && isPhoto && thumbnailServerPort > 0) {
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
            // Icon or Photo Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (fileItem.isDir) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                } else if (thumbnailUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = fileItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = "File",
                        tint = MaterialTheme.colorScheme.secondary,
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
