package ca.pkay.rcloneexplorer.workmanager

object SyncResultFormatter {
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

    data class Labels(
        val nothingToDo: String,
        val completedWithErrors: (Int) -> String,
        val completed: String,
        val transferSummary: (String, Int) -> String,
        val deletionSummary: (Int) -> String,
        val renameSummary: (Int) -> String
    )

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
