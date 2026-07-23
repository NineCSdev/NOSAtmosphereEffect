package com.app.nosatmosphereeffect.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

internal object UriFiles {
    private const val TAG = "UriFiles"

    fun open(context: Context, uri: Uri): InputStream {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path ?: throw FileNotFoundException("File URI has no path")
            return File(path).inputStream()
        }
        return context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Unable to open $uri")
    }

    fun copyAtomically(context: Context, uri: Uri, destination: File) {
        val directory = destination.parentFile
            ?: throw FileNotFoundException("Destination has no parent directory")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create ${directory.absolutePath}")
        }

        val temporary = File.createTempFile("${destination.name}.", ".tmp", directory)
        var completed = false
        try {
            open(context, uri).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            FileTransactions.moveReplacing(temporary, destination)
            completed = true
        } finally {
            if (temporary.exists()) {
                try {
                    FileTransactions.deleteRecursively(temporary)
                } catch (cleanupError: IOException) {
                    if (completed) {
                        throw cleanupError
                    } else {
                        Log.w(
                            TAG,
                            "Could not remove temporary copy ${temporary.absolutePath}",
                            cleanupError
                        )
                    }
                } catch (cleanupError: SecurityException) {
                    if (completed) {
                        throw cleanupError
                    } else {
                        Log.w(
                            TAG,
                            "Could not access temporary copy ${temporary.absolutePath}",
                            cleanupError
                        )
                    }
                }
            }
        }
    }
}
