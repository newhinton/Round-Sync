package ca.pkay.rcloneexplorer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Dialogs.SortDialog
import ca.pkay.rcloneexplorer.FileComparators
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.data.*
import ca.pkay.rcloneexplorer.ui.ActiveTransferItem
import ca.pkay.rcloneexplorer.ui.BreadcrumbItem
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class FileExplorerUiState(
    val remote: RemoteItem? = null,
    val currentPath: String = "",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val rawFiles: List<FileItem> = emptyList(),
    val displayFiles: List<FileItem> = emptyList(),
    val selectedItems: Set<FileItem> = emptySet(),
    val moveModeItems: List<FileItem> = emptyList(),
    val isGridView: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val typeFilter: FileTypeFilter = FileTypeFilter.ALL,
    val showHiddenFiles: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showThumbnails: Boolean = true,
    val thumbnailServerAuth: String = "",
    val thumbnailServerPort: Int = 0,
    val hasImagesInFolder: Boolean = false,
    val isDedupeSheetOpen: Boolean = false,
    val isScanningDuplicates: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val isSortSheetOpen: Boolean = false,
    val sortOrder: Int = SortDialog.ALPHA_ASCENDING,
    val isBookmarksOpen: Boolean = false,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val isCreateFolderDialogOpen: Boolean = false,
    val activeTransfers: List<ActiveTransferItem> = emptyList(),
    val infoMessage: String? = null
)

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "FileExplorerVM"
    private val rclone = Rclone(application)
    private val bookmarksManager = BookmarksManager(application)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(FileExplorerUiState())
    val uiState: StateFlow<FileExplorerUiState> = _uiState.asStateFlow()

    val clipboard = FileClipboardManager.clipboard

    private var sortOrder: Int = prefs.getInt("ca.pkay.rcexplorer.sort_order", SortDialog.ALPHA_ASCENDING)
    private val pathStack = Stack<String>()
    private var backgroundRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            bookmarksManager.bookmarksFlow.collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    fun initRemote(remote: RemoteItem) {
        val rootPath = "//${remote.name}"
        val showHidden = prefs.getBoolean("pref_key_show_hidden_files", false)
        sortOrder = prefs.getInt("ca.pkay.rcexplorer.sort_order", SortDialog.ALPHA_ASCENDING)
        _uiState.update {
            it.copy(
                remote = remote,
                currentPath = rootPath,
                showHiddenFiles = showHidden,
                sortOrder = sortOrder,
                showThumbnails = prefs.getBoolean(getApplication<Application>().getString(R.string.pref_key_show_thumbnails), true),
                isGridView = prefs.getBoolean("pref_key_file_grid_view", false)
            )
        }
        pathStack.clear()
        pathStack.push(rootPath)
        loadDirectory(rootPath, clearSearch = true, forceRefresh = false, isNavigatingBack = false)
    }

    fun setThumbnailServerInfo(auth: String, port: Int) {
        _uiState.update { it.copy(thumbnailServerAuth = auth, thumbnailServerPort = port) }
    }

    private fun normalizeRclonePath(remoteName: String, path: String): String {
        val root = "//$remoteName"
        if (path == root || path.isEmpty()) {
            return root
        }
        val clean = path.removePrefix(root).trimStart('/')
        return if (clean.isEmpty()) root else clean
    }

    fun loadDirectory(
        path: String,
        clearSearch: Boolean = true,
        forceRefresh: Boolean = false,
        isNavigatingBack: Boolean = false
    ) {
        val currentRemote = _uiState.value.remote ?: return
        val cachedFiles = DirectoryCacheRepository.getWithDiskFallback(getApplication(), currentRemote, path)
        val showHidden = _uiState.value.showHiddenFiles
        val rclonePath = normalizeRclonePath(currentRemote.name, path)

        // Always cancel previous background refresh job when navigating
        backgroundRefreshJob?.cancel()

        viewModelScope.launch {
            if (cachedFiles != null && !forceRefresh) {
                // Instant pre-cached display (0ms delay)
                val sorted = sortFiles(cachedFiles, sortOrder)
                val filtered = applyFiltersAndSearch(
                    sorted,
                    if (clearSearch) "" else _uiState.value.searchQuery,
                    if (clearSearch) FileTypeFilter.ALL else _uiState.value.typeFilter,
                    showHidden
                )
                val hasImages = filtered.any { !it.isDir && it.mimeType?.startsWith("image/") == true }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        currentPath = path,
                        rawFiles = cachedFiles,
                        displayFiles = filtered,
                        hasImagesInFolder = hasImages,
                        breadcrumbs = generateBreadcrumbs(currentRemote.name, path),
                        searchQuery = if (clearSearch) "" else it.searchQuery,
                        isSearching = if (clearSearch) false else it.isSearching,
                        typeFilter = if (clearSearch) FileTypeFilter.ALL else it.typeFilter,
                        selectedItems = emptySet(),
                        errorMessage = null
                    )
                }

                // If navigating backward: DO NOT background refresh (zero bandwidth / jitter)
                // If navigating forward/direct: schedule 2-second debounced background refresh
                if (!isNavigatingBack) {
                    backgroundRefreshJob = viewModelScope.launch {
                        delay(2000) // 2-second debounce
                        if (!isActive || _uiState.value.currentPath != path) return@launch

                        _uiState.update { it.copy(isRefreshing = true) }
                        val freshItems = withContext(Dispatchers.IO) {
                            try {
                                rclone.getDirectoryContent(currentRemote, rclonePath, false)
                            } catch (e: Exception) {
                                FLog.e(TAG, "Background directory refresh error", e)
                                null
                            }
                        }

                        if (!isActive || _uiState.value.currentPath != path) return@launch

                        if (freshItems != null) {
                            DirectoryCacheRepository.putWithDiskPersist(getApplication(), currentRemote.name, path, freshItems)
                            val freshSorted = sortFiles(freshItems, sortOrder)
                            val freshFiltered = applyFiltersAndSearch(
                                freshSorted,
                                _uiState.value.searchQuery,
                                _uiState.value.typeFilter,
                                _uiState.value.showHiddenFiles
                            )
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    rawFiles = freshItems,
                                    displayFiles = freshFiltered,
                                    hasImagesInFolder = freshFiltered.any { item -> !item.isDir && item.mimeType?.startsWith("image/") == true }
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isRefreshing = false) }
                        }
                    }
                }
            } else {
                // Not in cache or forced refresh
                _uiState.update {
                    it.copy(
                        isLoading = cachedFiles == null,
                        isRefreshing = cachedFiles != null,
                        currentPath = path,
                        rawFiles = cachedFiles ?: emptyList(),
                        displayFiles = if (cachedFiles != null) applyFiltersAndSearch(sortFiles(cachedFiles, sortOrder), it.searchQuery, it.typeFilter, showHidden) else emptyList(),
                        breadcrumbs = generateBreadcrumbs(currentRemote.name, path),
                        searchQuery = if (clearSearch) "" else it.searchQuery,
                        isSearching = if (clearSearch) false else it.isSearching,
                        typeFilter = if (clearSearch) FileTypeFilter.ALL else it.typeFilter,
                        selectedItems = emptySet(),
                        errorMessage = null
                    )
                }

                val result = withContext(Dispatchers.IO) {
                    try {
                        rclone.getDirectoryContent(currentRemote, rclonePath, false)
                    } catch (e: Exception) {
                        FLog.e(TAG, "Error loading directory", e)
                        null
                    }
                }

                if (result != null) {
                    DirectoryCacheRepository.putWithDiskPersist(getApplication(), currentRemote.name, path, result)
                    val sorted = sortFiles(result, sortOrder)
                    val filtered = applyFiltersAndSearch(
                        sorted,
                        _uiState.value.searchQuery,
                        _uiState.value.typeFilter,
                        _uiState.value.showHiddenFiles
                    )
                    val hasImages = filtered.any { !it.isDir && it.mimeType?.startsWith("image/") == true }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            rawFiles = result,
                            displayFiles = filtered,
                            hasImagesInFolder = hasImages
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = if (cachedFiles == null) "Failed to load directory" else null
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isNotEmpty()) {
            loadDirectory(currentPath, clearSearch = false, forceRefresh = true, isNavigatingBack = false)
        }
    }

    fun setSortOrder(order: Int) {
        sortOrder = order
        prefs.edit().putInt("ca.pkay.rcexplorer.sort_order", order).apply()
        val sorted = sortFiles(_uiState.value.rawFiles, order)
        val filtered = applyFiltersAndSearch(
            sorted,
            _uiState.value.searchQuery,
            _uiState.value.typeFilter,
            _uiState.value.showHiddenFiles
        )
        _uiState.update {
            it.copy(
                rawFiles = sorted,
                displayFiles = filtered
            )
        }
    }

    private fun sortFiles(files: List<FileItem>, order: Int): List<FileItem> {
        val comparator = when (order) {
            SortDialog.ALPHA_ASCENDING -> FileComparators.SortAlphaAscending()
            SortDialog.ALPHA_DESCENDING -> FileComparators.SortAlphaDescending()
            SortDialog.SIZE_ASCENDING -> FileComparators.SortSizeAscending()
            SortDialog.SIZE_DESCENDING -> FileComparators.SortSizeDescending()
            SortDialog.MOD_TIME_ASCENDING -> FileComparators.SortModTimeAscending()
            SortDialog.MOD_TIME_DESCENDING -> FileComparators.SortModTimeDescending()
            else -> FileComparators.SortAlphaAscending()
        }
        return files.sortedWith(comparator)
    }

    fun navigateInto(folder: FileItem) {
        val currentRemote = _uiState.value.remote ?: return
        val rootPath = "//${currentRemote.name}"
        val folderRelPath = folder.path.removePrefix(rootPath).trimStart('/')
        val targetPath = if (folderRelPath.isEmpty()) rootPath else "$rootPath/$folderRelPath"

        pathStack.push(targetPath)
        loadDirectory(targetPath, clearSearch = true, forceRefresh = false, isNavigatingBack = false)
    }

    fun navigateUp(): Boolean {
        if (pathStack.size > 1) {
            pathStack.pop()
            val parentPath = pathStack.peek()
            // Navigate back silently using cached data (zero background refresh)
            loadDirectory(parentPath, clearSearch = true, forceRefresh = false, isNavigatingBack = true)
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(breadcrumb: BreadcrumbItem) {
        val targetPath = breadcrumb.path
        if (targetPath != _uiState.value.currentPath) {
            while (pathStack.size > 1 && pathStack.peek() != targetPath) {
                pathStack.pop()
            }
            if (pathStack.isEmpty() || pathStack.peek() != targetPath) {
                pathStack.push(targetPath)
            }
            loadDirectory(targetPath, clearSearch = true, forceRefresh = false, isNavigatingBack = false)
        }
    }

    fun startMoveMode(items: List<FileItem>) {
        _uiState.update {
            it.copy(
                moveModeItems = items,
                selectedItems = emptySet()
            )
        }
    }

    fun cancelMoveMode() {
        _uiState.update { it.copy(moveModeItems = emptyList()) }
    }

    fun executeMoveHere() {
        val itemsToMove = _uiState.value.moveModeItems
        if (itemsToMove.isEmpty()) return
        val currentRemote = _uiState.value.remote ?: return
        val destinationPath = _uiState.value.currentPath

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, moveModeItems = emptyList()) }
            var successCount = 0
            withContext(Dispatchers.IO) {
                for (item in itemsToMove) {
                    try {
                        val target = if (destinationPath.endsWith("/")) destinationPath + item.name else "$destinationPath/${item.name}"
                        val cleanOld = item.path.removePrefix("//${currentRemote.name}/").removePrefix("//${currentRemote.name}")
                        val cleanNew = target.removePrefix("//${currentRemote.name}/").removePrefix("//${currentRemote.name}")
                        val moved = rclone.moveTo(currentRemote, cleanOld, cleanNew)
                        if (moved != null && moved) {
                            successCount++
                        }
                    } catch (e: Exception) {
                        FLog.e(TAG, "Failed moving file ${item.name}", e)
                    }
                }
            }
            DirectoryCacheRepository.remove(currentRemote.name, destinationPath)
            _uiState.update { it.copy(infoMessage = "Moved $successCount item(s)") }
            loadDirectory(destinationPath, clearSearch = false, forceRefresh = true, isNavigatingBack = false)
        }
    }

    fun toggleSelection(item: FileItem) {
        _uiState.update {
            val next = it.selectedItems.toMutableSet()
            if (next.contains(item)) {
                next.remove(item)
            } else {
                next.add(item)
            }
            it.copy(selectedItems = next)
        }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedItems = it.displayFiles.toSet())
        }
    }

    fun invertSelection() {
        _uiState.update {
            val all = it.displayFiles.toSet()
            val inverted = all - it.selectedItems
            it.copy(selectedItems = inverted)
        }
    }

    fun cancelSelection() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    fun deselectAll() {
        cancelSelection()
    }

    fun copySelected() {
        val items = _uiState.value.selectedItems.toList()
        val remote = _uiState.value.remote ?: return
        if (items.isNotEmpty()) {
            FileClipboardManager.copy(items, remote, _uiState.value.currentPath)
            _uiState.update { it.copy(selectedItems = emptySet(), infoMessage = "Copied ${items.size} item(s) to clipboard") }
        }
    }

    fun cutSelected() {
        val items = _uiState.value.selectedItems.toList()
        val remote = _uiState.value.remote ?: return
        if (items.isNotEmpty()) {
            FileClipboardManager.cut(items, remote, _uiState.value.currentPath)
            _uiState.update { it.copy(selectedItems = emptySet(), infoMessage = "Cut ${items.size} item(s) to clipboard") }
        }
    }

    fun pasteClipboard() {
        val clip = FileClipboardManager.clipboard.value
        if (clip.isEmpty) return
        val currentRemote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                for (item in clip.items) {
                    try {
                        val destLocation = if (currentPath == "//${currentRemote.name}" || currentPath.isEmpty()) {
                            item.name
                        } else {
                            val clean = currentPath.removePrefix("//${currentRemote.name}/").removePrefix("//${currentRemote.name}")
                            if (clean.isEmpty()) item.name else "$clean/${item.name}"
                        }
                        if (clip.operation == ClipboardOp.COPY) {
                            RcloneExtensions.copyItem(rclone, clip.sourceRemote ?: currentRemote, item, currentRemote, currentPath)
                        } else {
                            val cleanOld = item.path.removePrefix("//${currentRemote.name}/").removePrefix("//${currentRemote.name}")
                            rclone.moveTo(currentRemote, cleanOld, destLocation)
                        }
                    } catch (e: Exception) {
                        FLog.e(TAG, "Paste clipboard error", e)
                    }
                }
            }
            if (clip.operation == ClipboardOp.CUT) {
                FileClipboardManager.clear()
            }
            DirectoryCacheRepository.remove(currentRemote.name, currentPath)
            loadDirectory(currentPath, clearSearch = false, forceRefresh = true)
        }
    }

    fun duplicateSelected() {
        val items = _uiState.value.selectedItems.toList()
        val remote = _uiState.value.remote ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                for (item in items) {
                    try {
                        RcloneExtensions.copyItem(rclone, remote, item, remote, _uiState.value.currentPath)
                    } catch (e: Exception) {
                        FLog.e(TAG, "Duplicate error", e)
                    }
                }
            }
            DirectoryCacheRepository.remove(remote.name, _uiState.value.currentPath)
            loadDirectory(_uiState.value.currentPath, clearSearch = false, forceRefresh = true)
        }
    }


    fun openSortSheet() {
        _uiState.update { it.copy(isSortSheetOpen = true) }
    }

    fun closeSortSheet() {
        _uiState.update { it.copy(isSortSheetOpen = false) }
    }

    fun applySortOrder(newOrder: Int) {
        sortOrder = newOrder
        prefs.edit().putInt("ca.pkay.rcexplorer.sort_order", newOrder).apply()
        val currentRaw = _uiState.value.rawFiles
        val sorted = sortFiles(currentRaw, newOrder)
        val filtered = applyFiltersAndSearch(
            sorted,
            _uiState.value.searchQuery,
            _uiState.value.typeFilter,
            _uiState.value.showHiddenFiles
        )
        _uiState.update {
            it.copy(
                sortOrder = newOrder,
                isSortSheetOpen = false,
                rawFiles = sorted,
                displayFiles = filtered
            )
        }
    }

    fun openDedupeSheet() {
        val currentDisplay = _uiState.value.displayFiles
        _uiState.update { it.copy(isDedupeSheetOpen = true, isScanningDuplicates = true, duplicateGroups = emptyList()) }
        viewModelScope.launch {
            val groups = withContext(Dispatchers.Default) {
                // Group by exact same name and size in current folder
                val filesOnly = currentDisplay.filter { !it.isDir }
                filesOnly.groupBy { "${it.name.lowercase()}_${it.size}" }
                    .filter { it.value.size > 1 }
                    .map { (key, list) ->
                        DuplicateGroup(
                            hashOrKey = key,
                            size = list.first().size,
                            items = list
                        )
                    }
            }
            _uiState.update { it.copy(isScanningDuplicates = false, duplicateGroups = groups) }
        }
    }

    fun closeDedupeSheet() {
        _uiState.update { it.copy(isDedupeSheetOpen = false) }
    }

    fun deleteDuplicates(files: List<FileItem>) {
        val remote = _uiState.value.remote ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isDedupeSheetOpen = false) }
            withContext(Dispatchers.IO) {
                for (file in files) {
                    try {
                        rclone.deleteItems(remote, file)?.waitFor()
                    } catch (e: Exception) {
                        FLog.e(TAG, "Failed deleting duplicate", e)
                    }
                }
            }
            DirectoryCacheRepository.remove(remote.name, _uiState.value.currentPath)
            loadDirectory(_uiState.value.currentPath, clearSearch = false, forceRefresh = true)
        }
    }

    fun openBookmarks() {
        _uiState.update { it.copy(isBookmarksOpen = true) }
    }

    fun closeBookmarks() {
        _uiState.update { it.copy(isBookmarksOpen = false) }
    }

    fun removeBookmark(bookmark: BookmarkItem) {
        bookmarksManager.removeBookmark(bookmark.remoteName, bookmark.path)
    }

    fun bookmarkCurrentFolder() {
        val remote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath
        val name = _uiState.value.breadcrumbs.lastOrNull()?.title ?: remote.name
        bookmarksManager.addBookmark(remote.name, currentPath, name)
        _uiState.update { it.copy(infoMessage = "Added \"$name\" to bookmarks") }
    }

    fun openCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = true) }
    }

    fun closeCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = false) }
    }

    fun createFolder(name: String) {
        createDirectory(name)
        closeCreateFolderDialog()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            val filtered = applyFiltersAndSearch(
                it.rawFiles,
                query,
                it.typeFilter,
                it.showHiddenFiles
            )
            it.copy(
                searchQuery = query,
                displayFiles = filtered
            )
        }
    }

    fun toggleSearch() {
        _uiState.update {
            val next = !it.isSearching
            val query = if (next) it.searchQuery else ""
            val filtered = applyFiltersAndSearch(
                it.rawFiles,
                query,
                it.typeFilter,
                it.showHiddenFiles
            )
            it.copy(
                isSearching = next,
                searchQuery = query,
                displayFiles = filtered
            )
        }
    }

    fun onTypeFilterChanged(filter: FileTypeFilter) {
        _uiState.update {
            val filtered = applyFiltersAndSearch(
                it.rawFiles,
                it.searchQuery,
                filter,
                it.showHiddenFiles
            )
            it.copy(
                typeFilter = filter,
                displayFiles = filtered
            )
        }
    }

    fun toggleGridView() {
        _uiState.update {
            val next = !it.isGridView
            prefs.edit().putBoolean("pref_key_file_grid_view", next).apply()
            it.copy(isGridView = next)
        }
    }

    fun toggleViewMode() {
        toggleGridView()
    }

    fun toggleShowHiddenFiles() {
        _uiState.update {
            val next = !it.showHiddenFiles
            prefs.edit().putBoolean("pref_key_show_hidden_files", next).apply()
            val filtered = applyFiltersAndSearch(
                it.rawFiles,
                it.searchQuery,
                it.typeFilter,
                next
            )
            it.copy(
                showHiddenFiles = next,
                displayFiles = filtered
            )
        }
    }

    fun deleteSelectedFiles() {
        val selected = _uiState.value.selectedItems.toList()
        if (selected.isEmpty()) return
        val currentRemote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath

        // Optimistic UI Update: remove items immediately from UI state
        val updatedRaw = _uiState.value.rawFiles.filterNot { selected.contains(it) }
        val updatedDisplay = applyFiltersAndSearch(
            sortFiles(updatedRaw, sortOrder),
            _uiState.value.searchQuery,
            _uiState.value.typeFilter,
            _uiState.value.showHiddenFiles
        )
        _uiState.update {
            it.copy(
                rawFiles = updatedRaw,
                displayFiles = updatedDisplay,
                selectedItems = emptySet(),
                isRefreshing = true
            )
        }

        viewModelScope.launch {
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                for (item in selected) {
                    try {
                        val proc = rclone.deleteItems(currentRemote, item)
                        proc?.waitFor()
                        deletedCount++
                    } catch (e: Exception) {
                        FLog.e(TAG, "Error deleting item: ${item.name}", e)
                    }
                }
            }
            DirectoryCacheRepository.remove(currentRemote.name, currentPath)
            _uiState.update { it.copy(infoMessage = "Deleted $deletedCount item(s)", isRefreshing = false) }
            loadDirectory(currentPath, clearSearch = false, forceRefresh = true, isNavigatingBack = false)
        }
    }

    fun deleteSelected() {
        deleteSelectedFiles()
    }

    fun deleteSingleFile(fileItem: FileItem) {
        val currentRemote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath

        // Optimistic UI Update: remove item immediately from UI state
        val updatedRaw = _uiState.value.rawFiles.filterNot { it == fileItem }
        val updatedDisplay = applyFiltersAndSearch(
            sortFiles(updatedRaw, sortOrder),
            _uiState.value.searchQuery,
            _uiState.value.typeFilter,
            _uiState.value.showHiddenFiles
        )
        _uiState.update {
            it.copy(
                rawFiles = updatedRaw,
                displayFiles = updatedDisplay,
                selectedItems = it.selectedItems - fileItem,
                isRefreshing = true
            )
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val proc = rclone.deleteItems(currentRemote, fileItem)
                    proc?.waitFor()
                } catch (e: Exception) {
                    FLog.e(TAG, "Error deleting item ${fileItem.name}", e)
                }
            }
            DirectoryCacheRepository.remove(currentRemote.name, currentPath)
            _uiState.update { it.copy(infoMessage = "Deleted \"${fileItem.name}\"", isRefreshing = false) }
            loadDirectory(currentPath, clearSearch = false, forceRefresh = true, isNavigatingBack = false)
        }
    }

    fun createDirectory(folderName: String) {
        val currentRemote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath
        val newDirPath = if (currentPath == "//${currentRemote.name}") {
            folderName
        } else {
            val clean = currentPath.removePrefix("//${currentRemote.name}/").removePrefix("//${currentRemote.name}")
            if (clean.isEmpty()) folderName else "$clean/$folderName"
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val created = withContext(Dispatchers.IO) {
                try {
                    rclone.makeDirectory(currentRemote, newDirPath)
                } catch (e: Exception) {
                    FLog.e(TAG, "Error creating directory $folderName", e)
                    false
                }
            }
            if (created == true) {
                DirectoryCacheRepository.remove(currentRemote.name, currentPath)
                _uiState.update { it.copy(infoMessage = "Created folder \"$folderName\"", isRefreshing = false) }
                loadDirectory(currentPath, clearSearch = false, forceRefresh = true, isNavigatingBack = false)
            } else {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = "Failed to create folder") }
            }
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        val currentRemote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath
        if (fileItem.name == newName || newName.isBlank()) return

        val cleanOld = fileItem.path.removePrefix("//${currentRemote.name}/").removePrefix("//${currentRemote.name}")
        val cleanParent = cleanOld.substringBeforeLast('/', "")
        val cleanNew = if (cleanParent.isEmpty()) newName else "$cleanParent/$newName"

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val moved = withContext(Dispatchers.IO) {
                try {
                    rclone.moveTo(currentRemote, cleanOld, cleanNew)
                } catch (e: Exception) {
                    FLog.e(TAG, "Failed renaming file", e)
                    false
                }
            }
            if (moved == true) {
                DirectoryCacheRepository.remove(currentRemote.name, currentPath)
                _uiState.update { it.copy(infoMessage = "Renamed to \"$newName\"", isRefreshing = false) }
                loadDirectory(currentPath, clearSearch = false, forceRefresh = true, isNavigatingBack = false)
            } else {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = "Failed to rename file") }
            }
        }
    }

    fun setInfoMessage(msg: String) {
        _uiState.update { it.copy(infoMessage = msg) }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun setErrorMessage(msg: String) {
        _uiState.update { it.copy(errorMessage = msg) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun generateBreadcrumbs(remoteName: String, path: String): List<BreadcrumbItem> {
        val list = mutableListOf<BreadcrumbItem>()
        val rootPath = "//$remoteName"
        list.add(BreadcrumbItem(title = remoteName, path = rootPath))

        val relative = path.removePrefix(rootPath).trimStart('/')
        if (relative.isNotEmpty()) {
            val segments = relative.split('/')
            var cumulative = rootPath
            for (seg in segments) {
                if (seg.isNotBlank()) {
                    cumulative += "/$seg"
                    list.add(BreadcrumbItem(title = seg, path = cumulative))
                }
            }
        }
        return list
    }

    private fun applyFiltersAndSearch(
        rawList: List<FileItem>,
        query: String,
        typeFilter: FileTypeFilter,
        showHidden: Boolean
    ): List<FileItem> {
        return rawList.filter { item ->
            val matchesHidden = if (!showHidden) !item.name.startsWith(".") else true
            val matchesQuery = if (query.isNotBlank()) item.name.contains(query, ignoreCase = true) else true
            val matchesType = when (typeFilter) {
                FileTypeFilter.ALL -> true
                FileTypeFilter.IMAGES -> item.isDir || item.mimeType?.startsWith("image/") == true
                FileTypeFilter.VIDEOS -> item.isDir || item.mimeType?.startsWith("video/") == true
                FileTypeFilter.AUDIO -> item.isDir || item.mimeType?.startsWith("audio/") == true
                FileTypeFilter.DOCUMENTS -> item.isDir || item.mimeType?.contains("pdf") == true || item.mimeType?.contains("document") == true || item.mimeType?.contains("text") == true
                FileTypeFilter.ARCHIVES -> item.isDir || item.mimeType?.contains("zip") == true || item.mimeType?.contains("tar") == true || item.mimeType?.contains("rar") == true || item.mimeType?.contains("7z") == true
            }
            matchesHidden && matchesQuery && matchesType
        }
    }
}
