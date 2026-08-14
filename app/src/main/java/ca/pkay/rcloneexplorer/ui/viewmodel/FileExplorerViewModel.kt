package ca.pkay.rcloneexplorer.ui.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class FileExplorerUiState(
    val remote: RemoteItem? = null,
    val currentPath: String = "",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val rawFiles: List<FileItem> = emptyList(),
    val displayFiles: List<FileItem> = emptyList(),
    val selectedItems: Set<FileItem> = emptySet(),
    val isGridView: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val typeFilter: FileTypeFilter = FileTypeFilter.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showThumbnails: Boolean = true,
    val thumbnailServerAuth: String = "",
    val thumbnailServerPort: Int = 0,
    val isDedupeSheetOpen: Boolean = false,
    val isScanningDuplicates: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val isBatchRenameOpen: Boolean = false,
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
    private val directoryCache = ConcurrentHashMap<String, List<FileItem>>()

    init {
        viewModelScope.launch {
            bookmarksManager.bookmarksFlow.collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    fun initRemote(remote: RemoteItem) {
        val rootPath = "//${remote.name}"
        _uiState.update {
            it.copy(
                remote = remote,
                currentPath = rootPath,
                showThumbnails = prefs.getBoolean(getApplication<Application>().getString(R.string.pref_key_show_thumbnails), true),
                isGridView = prefs.getBoolean("pref_key_file_grid_view", false)
            )
        }
        pathStack.clear()
        pathStack.push(rootPath)
        loadDirectory(rootPath)
    }

    fun setThumbnailServerInfo(auth: String, port: Int) {
        _uiState.update { it.copy(thumbnailServerAuth = auth, thumbnailServerPort = port) }
    }

    fun loadDirectory(path: String, clearSearch: Boolean = true, forceRefresh: Boolean = false) {
        val currentRemote = _uiState.value.remote ?: return
        val cacheKey = "${currentRemote.name}:$path"
        val cachedFiles = directoryCache[cacheKey]

        viewModelScope.launch {
            if (cachedFiles != null && !forceRefresh) {
                // Instant pre-cached display
                val sorted = sortFiles(cachedFiles, sortOrder)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = true,
                        currentPath = path,
                        rawFiles = cachedFiles,
                        displayFiles = applyFiltersAndSearch(sorted, if (clearSearch) "" else it.searchQuery, if (clearSearch) FileTypeFilter.ALL else it.typeFilter),
                        breadcrumbs = generateBreadcrumbs(currentRemote.name, path),
                        searchQuery = if (clearSearch) "" else it.searchQuery,
                        isSearching = if (clearSearch) false else it.isSearching,
                        typeFilter = if (clearSearch) FileTypeFilter.ALL else it.typeFilter,
                        selectedItems = emptySet(),
                        errorMessage = null
                    )
                }

                // Seamless background refresh
                val freshItems = withContext(Dispatchers.IO) {
                    try {
                        rclone.getDirectoryContent(currentRemote, path, false)
                    } catch (e: Exception) {
                        FLog.e(TAG, "Background directory refresh error", e)
                        null
                    }
                }

                if (freshItems != null) {
                    directoryCache[cacheKey] = freshItems
                    val freshSorted = sortFiles(freshItems, sortOrder)
                    _uiState.update {
                        if (it.currentPath == path) {
                            it.copy(
                                isRefreshing = false,
                                rawFiles = freshItems,
                                displayFiles = applyFiltersAndSearch(freshSorted, it.searchQuery, it.typeFilter)
                            )
                        } else it
                    }
                } else {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            } else {
                // Not cached or force refreshed
                _uiState.update {
                    it.copy(
                        isLoading = cachedFiles == null,
                        isRefreshing = cachedFiles != null,
                        currentPath = path,
                        rawFiles = cachedFiles ?: emptyList(),
                        displayFiles = if (cachedFiles != null) applyFiltersAndSearch(sortFiles(cachedFiles, sortOrder), it.searchQuery, it.typeFilter) else emptyList(),
                        breadcrumbs = generateBreadcrumbs(currentRemote.name, path),
                        searchQuery = if (clearSearch) "" else it.searchQuery,
                        isSearching = if (clearSearch) false else it.isSearching,
                        typeFilter = if (clearSearch) FileTypeFilter.ALL else it.typeFilter,
                        selectedItems = emptySet(),
                        errorMessage = null
                    )
                }

                val items = withContext(Dispatchers.IO) {
                    try {
                        rclone.getDirectoryContent(currentRemote, path, false)
                    } catch (e: Exception) {
                        FLog.e(TAG, "Failed loading directory", e)
                        null
                    }
                }

                if (items != null) {
                    directoryCache[cacheKey] = items
                    val sorted = sortFiles(items, sortOrder)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            rawFiles = items,
                            displayFiles = applyFiltersAndSearch(sorted, it.searchQuery, it.typeFilter),
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = if (cachedFiles == null) "Could not load folder contents" else null
                        )
                    }
                }
            }
        }
    }

    fun refreshCurrentDirectory() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isNotEmpty()) {
            loadDirectory(currentPath, clearSearch = false, forceRefresh = true)
        }
    }

    fun invalidateCache(path: String? = null) {
        val remoteName = _uiState.value.remote?.name ?: return
        if (path != null) {
            directoryCache.remove("$remoteName:$path")
        } else {
            directoryCache.clear()
        }
    }

    fun refresh() {
        refreshCurrentDirectory()
    }

    fun navigateInto(dirItem: FileItem) {
        val newPath = dirItem.path
        pathStack.push(newPath)
        loadDirectory(newPath)
    }

    fun navigateUp(): Boolean {
        if (pathStack.size > 1) {
            pathStack.pop()
            val prevPath = pathStack.peek()
            loadDirectory(prevPath)
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(path: String) {
        val currentRemote = _uiState.value.remote ?: return
        rebuildStack(currentRemote.name, path)
        loadDirectory(path)
    }

    fun toggleViewMode() {
        val newMode = !_uiState.value.isGridView
        prefs.edit().putBoolean("pref_key_file_grid_view", newMode).apply()
        _uiState.update { it.copy(isGridView = newMode) }
    }

    fun toggleSearch() {
        val currentlySearching = _uiState.value.isSearching
        _uiState.update {
            val nextState = !currentlySearching
            val query = if (nextState) it.searchQuery else ""
            it.copy(
                isSearching = nextState,
                searchQuery = query,
                displayFiles = applyFiltersAndSearch(it.rawFiles, query, it.typeFilter)
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                displayFiles = applyFiltersAndSearch(it.rawFiles, query, it.typeFilter)
            )
        }
    }

    fun onTypeFilterChanged(filter: FileTypeFilter) {
        _uiState.update {
            it.copy(
                typeFilter = filter,
                displayFiles = applyFiltersAndSearch(it.rawFiles, it.searchQuery, filter)
            )
        }
    }

    fun toggleSelection(item: FileItem) {
        _uiState.update {
            val currentSelected = it.selectedItems.toMutableSet()
            if (currentSelected.contains(item)) {
                currentSelected.remove(item)
            } else {
                currentSelected.add(item)
            }
            it.copy(selectedItems = currentSelected)
        }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedItems = it.displayFiles.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update {
            it.copy(selectedItems = emptySet())
        }
    }

    // --- Clipboard Operations (Copy / Cut / Paste) ---

    fun copySelected() {
        val remote = _uiState.value.remote ?: return
        val selected = _uiState.value.selectedItems.toList()
        if (selected.isNotEmpty()) {
            FileClipboardManager.copy(selected, remote, _uiState.value.currentPath)
            deselectAll()
            setInfoMessage("Copied ${selected.size} items to clipboard")
        }
    }

    fun cutSelected() {
        val remote = _uiState.value.remote ?: return
        val selected = _uiState.value.selectedItems.toList()
        if (selected.isNotEmpty()) {
            FileClipboardManager.cut(selected, remote, _uiState.value.currentPath)
            deselectAll()
            setInfoMessage("Cut ${selected.size} items to clipboard")
        }
    }

    fun pasteClipboard() {
        val clip = clipboard.value
        val destRemote = _uiState.value.remote ?: return
        val destPath = _uiState.value.currentPath
        val sourceRemote = clip.sourceRemote ?: return

        if (clip.isEmpty) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val transferId = UUID.randomUUID().toString()
            addActiveTransfer(transferId, "Pasting ${clip.count} item(s)...")

            var successCount = 0
            for (item in clip.items) {
                val success = if (clip.operation == ClipboardOp.COPY) {
                    RcloneExtensions.copyItem(rclone, sourceRemote, item, destRemote, destPath)
                } else {
                    withContext(Dispatchers.IO) {
                        val process = rclone.moveTo(sourceRemote, item, destPath)
                        process?.waitFor()
                        process != null && process.exitValue() == 0
                    }
                }
                if (success) successCount++
            }

            if (clip.operation == ClipboardOp.CUT) {
                FileClipboardManager.clear()
            }
            removeActiveTransfer(transferId)
            setInfoMessage("Transferred $successCount / ${clip.count} items")
            invalidateCache(destPath)
            refreshCurrentDirectory()
        }
    }

    // --- Duplicate Operation ---

    fun duplicateSelected() {
        val remote = _uiState.value.remote ?: return
        val selected = _uiState.value.selectedItems.toList()
        val existingNames = _uiState.value.rawFiles.map { it.name }.toSet()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val transferId = UUID.randomUUID().toString()
            addActiveTransfer(transferId, "Duplicating ${selected.size} item(s)...")

            var successCount = 0
            for (item in selected) {
                val success = RcloneExtensions.duplicateItem(rclone, remote, item, existingNames)
                if (success) successCount++
            }

            removeActiveTransfer(transferId)
            deselectAll()
            setInfoMessage("Duplicated $successCount items")
            invalidateCache(_uiState.value.currentPath)
            refreshCurrentDirectory()
        }
    }

    // --- Batch Rename ---

    fun openBatchRename() {
        _uiState.update { it.copy(isBatchRenameOpen = true) }
    }

    fun closeBatchRename() {
        _uiState.update { it.copy(isBatchRenameOpen = false) }
    }

    fun executeBatchRename(rule: BatchRenameRule) {
        val remote = _uiState.value.remote ?: return
        val selected = _uiState.value.selectedItems.toList()
        closeBatchRename()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val results = RcloneExtensions.batchRename(rclone, remote, selected, rule)
            val successCount = results.count { it.second }
            deselectAll()
            setInfoMessage("Renamed $successCount / ${selected.size} items")
            invalidateCache(_uiState.value.currentPath)
            refreshCurrentDirectory()
        }
    }

    // --- Deduplication ---

    fun openDedupeSheet() {
        val remote = _uiState.value.remote ?: return
        val path = _uiState.value.currentPath
        _uiState.update { it.copy(isDedupeSheetOpen = true, isScanningDuplicates = true, duplicateGroups = emptyList()) }

        viewModelScope.launch {
            val groups = RcloneExtensions.scanDuplicates(rclone, remote, path)
            _uiState.update {
                it.copy(isScanningDuplicates = false, duplicateGroups = groups)
            }
        }
    }

    fun closeDedupeSheet() {
        _uiState.update { it.copy(isDedupeSheetOpen = false) }
    }

    fun deleteDuplicates(files: List<FileItem>) {
        val remote = _uiState.value.remote ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var deletedCount = 0
            for (file in files) {
                val success = withContext(Dispatchers.IO) {
                    val process = rclone.deleteItems(remote, file)
                    process?.waitFor()
                    process != null && process.exitValue() == 0
                }
                if (success) deletedCount++
            }
            closeDedupeSheet()
            setInfoMessage("Deleted $deletedCount duplicate files")
            invalidateCache(_uiState.value.currentPath)
            refreshCurrentDirectory()
        }
    }

    // --- Drag and Drop Move / Copy ---

    fun onDropItemIntoFolder(draggedItems: List<FileItem>, targetFolder: FileItem, isCopy: Boolean = false) {
        val remote = _uiState.value.remote ?: return
        val targetPath = targetFolder.path

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val actionName = if (isCopy) "Copying" else "Moving"
            val transferId = UUID.randomUUID().toString()
            addActiveTransfer(transferId, "$actionName ${draggedItems.size} item(s) to ${targetFolder.name}...")

            var count = 0
            for (item in draggedItems) {
                val success = if (isCopy) {
                    RcloneExtensions.copyItem(rclone, remote, item, remote, targetPath)
                } else {
                    withContext(Dispatchers.IO) {
                        val process = rclone.moveTo(remote, item, targetPath)
                        process?.waitFor()
                        process != null && process.exitValue() == 0
                    }
                }
                if (success) count++
            }

            removeActiveTransfer(transferId)
            deselectAll()
            setInfoMessage("$actionName $count item(s) to ${targetFolder.name}")
            invalidateCache(_uiState.value.currentPath)
            invalidateCache(targetPath)
            refreshCurrentDirectory()
        }
    }

    // --- Bookmarks ---

    fun openBookmarks() {
        _uiState.update { it.copy(isBookmarksOpen = true) }
    }

    fun closeBookmarks() {
        _uiState.update { it.copy(isBookmarksOpen = false) }
    }

    fun bookmarkCurrentFolder() {
        val remote = _uiState.value.remote ?: return
        val path = _uiState.value.currentPath
        bookmarksManager.addBookmark(remote.name, path)
        setInfoMessage("Folder bookmarked")
    }

    fun removeBookmark(item: BookmarkItem) {
        bookmarksManager.removeBookmark(item.remoteName, item.path)
    }

    // --- Folder Creation & Deletion ---

    fun openCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = true) }
    }

    fun closeCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = false) }
    }

    fun createFolder(name: String) {
        val remote = _uiState.value.remote ?: return
        val currentPath = _uiState.value.currentPath
        closeCreateFolderDialog()

        viewModelScope.launch {
            val relativeDir = if (currentPath == "//${remote.name}" || currentPath.isEmpty()) name else "$currentPath/$name"
            val cleanDir = relativeDir.removePrefix("//${remote.name}").trimStart('/')

            val success = withContext(Dispatchers.IO) {
                rclone.makeDirectory(remote, cleanDir)
            }
            if (success) {
                setInfoMessage("Created folder \"$name\"")
                invalidateCache(currentPath)
                refreshCurrentDirectory()
            } else {
                setErrorMessage("Failed to create folder")
            }
        }
    }

    fun deleteSelected() {
        val remote = _uiState.value.remote ?: return
        val selected = _uiState.value.selectedItems.toList()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var count = 0
            for (item in selected) {
                val success = withContext(Dispatchers.IO) {
                    val process = rclone.deleteItems(remote, item)
                    process?.waitFor()
                    process != null && process.exitValue() == 0
                }
                if (success) count++
            }
            deselectAll()
            setInfoMessage("Deleted $count items")
            invalidateCache(_uiState.value.currentPath)
            refreshCurrentDirectory()
        }
    }

    // --- Helpers ---

    private fun addActiveTransfer(id: String, title: String) {
        _uiState.update {
            it.copy(activeTransfers = it.activeTransfers + ActiveTransferItem(id = id, title = title))
        }
    }

    private fun removeActiveTransfer(id: String) {
        _uiState.update {
            it.copy(activeTransfers = it.activeTransfers.filterNot { item -> item.id == id })
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

    private fun generateBreadcrumbs(remoteName: String, path: String): List<BreadcrumbItem> {
        val crumbs = mutableListOf<BreadcrumbItem>()
        crumbs.add(BreadcrumbItem(title = remoteName, path = "//$remoteName"))

        val cleanPath = path.removePrefix("//$remoteName").trimStart('/')
        if (cleanPath.isNotEmpty()) {
            val segments = cleanPath.split('/')
            var cumulative = ""
            for (segment in segments) {
                if (segment.isNotEmpty()) {
                    cumulative = if (cumulative.isEmpty()) segment else "$cumulative/$segment"
                    crumbs.add(BreadcrumbItem(title = segment, path = cumulative))
                }
            }
        }
        return crumbs
    }

    private fun rebuildStack(remoteName: String, targetPath: String) {
        pathStack.clear()
        pathStack.push("//$remoteName")
        val cleanPath = targetPath.removePrefix("//$remoteName").trimStart('/')
        if (cleanPath.isNotEmpty()) {
            val segments = cleanPath.split('/')
            var cumulative = ""
            for (segment in segments) {
                if (segment.isNotEmpty()) {
                    cumulative = if (cumulative.isEmpty()) segment else "$cumulative/$segment"
                    pathStack.push(cumulative)
                }
            }
        }
    }

    private fun sortFiles(items: List<FileItem>, sortOrder: Int): List<FileItem> {
        val comparator = when (sortOrder) {
            SortDialog.ALPHA_DESCENDING -> FileComparators.SortAlphaDescending()
            SortDialog.SIZE_ASCENDING -> FileComparators.SortSizeAscending()
            SortDialog.SIZE_DESCENDING -> FileComparators.SortSizeDescending()
            SortDialog.MOD_TIME_ASCENDING -> FileComparators.SortModTimeAscending()
            SortDialog.MOD_TIME_DESCENDING -> FileComparators.SortModTimeDescending()
            else -> FileComparators.SortAlphaAscending()
        }
        return items.sortedWith(comparator)
    }

    private fun applyFiltersAndSearch(
        files: List<FileItem>,
        query: String,
        filter: FileTypeFilter
    ): List<FileItem> {
        return files.filter { item ->
            val matchesQuery = query.isEmpty() || item.name.contains(query, ignoreCase = true)
            val matchesType = when (filter) {
                FileTypeFilter.ALL -> true
                FileTypeFilter.IMAGES -> item.isDir || (item.mimeType?.startsWith("image/") == true)
                FileTypeFilter.VIDEOS -> item.isDir || (item.mimeType?.startsWith("video/") == true)
                FileTypeFilter.DOCUMENTS -> item.isDir || isDocMime(item)
                FileTypeFilter.AUDIO -> item.isDir || (item.mimeType?.startsWith("audio/") == true)
                FileTypeFilter.ARCHIVES -> item.isDir || isArchive(item.name)
            }
            matchesQuery && matchesType
        }
    }

    private fun isDocMime(item: FileItem): Boolean {
        val mime = item.mimeType ?: ""
        val name = item.name.lowercase()
        return mime.contains("pdf") || mime.contains("document") || mime.contains("text") ||
                name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt") ||
                name.endsWith(".xlsx") || name.endsWith(".pptx") || name.endsWith(".md")
    }

    private fun isArchive(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
                lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".bz2")
    }
}
