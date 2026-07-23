package com.app.nosatmosphereeffect.storage

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTransactionsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `multiple directories are replaced as one prepared set`() {
        val root = temporaryFolder.root
        val activeImages = directory(root, "images", "old.jpg" to "old")
        val activeOriginals = directory(root, "originals", "old-source.jpg" to "old-source")
        val stagedImages = directory(root, "images-staged", "new.jpg" to "new")
        val stagedOriginals =
            directory(root, "originals-staged", "new-source.jpg" to "new-source")

        FileTransactions.replaceDirectories(
            listOf(
                stagedImages to activeImages,
                stagedOriginals to activeOriginals
            )
        )

        assertEquals("new", File(activeImages, "new.jpg").readText())
        assertEquals("new-source", File(activeOriginals, "new-source.jpg").readText())
        assertFalse(File(activeImages, "old.jpg").exists())
        assertFalse(stagedImages.exists())
        assertFalse(stagedOriginals.exists())
    }

    @Test
    fun `validation failure leaves every active directory untouched`() {
        val root = temporaryFolder.root
        val activeImages = directory(root, "images", "old.jpg" to "old")
        val activeOriginals = directory(root, "originals", "old-source.jpg" to "old-source")
        val stagedImages = directory(root, "images-staged", "new.jpg" to "new")
        val missingOriginals = File(root, "missing-originals")

        assertThrows(IOException::class.java) {
            FileTransactions.replaceDirectories(
                listOf(
                    stagedImages to activeImages,
                    missingOriginals to activeOriginals
                )
            )
        }

        assertEquals("old", File(activeImages, "old.jpg").readText())
        assertEquals("old-source", File(activeOriginals, "old-source.jpg").readText())
        assertTrue(stagedImages.exists())
    }

    @Test
    fun `mid-install failure restores every original directory`() {
        val root = temporaryFolder.root
        val firstActive = directory(root, "first", "old-first.jpg" to "old-first")
        val secondActive = directory(root, "second", "old-second.jpg" to "old-second")
        val sharedStaging = directory(root, "staged", "new.jpg" to "new")

        assertThrows(IOException::class.java) {
            FileTransactions.replaceDirectories(
                listOf(
                    sharedStaging to firstActive,
                    sharedStaging to secondActive
                )
            )
        }

        assertEquals("old-first", File(firstActive, "old-first.jpg").readText())
        assertEquals("old-second", File(secondActive, "old-second.jpg").readText())
        assertFalse(File(firstActive, "new.jpg").exists())
        assertTrue(
            root.listFiles().orEmpty().none { file -> ".backup-" in file.name }
        )
    }

    @Test
    fun `atomic text write replaces existing content`() {
        val destination = temporaryFolder.newFile("metadata.json")
        destination.writeText("old")

        FileTransactions.writeTextAtomically(destination, "new")

        assertEquals("new", destination.readText())
    }

    @Test
    fun `multiple files are replaced from one prepared set`() {
        val root = temporaryFolder.root
        val activeImage = File(root, "wallpaper.jpg").apply { writeText("old-image") }
        val activeSource = File(root, "wallpaper_src.jpg").apply { writeText("old-source") }
        val stagedImage = File(root, ".wallpaper-staged.jpg").apply { writeText("new-image") }
        val stagedSource =
            File(root, ".wallpaper-source-staged.jpg").apply { writeText("new-source") }

        FileTransactions.replaceFiles(
            listOf(
                stagedImage to activeImage,
                stagedSource to activeSource
            )
        )

        assertEquals("new-image", activeImage.readText())
        assertEquals("new-source", activeSource.readText())
        assertFalse(stagedImage.exists())
        assertFalse(stagedSource.exists())
    }

    @Test
    fun `file replacement validation preserves active files`() {
        val root = temporaryFolder.root
        val activeImage = File(root, "wallpaper.jpg").apply { writeText("old-image") }
        val activeSource = File(root, "wallpaper_src.jpg").apply { writeText("old-source") }
        val stagedImage = File(root, ".wallpaper-staged.jpg").apply { writeText("new-image") }
        val missingSource = File(root, ".missing-source.jpg")

        assertThrows(IOException::class.java) {
            FileTransactions.replaceFiles(
                listOf(
                    stagedImage to activeImage,
                    missingSource to activeSource
                )
            )
        }

        assertEquals("old-image", activeImage.readText())
        assertEquals("old-source", activeSource.readText())
        assertTrue(stagedImage.exists())
    }

    private fun directory(parent: File, name: String, file: Pair<String, String>): File {
        return File(parent, name).apply {
            assertTrue(mkdirs())
            File(this, file.first).writeText(file.second)
        }
    }
}
