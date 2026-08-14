package ca.pkay.rcloneexplorer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ca.pkay.rcloneexplorer.Items.FileItem

data class FileTypeVisual(
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundColor: Color,
    val categoryLabel: String
)

object FileIconHelper {

    fun getVisualForFile(fileItem: FileItem): FileTypeVisual {
        if (fileItem.isDir) {
            return FileTypeVisual(
                icon = Icons.Default.Folder,
                iconColor = Color(0xFF3B82F6),
                backgroundColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                categoryLabel = "Folder"
            )
        }

        val name = fileItem.name.lowercase()
        val ext = name.substringAfterLast('.', "")
        val mime = fileItem.mimeType?.lowercase() ?: ""

        return when {
            // PDF Documents
            ext == "pdf" || mime.contains("pdf") -> FileTypeVisual(
                icon = Icons.Default.PictureAsPdf,
                iconColor = Color(0xFFEF4444),
                backgroundColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                categoryLabel = "PDF"
            )

            // Photos & Images
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "raw") -> FileTypeVisual(
                icon = Icons.Default.Image,
                iconColor = Color(0xFF10B981),
                backgroundColor = Color(0xFF10B981).copy(alpha = 0.12f),
                categoryLabel = "Image"
            )

            // Videos & Movies
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp", "m4v", "ts") -> FileTypeVisual(
                icon = Icons.Default.PlayCircle,
                iconColor = Color(0xFFF97316),
                backgroundColor = Color(0xFFF97316).copy(alpha = 0.12f),
                categoryLabel = "Video"
            )

            // Audio & Music
            mime.startsWith("audio/") || ext in listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "wma", "opus") -> FileTypeVisual(
                icon = Icons.Default.MusicNote,
                iconColor = Color(0xFF8B5CF6),
                backgroundColor = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                categoryLabel = "Audio"
            )

            // Compressed Archives
            ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz", "zst") -> FileTypeVisual(
                icon = Icons.Default.FolderZip,
                iconColor = Color(0xFFF59E0B),
                backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.12f),
                categoryLabel = "Archive"
            )

            // Code & Dev Files
            ext in listOf("kt", "java", "js", "ts", "py", "c", "cpp", "h", "cs", "go", "rs", "rb", "php", "html", "css", "json", "xml", "yaml", "yml", "sql", "sh", "bat", "gradle") -> FileTypeVisual(
                icon = Icons.Default.Code,
                iconColor = Color(0xFF06B6D4),
                backgroundColor = Color(0xFF06B6D4).copy(alpha = 0.12f),
                categoryLabel = "Code"
            )

            // Android Apps
            ext in listOf("apk", "aab", "xapk", "apks") -> FileTypeVisual(
                icon = Icons.Default.Android,
                iconColor = Color(0xFF22C55E),
                backgroundColor = Color(0xFF22C55E).copy(alpha = 0.12f),
                categoryLabel = "Android App"
            )

            // Word / Text Documents
            ext in listOf("doc", "docx", "txt", "md", "rtf", "odt", "tex", "log") -> FileTypeVisual(
                icon = Icons.Default.Description,
                iconColor = Color(0xFF2563EB),
                backgroundColor = Color(0xFF2563EB).copy(alpha = 0.12f),
                categoryLabel = "Document"
            )

            // Spreadsheets
            ext in listOf("xls", "xlsx", "csv", "tsv", "ods") -> FileTypeVisual(
                icon = Icons.Default.TableChart,
                iconColor = Color(0xFF059669),
                backgroundColor = Color(0xFF059669).copy(alpha = 0.12f),
                categoryLabel = "Spreadsheet"
            )

            // Presentations
            ext in listOf("ppt", "pptx", "odp", "key") -> FileTypeVisual(
                icon = Icons.Default.Slideshow,
                iconColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFEA580C).copy(alpha = 0.12f),
                categoryLabel = "Presentation"
            )

            // Generic / Other
            else -> FileTypeVisual(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                iconColor = Color(0xFF64748B),
                backgroundColor = Color(0xFF64748B).copy(alpha = 0.12f),
                categoryLabel = "File"
            )
        }
    }
}
