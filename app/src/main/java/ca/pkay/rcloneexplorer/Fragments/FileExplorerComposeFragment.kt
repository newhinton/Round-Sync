package ca.pkay.rcloneexplorer.Fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Dialogs.FilePropertiesDialog
import ca.pkay.rcloneexplorer.Dialogs.LinkDialog
import ca.pkay.rcloneexplorer.Dialogs.ServeDialog
import ca.pkay.rcloneexplorer.Dialogs.SortDialog
import ca.pkay.rcloneexplorer.FilePicker
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.Services.StreamingService
import ca.pkay.rcloneexplorer.ui.FileExplorerComposeScreen
import ca.pkay.rcloneexplorer.ui.viewmodel.FileExplorerViewModel
import ca.pkay.rcloneexplorer.util.ActivityHelper.tryStartService
import ca.pkay.rcloneexplorer.workmanager.EphemeralTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayList

class FileExplorerComposeFragment : Fragment(), SortDialog.OnClickListener, ServeDialog.Callback {

    companion object {
        private const val ARG_REMOTE = "remote_param"
        private const val FILE_PICKER_UPLOAD_RESULT = 186
        private const val FILE_PICKER_DOWNLOAD_RESULT = 204

        @JvmStatic
        fun newInstance(remoteItem: RemoteItem): FileExplorerComposeFragment {
            val fragment = FileExplorerComposeFragment()
            val args = Bundle()
            args.putParcelable(ARG_REMOTE, remoteItem)
            fragment.arguments = args
            return fragment
        }
    }

