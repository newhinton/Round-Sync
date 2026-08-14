package ca.pkay.rcloneexplorer.data

import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClipboardOp {
    COPY,
    CUT
}

data class ClipboardState(
    val items: List<FileItem> = emptyList(),
    val sourceRemote: RemoteItem? = null,
    val sourcePath: String = "",
    val operation: ClipboardOp = ClipboardOp.COPY
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val isNotEmpty: Boolean get() = items.isNotEmpty()
    val count: Int get() = items.size
}

object FileClipboardManager {
    private val _clipboard = MutableStateFlow(ClipboardState())
    val clipboard: StateFlow<ClipboardState> = _clipboard.asStateFlow()

    fun copy(items: List<FileItem>, sourceRemote: RemoteItem, sourcePath: String) {
        _clipboard.value = ClipboardState(
            items = items,
            sourceRemote = sourceRemote,
            sourcePath = sourcePath,
            operation = ClipboardOp.COPY
        )
    }

    fun cut(items: List<FileItem>, sourceRemote: RemoteItem, sourcePath: String) {
        _clipboard.value = ClipboardState(
            items = items,
            sourceRemote = sourceRemote,
            sourcePath = sourcePath,
            operation = ClipboardOp.CUT
        )
    }

    fun clear() {
        _clipboard.value = ClipboardState()
    }
}
