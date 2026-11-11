package ca.pkay.rcloneexplorer.workmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteManifestTest {

    private val entries = listOf(
        DeleteEntry(path = "/photos/img 1.jpg", name = "img 1.jpg", isDir = false),
        DeleteEntry(path = "/photos/ünïcode.txt", name = "ünïcode.txt", isDir = false),
        DeleteEntry(path = "/photos/sub dir", name = "sub dir", isDir = true),
        DeleteEntry(path = "/notes.md", name = "notes.md", isDir = false)
    )

    @Test
    fun roundTripPreservesAllEntries() {
        val decoded = DeleteManifest.decode(DeleteManifest.encode(entries))
        assertEquals(entries, decoded)
    }

    @Test
    fun emptyManifestDecodesToEmptyList() {
        assertTrue(DeleteManifest.decode("").isEmpty())
        assertEquals("", DeleteManifest.encode(emptyList()))
    }

    @Test
    fun decodeMalformedManifestDropsIncompleteTrailingEntry() {
        val manifest = DeleteManifest.encode(entries) + "/orphan\u0000name"
        val decoded = DeleteManifest.decode(manifest)
        assertEquals(entries, decoded)
    }

    @Test
    fun partitionSeparatesDirsAndBatchableFiles() {
        val partition = DeleteManifest.partition(entries)
        assertEquals(
            listOf("/photos/img 1.jpg", "/photos/ünïcode.txt", "/notes.md"),
            partition.batchableFiles.map { it.path }
        )
        assertEquals(listOf("/photos/sub dir"), partition.dirs.map { it.path })
        assertTrue(partition.fallbackFiles.isEmpty())
    }

    @Test
    fun filesFromLinesAreRootRelativeWithoutLeadingSlash() {
        val partition = DeleteManifest.partition(entries)
        assertEquals(
            listOf("photos/img 1.jpg", "photos/ünïcode.txt", "notes.md"),
            DeleteManifest.filesFromLines(partition.batchableFiles)
        )
    }

    @Test
    fun filesWithNewlinesInNameFallBackToSingleDelete() {
        val tricky = listOf(
            DeleteEntry(path = "/a/normal.txt", name = "normal.txt", isDir = false),
            DeleteEntry(path = "/a/bad\nname.txt", name = "bad\nname.txt", isDir = false),
            DeleteEntry(path = "/a/worse\rname.txt", name = "worse\rname.txt", isDir = false)
        )
        val partition = DeleteManifest.partition(tricky)
        assertEquals(listOf("/a/normal.txt"), partition.batchableFiles.map { it.path })
        assertEquals(
            listOf("/a/bad\nname.txt", "/a/worse\rname.txt"),
            partition.fallbackFiles.map { it.path }
        )
    }

    @Test
    fun fileInsideDirectoryWithNewlineInNameFallsBackToSingleDelete() {
        // The files-from line contains the full path, so a newline anywhere in the path corrupts it.
        val tricky = listOf(
            DeleteEntry(path = "/foo\nbar/clean.txt", name = "clean.txt", isDir = false)
        )
        val partition = DeleteManifest.partition(tricky)
        assertTrue(partition.batchableFiles.isEmpty())
        assertEquals(listOf("/foo\nbar/clean.txt"), partition.fallbackFiles.map { it.path })
    }
}
