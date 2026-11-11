package ca.pkay.rcloneexplorer.workmanager

/**
 * Plain-text manifest for batch deletes.
 *
 * A multi-select delete must be handed to WorkManager as one job, but `Data` is capped at 10 KB —
 * a few hundred [ca.pkay.rcloneexplorer.Items.FileItem] parcels exceed that. Instead the enqueue
 * side writes this manifest to the cache dir and passes only the file path.
 *
 * Entries are NUL-separated (`path\0name\0flag\0` repeated). File names cannot contain NUL on any
 * supported file system, so no escaping is needed. Kept free of Android types so it is unit-testable.
 */
data class DeleteEntry(val path: String, val name: String, val isDir: Boolean)

object DeleteManifest {

    private const val SEPARATOR = '\u0000'

    data class Partition(
        val batchableFiles: List<DeleteEntry>,
        val dirs: List<DeleteEntry>,
        val fallbackFiles: List<DeleteEntry>
    )

    fun encode(entries: List<DeleteEntry>): String {
        return entries.joinToString("") { "${it.path}$SEPARATOR${it.name}$SEPARATOR${if (it.isDir) 1 else 0}$SEPARATOR" }
    }

    fun decode(manifest: String): List<DeleteEntry> {
        if (manifest.isEmpty()) {
            return emptyList()
        }
        val parts = manifest.split(SEPARATOR)
        val entries = ArrayList<DeleteEntry>(parts.size / 3)
        var i = 0
        while (i + 2 < parts.size) {
            entries.add(DeleteEntry(path = parts[i], name = parts[i + 1], isDir = parts[i + 2] == "1"))
            i += 3
        }
        return entries
    }

    /**
     * `rclone delete --files-from` is line based, so paths containing newlines (from a newline
     * in any parent directory name) cannot take the batch path; they get individual `deletefile`
     * runs instead. Directories need `purge`.
     */
    fun partition(entries: List<DeleteEntry>): Partition {
        val batchable = ArrayList<DeleteEntry>()
        val dirs = ArrayList<DeleteEntry>()
        val fallback = ArrayList<DeleteEntry>()
        for (entry in entries) {
            when {
                entry.isDir -> dirs.add(entry)
                entry.path.contains('\n') || entry.path.contains('\r') -> fallback.add(entry)
                else -> batchable.add(entry)
            }
        }
        return Partition(batchable, dirs, fallback)
    }

    /** Lines for `--files-from`, relative to the remote root (no leading slash). */
    fun filesFromLines(files: List<DeleteEntry>): List<String> {
        return files.map { it.path.removePrefix("/") }
    }
}
