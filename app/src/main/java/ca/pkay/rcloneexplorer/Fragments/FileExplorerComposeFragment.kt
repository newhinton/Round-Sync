package ca.pkay.rcloneexplorer.Fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.Dialogs.*
import ca.pkay.rcloneexplorer.FilePicker
import ca.pkay.rcloneexplorer.data.CacheManager
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.Services.StreamingService
import ca.pkay.rcloneexplorer.Services.ThumbnailsLoadingService
import ca.pkay.rcloneexplorer.ui.FileExplorerComposeScreen
import ca.pkay.rcloneexplorer.ui.viewmodel.FileExplorerViewModel
import ca.pkay.rcloneexplorer.util.ActivityHelper.tryStartService
import ca.pkay.rcloneexplorer.util.FLog
import ca.pkay.rcloneexplorer.workmanager.EphemeralTaskManager
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.ArrayList

class FileExplorerComposeFragment : Fragment(), SortDialog.OnClickListener, ServeDialog.Callback, OpenAsDialog.OnClickListener {

    companion object {
        private const val ARG_REMOTE = "remote_param"
        private const val FILE_PICKER_UPLOAD_RESULT = 186
        private const val FILE_PICKER_DOWNLOAD_RESULT = 204
        const val STREAMING_INTENT_RESULT = 168

        const val OPEN_AS_TEXT = 1
        const val OPEN_AS_IMAGE = 2
        const val OPEN_AS_VIDEO = 3
        const val OPEN_AS_AUDIO = 4

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

    private var thumbnailServerAuth: String = ""
    private var thumbnailServerPort: Int = 0
    private var isThumbnailServiceRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        remote = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_REMOTE, RemoteItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_REMOTE)
        }
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

        // Smart On-Demand Thumbnail Service Lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState
                .map { Triple(it.displayFiles, it.showThumbnails, it.remote) }
                .distinctUntilChanged()
                .collect { (displayFiles, showThumbnails, currentRemote) ->
                    evaluateThumbnailServiceDemand(displayFiles, showThumbnails, currentRemote)
                }
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    FileExplorerComposeScreen(
                        viewModel = viewModel,
                        onFileClicked = { fileItem -> openFile(fileItem) },
                        onOpenAsClicked = { fileItem -> showOpenAsDialog(fileItem) },
                        onRenameClicked = { fileItem, newName -> viewModel.renameFile(fileItem, newName) },
                        onFilePropertiesClicked = { fileItem -> showFileProperties(fileItem) },
                        onFileLinkShareClicked = { fileItem -> showLinkDialog(fileItem) },
                        onUploadFiles = { startUploadPicker() },
                        onDownloadSelected = { list -> startDownloadPicker(list) },
                        onMoveSelected = { viewModel.cutSelected() },
                        onSortClicked = { showSortDialog() },
                        onOpenServeDialog = { showServeDialog() }
                    )
                }
            }
        }
    }

    private fun evaluateThumbnailServiceDemand(
        displayFiles: List<FileItem>,
        showThumbnails: Boolean,
        currentRemote: RemoteItem?
    ) {
        if (currentRemote == null || !showThumbnails ||
            currentRemote.isRemoteType(RemoteItem.LOCAL, RemoteItem.SAFW) ||
            currentRemote.isPathAlias
        ) {
            stopThumbnailService()
            return
        }

        val imageFiles = displayFiles.filter { !it.isDir && it.mimeType?.startsWith("image/") == true }
        if (imageFiles.isEmpty()) {
            stopThumbnailService()
            return
        }

        val ctx = context ?: return
        val diskCache = coil.Coil.imageLoader(ctx).diskCache
        val maxThumbnailSize = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getLong(getString(R.string.pref_key_thumbnail_size_limit), 26214400L)

        // Check if there is at least one visible image NOT yet cached locally
        val hasUncachedImages = imageFiles.any { item ->
            if (item.size > maxThumbnailSize) return@any false
            val cacheSignature = "${item.remote.name}:${item.path}:${item.modTime}:${item.size}"
            diskCache?.get(cacheSignature) == null
        }

        if (hasUncachedImages) {
            startThumbnailService()
        } else {
            // All images are already cached locally on disk - no service needed
            stopThumbnailService()
        }
    }

    private fun startThumbnailService() {
        val currentRemote = remote ?: return
        if (currentRemote.isRemoteType(RemoteItem.LOCAL, RemoteItem.SAFW) || currentRemote.isPathAlias) return
        val context = context ?: return
        if (isThumbnailServiceRunning) return

        try {
            val random = SecureRandom()
            val values = ByteArray(16)
            random.nextBytes(values)
            thumbnailServerAuth = Base64.encodeToString(values, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)
            thumbnailServerPort = allocatePort(29179)

            val serveIntent = Intent(context, ThumbnailsLoadingService::class.java).apply {
                putExtra(ThumbnailsLoadingService.REMOTE_ARG, currentRemote)
                putExtra(ThumbnailsLoadingService.HIDDEN_PATH, thumbnailServerAuth)
                putExtra(ThumbnailsLoadingService.SERVER_PORT, thumbnailServerPort)
            }
            tryStartService(context, serveIntent)
            isThumbnailServiceRunning = true
            viewModel.setThumbnailServerInfo(thumbnailServerAuth, thumbnailServerPort)
        } catch (e: Exception) {
            // Ignore thumbnail server startup failure
        }
    }

    private fun stopThumbnailService() {
        if (isThumbnailServiceRunning) {
            val context = context ?: return
            try {
                context.stopService(Intent(context, ThumbnailsLoadingService::class.java))
            } catch (ignored: Exception) {}
            isThumbnailServiceRunning = false
            viewModel.setThumbnailServerInfo("", 0)
        }
    }

    private fun allocatePort(port: Int): Int {
        return try {
            val serverSocket = ServerSocket(port)
            val localPort = serverSocket.localPort
            serverSocket.close()
            localPort
        } catch (e: Exception) {
            try {
                val serverSocket = ServerSocket(0)
                val localPort = serverSocket.localPort
                serverSocket.close()
                localPort
            } catch (e2: Exception) {
                29179
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopThumbnailService()
    }

    fun openFile(fileItem: FileItem, openAs: Int = -1) {
        val currentRemote = remote ?: return
        val ctx = context ?: return
        val mime = fileItem.mimeType ?: ""

        val isMedia = openAs == OPEN_AS_VIDEO || openAs == OPEN_AS_AUDIO ||
                (openAs == -1 && (mime.startsWith("video/") || mime.startsWith("audio/")))

        if (currentRemote.isRemoteType(RemoteItem.LOCAL, RemoteItem.SAFW) || currentRemote.isPathAlias) {
            try {
                val localPrefix = try { Rclone.getLocalRemotePathPrefix(currentRemote, ctx) } catch (e: Exception) { "" }
                val rawFile = File(fileItem.path)
                val localFile = if (rawFile.exists() && rawFile.isAbsolute) {
                    rawFile
                } else if (localPrefix.isNotEmpty() && File(localPrefix, fileItem.path).exists()) {
                    File(localPrefix, fileItem.path)
                } else {
                    val extStorage = android.os.Environment.getExternalStorageDirectory()
                    val fallback = File(extStorage, fileItem.path)
                    if (fallback.exists()) fallback else if (localPrefix.isNotEmpty()) File(localPrefix, fileItem.path) else rawFile
                }

                if (localFile.exists()) {
                    val sharedFileUri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".fileprovider", localFile)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val fileMime = fileItem.mimeType
                        if (!fileMime.isNullOrEmpty() && fileMime != "application/octet-stream") {
                            setDataAndTypeAndNormalize(sharedFileUri, fileMime)
                        } else {
                            setDataAndType(sharedFileUri, "*/*")
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.launchWithConfiguredOpener(
                        this@FileExplorerComposeFragment,
                        intent,
                        "Open with...",
                        fileItem,
                        onUnknownFallback = { showOpenAsDialog(fileItem) }
                    )
                    return
                }
            } catch (e: Exception) {
                FLog.e("FileExplorer", "Failed direct open for local file", e)
            }
        }

        if (isMedia) {
            streamAndOpen(fileItem, currentRemote, openAs)
        } else {
            downloadAndOpen(fileItem, currentRemote, openAs)
        }
    }

    private fun streamAndOpen(fileItem: FileItem, currentRemote: RemoteItem, openAs: Int) {
        val ctx = context ?: return
        val loadingDialog = LoadingDialog()
            .setCanCancel(false)
            .setTitle(R.string.loading)
        loadingDialog.show(childFragmentManager, "streaming loading dialog")

        viewLifecycleOwner.lifecycleScope.launch {
            val port = allocatePort(8080)
            val serveIntent = Intent(ctx, StreamingService::class.java).apply {
                putExtra(StreamingService.SERVE_PATH_ARG, fileItem.path)
                putExtra(StreamingService.REMOTE_ARG, currentRemote)
                putExtra(StreamingService.SHOW_NOTIFICATION_TEXT, false)
                putExtra(StreamingService.SERVE_PORT, port)
            }
            try {
                ctx.stopService(Intent(ctx, StreamingService::class.java))
            } catch (ignored: Exception) {}
            tryStartService(ctx, serveIntent)

            val uri = Uri.parse("http://127.0.0.1:$port")
                .buildUpon()
                .appendPath(fileItem.name)
                .build()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                when {
                    openAs == OPEN_AS_VIDEO -> setDataAndType(uri, "video/*")
                    openAs == OPEN_AS_AUDIO -> setDataAndType(uri, "audio/*")
                    fileItem.mimeType?.startsWith("audio/") == true -> setDataAndType(uri, "audio/*")
                    fileItem.mimeType?.startsWith("video/") == true -> setDataAndType(uri, "video/*")
                    else -> setData(uri)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val ready = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(uri.toString()).head().build()
                var available = false
                var waitTime = 15000L
                while (waitTime > 0 && isActive) {
                    val start = System.currentTimeMillis()
                    try {
                        val response = client.newCall(request).execute()
                        if (response.code in 200..299) {
                            available = true
                            break
                        }
                    } catch (ignored: Exception) {}
                    delay(250)
                    val elapsed = System.currentTimeMillis() - start
                    waitTime -= elapsed
                }
                available
            }

            Dialogs.dismissSilently(loadingDialog)
            if (ready && isAdded) {
                try {
                    ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.launchWithConfiguredOpener(
                        this@FileExplorerComposeFragment,
                        intent,
                        "Open with...",
                        fileItem,
                        STREAMING_INTENT_RESULT,
                        onUnknownFallback = { showOpenAsDialog(fileItem) }
                    )
                } catch (e: Exception) {
                    showOpenAsDialog(fileItem)
                }
            } else if (isAdded) {
                Toasty.error(ctx, getString(R.string.streaming_task_failed), Toast.LENGTH_LONG, true).show()
                try {
                    ctx.stopService(serveIntent)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun downloadAndOpen(fileItem: FileItem, currentRemote: RemoteItem, openAs: Int) {
        val ctx = context ?: return
        val rclone = Rclone(ctx)

        var process: Process? = null
        var isCancelled = false

        val loadingDialog = LoadingDialog()
            .setCanCancel(false)
            .setTitle(getString(R.string.loading_file))
            .setNegativeButton(getString(R.string.cancel))
            .setOnNegativeListener {
                isCancelled = true
                try {
                    process?.destroy()
                } catch (ignored: Exception) {}
            }
        loadingDialog.show(childFragmentManager, "download loading dialog")

        viewLifecycleOwner.lifecycleScope.launch {
            val cacheDirs = ContextCompat.getExternalCacheDirs(ctx)
            if (cacheDirs.isEmpty()) {
                Dialogs.dismissSilently(loadingDialog)
                Toasty.error(ctx, "Cache storage unavailable", Toast.LENGTH_SHORT, true).show()
                return@launch
            }
            val saveLocation = cacheDirs[0].absolutePath
            val fileLocation = "$saveLocation/${fileItem.name}"

            val success = withContext(Dispatchers.IO) {
                try {
                    val p = rclone.downloadFile(currentRemote, fileItem, saveLocation)
                    process = p
                    p?.waitFor()
                    p != null && p.exitValue() == 0 && !isCancelled
                } catch (e: Exception) {
                    false
                }
            }

            Dialogs.dismissSilently(loadingDialog)

            if (isCancelled) return@launch

            if (!success || !isAdded) {
                Toasty.error(ctx, "Failed to download and open file", Toast.LENGTH_SHORT, true).show()
                return@launch
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                CacheManager.pruneCacheIfNeeded(ctx)
            }

            try {
                val savedFile = File(fileLocation)
                val sharedFileUri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".fileprovider", savedFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    when (openAs) {
                        OPEN_AS_TEXT -> setDataAndType(sharedFileUri, "text/*")
                        OPEN_AS_IMAGE -> setDataAndType(sharedFileUri, "image/*")
                        else -> {
                            val mime = fileItem.mimeType
                            if (!mime.isNullOrEmpty() && mime != "application/octet-stream") {
                                setDataAndTypeAndNormalize(sharedFileUri, mime)
                            } else {
                                setDataAndType(sharedFileUri, "*/*")
                            }
                        }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                ca.pkay.rcloneexplorer.util.DefaultOpenerHelper.launchWithConfiguredOpener(
                    this@FileExplorerComposeFragment,
                    intent,
                    "Open with...",
                    fileItem,
                    onUnknownFallback = { showOpenAsDialog(fileItem) }
                )
            } catch (e: Exception) {
                FLog.e("FileExplorer", "Failed launching open intent", e)
                showOpenAsDialog(fileItem)
            }
        }
    }

    private fun showOpenAsDialog(fileItem: FileItem) {
        val dialog = OpenAsDialog().setFileItem(fileItem)
        dialog.show(childFragmentManager, "open as dialog")
    }

    override fun onClickText(fileItem: FileItem) {
        openFile(fileItem, OPEN_AS_TEXT)
    }

    override fun onClickAudio(fileItem: FileItem) {
        openFile(fileItem, OPEN_AS_AUDIO)
    }

    override fun onClickVideo(fileItem: FileItem) {
        openFile(fileItem, OPEN_AS_VIDEO)
    }

    override fun onClickImage(fileItem: FileItem) {
        openFile(fileItem, OPEN_AS_IMAGE)
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

        viewModel.setSortOrder(sortOrder)
    }

    private fun showServeDialog() {
        val serveDialog = ServeDialog()
        serveDialog.show(childFragmentManager, "serve dialog")
    }

    override fun onServeOptionsSelected(protocol: Int, allowRemoteAccess: Boolean, user: String?, password: String?) {
        val currentRemote = remote ?: return
        val ctx = context ?: return
        try {
            ctx.stopService(Intent(ctx, StreamingService::class.java))
        } catch (ignored: Exception) {}

        val intent = Intent(ctx, StreamingService::class.java).apply {
            putExtra(StreamingService.SERVE_PATH_ARG, viewModel.uiState.value.currentPath)
            putExtra(StreamingService.REMOTE_ARG, currentRemote)
            putExtra(StreamingService.SHOW_NOTIFICATION_TEXT, true)
            putExtra(StreamingService.ALLOW_REMOTE_ACCESS, allowRemoteAccess)
            if (!user.isNullOrEmpty()) putExtra(StreamingService.AUTHENTICATION_USERNAME, user)
            if (!password.isNullOrEmpty()) putExtra(StreamingService.AUTHENTICATION_PASSWORD, password)
            when (protocol) {
                Rclone.SERVE_PROTOCOL_HTTP -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_HTTP)
                Rclone.SERVE_PROTOCOL_WEBDAV -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_WEBDAV)
                Rclone.SERVE_PROTOCOL_FTP -> putExtra(StreamingService.SERVE_PROTOCOL, StreamingService.SERVE_FTP)
            }
        }
        tryStartService(ctx, intent)
    }

    private fun startUploadPicker() {
        val intent = Intent(requireContext(), FilePicker::class.java)
        startActivityForResult(intent, FILE_PICKER_UPLOAD_RESULT)
    }

    private fun startDownloadPicker(itemsToDownload: List<FileItem>) {
        pendingDownloadList = itemsToDownload
        val intent = Intent(requireContext(), FilePicker::class.java).apply {
            putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
        }
        startActivityForResult(intent, FILE_PICKER_DOWNLOAD_RESULT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val currentRemote = remote ?: return
        val ctx = context ?: return

        if (requestCode == FILE_PICKER_UPLOAD_RESULT && resultCode == Activity.RESULT_OK && data != null) {
            @Suppress("UNCHECKED_CAST")
            val uploadFiles = data.getSerializableExtra(FilePicker.FILE_PICKER_RESULT) as? ArrayList<File> ?: return
            val path = viewModel.uiState.value.currentPath
            for (file in uploadFiles) {
                EphemeralTaskManager.queueUpload(ctx, currentRemote, file.path, path)
            }
            viewModel.setInfoMessage("Queued ${uploadFiles.size} file(s) for upload")
        } else if (requestCode == FILE_PICKER_DOWNLOAD_RESULT && resultCode == Activity.RESULT_OK && data != null) {
            val destination = data.getStringExtra(FilePicker.FILE_PICKER_RESULT) ?: return
            for (item in pendingDownloadList) {
                EphemeralTaskManager.queueDownload(ctx, currentRemote, item, destination)
            }
            viewModel.setInfoMessage("Queued ${pendingDownloadList.size} file(s) for download")
            pendingDownloadList = emptyList()
        }
    }
}