    private var remote: RemoteItem? = null
    private val viewModel: FileExplorerViewModel by viewModels()
    private var pendingDownloadList: List<FileItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        remote = arguments?.getParcelable(ARG_REMOTE)
    }

    fun onBackButtonPressed(): Boolean {
        return viewModel.navigateUp()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remote?.let { viewModel.initRemote(it) }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    FileExplorerComposeScreen(
                        viewModel = viewModel,
                        onFileClicked = { fileItem -> onFileClick(fileItem) },
                        onFilePropertiesClicked = { fileItem -> showFileProperties(fileItem) },
                        onFileLinkShareClicked = { fileItem -> showLinkDialog(fileItem) },
                        onUploadFiles = { startUploadPicker() },
                        onDownloadSelected = { list -> startDownloadPicker(list) },
                        onMoveSelected = { list -> viewModel.cutSelected() },
                        onSortClicked = { showSortDialog() },
                        onOpenServeDialog = { showServeDialog() }
                    )
                }
            }
        }
    }

    private fun onFileClick(fileItem: FileItem) {
        val context = context ?: return
        val currentRemote = remote ?: return

        // Launch stream or open intent
        val intent = Intent(context, StreamingService::class.java).apply {
            putExtra(StreamingService.SERVE_PATH_ARG, fileItem.path)
            putExtra(StreamingService.REMOTE_ARG, currentRemote)
            putExtra(StreamingService.SHOW_NOTIFICATION_TEXT, true)
            putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_HTTP)
        }
        tryStartService(context, intent)
    }

    private fun showFileProperties(fileItem: FileItem) {
        val currentRemote = remote ?: return
        val dialog = FilePropertiesDialog()
        dialog.setFile(fileItem)
        dialog.setRemote(currentRemote)
        if (currentRemote.isCrypt) {
            dialog.withHashCalculations(false)
        }
        dialog.show(childFragmentManager, "file properties")
    }

    private fun showLinkDialog(fileItem: FileItem) {
        val context = context ?: return
        val currentRemote = remote ?: return
        val rclone = Rclone(context)

        viewLifecycleOwner.lifecycleScope.launch {
            val link = withContext(Dispatchers.IO) {
                rclone.link(currentRemote, fileItem.path)
            }
            if (!link.isNullOrEmpty()) {
                val linkDialog = LinkDialog()
                linkDialog.setLinkUrl(link)
                linkDialog.show(childFragmentManager, "link dialog")
            } else {
                viewModel.setErrorMessage("Could not generate shareable link")
            }
        }
    }

    private fun showSortDialog() {
        val sortDialog = SortDialog()
        sortDialog.show(childFragmentManager, "sort dialog")
    }

    override fun onPositiveButtonClick(sortById: Int, sortOrderId: Int) {
        val context = context ?: return
        val sortOrder = when (sortById) {
            R.id.radio_sort_name -> if (sortOrderId == R.id.radio_sort_ascending) SortDialog.ALPHA_ASCENDING else SortDialog.ALPHA_DESCENDING
            R.id.radio_sort_size -> if (sortOrderId == R.id.radio_sort_ascending) SortDialog.SIZE_ASCENDING else SortDialog.SIZE_DESCENDING
            R.id.radio_sort_date -> if (sortOrderId == R.id.radio_sort_ascending) SortDialog.MOD_TIME_ASCENDING else SortDialog.MOD_TIME_DESCENDING
            else -> SortDialog.ALPHA_ASCENDING
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putInt("ca.pkay.rcexplorer.sort_order", sortOrder)
            .apply()
        viewModel.refresh()
    }

    private fun showServeDialog() {
        val serveDialog = ServeDialog()
        serveDialog.show(childFragmentManager, "serve dialog")
    }

    override fun onServeOptionsSelected(
        protocol: Int,
        allowRemoteAccess: Boolean,
        user: String?,
        password: String?
    ) {
        val context = context ?: return
        val currentRemote = remote ?: return

        context.stopService(Intent(context, StreamingService::class.java))

        val intent = Intent(context, StreamingService::class.java).apply {
            putExtra(StreamingService.SERVE_PATH_ARG, viewModel.uiState.value.currentPath)
            putExtra(StreamingService.REMOTE_ARG, currentRemote)
            putExtra(StreamingService.SHOW_NOTIFICATION_TEXT, true)
            putExtra(StreamingService.ALLOW_REMOTE_ACCESS, allowRemoteAccess)
            putExtra(StreamingService.AUTHENTICATION_USERNAME, user)
            putExtra(StreamingService.AUTHENTICATION_PASSWORD, password)

            when (protocol) {
                Rclone.SERVE_PROTOCOL_HTTP -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_HTTP)
                Rclone.SERVE_PROTOCOL_FTP -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_FTP)
                Rclone.SERVE_PROTOCOL_DLNA -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_DLNA)
                Rclone.SERVE_PROTOCOL_WEBDAV -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_WEBDAV)
            }
        }
        tryStartService(context, intent)
    }

    private fun startUploadPicker() {
        val intent = Intent(context, FilePicker::class.java).apply {
            putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, false)
        }
        startActivityForResult(intent, FILE_PICKER_UPLOAD_RESULT)
    }

    private fun startDownloadPicker(items: List<FileItem>) {
        pendingDownloadList = items
        val intent = Intent(context, FilePicker::class.java).apply {
            putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
        }
        startActivityForResult(intent, FILE_PICKER_DOWNLOAD_RESULT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val context = context ?: return
        val currentRemote = remote ?: return

        if (requestCode == FILE_PICKER_UPLOAD_RESULT && resultCode == Activity.RESULT_OK && data != null) {
            val result = data.getSerializableExtra(FilePicker.FILE_PICKER_RESULT) as? ArrayList<File> ?: return
            for (file in result) {
                EphemeralTaskManager.queueUpload(context, currentRemote, file.path, viewModel.uiState.value.currentPath)
            }
            viewModel.setInfoMessage("Queued ${result.size} upload(s)")
        } else if (requestCode == FILE_PICKER_DOWNLOAD_RESULT && resultCode == Activity.RESULT_OK && data != null) {
            val selectedPath = data.getStringExtra(FilePicker.FILE_PICKER_RESULT) ?: return
            for (item in pendingDownloadList) {
                EphemeralTaskManager.queueDownload(context, currentRemote, item, selectedPath)
            }
            viewModel.setInfoMessage("Queued ${pendingDownloadList.size} download(s)")
            pendingDownloadList = emptyList()
            viewModel.deselectAll()
        }
    }
}
