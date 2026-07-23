package com.app.nosatmosphereeffect.storage

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal object FileTransactions {
    private const val TAG = "FileTransactions"

    @Throws(IOException::class)
    fun prepareEmptyDirectory(directory: File) {
        deleteRecursively(directory)
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not create ${directory.absolutePath}")
        }
    }

    @Throws(IOException::class)
    fun writeTextAtomically(destination: File, text: String) {
        val directory = destination.parentFile
            ?: throw IOException("Destination has no parent directory")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create ${directory.absolutePath}")
        }

        val temporary = File.createTempFile("${destination.name}.", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            moveReplacing(temporary, destination)
        } finally {
            deleteRecursivelyQuietly(temporary)
        }
    }

    @Throws(IOException::class)
    fun replaceDirectories(replacements: List<Pair<File, File>>) {
        replaceEntries(replacements, expectedType = EntryType.DIRECTORY)
    }

    @Throws(IOException::class)
    fun replaceFiles(replacements: List<Pair<File, File>>) {
        replaceEntries(replacements, expectedType = EntryType.FILE)
    }

    private fun replaceEntries(
        replacements: List<Pair<File, File>>,
        expectedType: EntryType
    ) {
        require(replacements.isNotEmpty()) { "At least one replacement is required" }
        require(replacements.map { it.second.absolutePath }.distinct().size == replacements.size) {
            "Replacement targets must be unique"
        }
        replacements.forEach { (staged, target) ->
            val validSource = when (expectedType) {
                EntryType.DIRECTORY -> staged.isDirectory
                EntryType.FILE -> staged.isFile
            }
            if (!validSource) {
                throw IOException("Staged ${expectedType.label} is missing: ${staged.absolutePath}")
            }
            if (staged.parentFile?.canonicalFile != target.parentFile?.canonicalFile) {
                throw IOException("Staged and target entries must share a parent")
            }
        }

        val token = UUID.randomUUID().toString()
        val backups = replacements.associate { (_, target) ->
            target to File(target.parentFile, ".${target.name}.backup-$token")
        }
        val installedTargets = mutableListOf<File>()
        val backedUpTargets = mutableListOf<File>()
        var committed = false

        try {
            replacements.forEach { (_, target) ->
                if (target.exists()) {
                    val backup = backups.getValue(target)
                    moveReplacing(target, backup)
                    backedUpTargets += target
                }
            }
            replacements.forEach { (staged, target) ->
                moveReplacing(staged, target)
                installedTargets += target
            }
            committed = true
        } catch (failure: Exception) {
            installedTargets.asReversed().forEach(::deleteRecursivelyQuietly)
            backedUpTargets.asReversed().forEach { target ->
                val backup = backups.getValue(target)
                if (backup.exists()) {
                    try {
                        moveReplacing(backup, target)
                    } catch (rollbackFailure: Exception) {
                        failure.addSuppressed(rollbackFailure)
                    }
                }
            }
            throw failure
        } finally {
            if (committed) backups.values.forEach(::deleteRecursivelyQuietly)
        }
    }

    @Throws(IOException::class)
    fun deleteRecursively(file: File) {
        if (file.exists() && !file.deleteRecursively() && file.exists()) {
            throw IOException("Could not delete ${file.absolutePath}")
        }
    }

    @Throws(IOException::class)
    fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun deleteRecursivelyQuietly(file: File) {
        try {
            deleteRecursively(file)
        } catch (error: Exception) {
            Log.w(TAG, "Could not clean up ${file.absolutePath}", error)
        }
    }

    private enum class EntryType(val label: String) {
        DIRECTORY("directory"),
        FILE("file")
    }
}
