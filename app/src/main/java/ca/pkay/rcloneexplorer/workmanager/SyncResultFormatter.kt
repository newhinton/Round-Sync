package ca.pkay.rcloneexplorer.workmanager

/**
 * Formats the final sync result from already-collected stats.
 *
 * Example: errors=1, transfers=2 -> "Sync completed with 1 error.\nSuccessfully synced 4 MB in 2 files."
 */
object SyncResultFormatter {
    /**
     * Final rclone counters used for user-facing summaries. `totalTransfers` is copied or updated files,
     * while deletions and renames are separate change types.
     */
    data class Stats(
        val totalTransfers: Int,
        val totalSize: String,
        val deletions: Int,
        val renames: Int,
        val errors: Int
    ) {
        fun changedItems(): Int = totalTransfers + deletions + renames
        fun hasErrors(): Boolean = errors > 0
        fun hasChanges(): Boolean = changedItems() > 0
    }

    /**
     * Localized message builders supplied by Android resources.
     */
    data class Labels(
        val nothingToDo: String,
        val completedWithErrors: (Int) -> String,
        val completed: String,
        val transferSummary: (String, Int) -> String,
        val deletionSummary: (Int) -> String,
        val renameSummary: (Int) -> String
    )

    /**
     * Clean success message. "Nothing to do" is only valid when there were no changes and no errors.
     */
    fun successMessage(stats: Stats, labels: Labels): String {
        if (!stats.hasChanges() && !stats.hasErrors()) {
            return labels.nothingToDo
        }
        val lines = summaryLines(stats, labels)
        return if (lines.isEmpty()) {
            labels.completed
        } else {
            lines.joinToString("\n")
        }
    }

    /**
     * Partial result message: keep the clear error state, but still include any work rclone completed.
     */
    fun completedWithErrorsMessage(stats: Stats, labels: Labels): String {
        return (listOf(labels.completedWithErrors(stats.errors)) + summaryLines(stats, labels))
            .joinToString("\n")
    }

    private fun summaryLines(stats: Stats, labels: Labels): List<String> {
        val lines = ArrayList<String>()

        if (stats.totalTransfers > 0) {
            lines.add(labels.transferSummary(stats.totalSize, stats.totalTransfers))
        } else if (stats.hasChanges()) {
            lines.add(labels.completed)
        }

        if (stats.deletions > 0) {
            lines.add(labels.deletionSummary(stats.deletions))
        }

        if (stats.renames > 0) {
            lines.add(labels.renameSummary(stats.renames))
        }

        return lines
    }
}
