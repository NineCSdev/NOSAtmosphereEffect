package com.app.nosatmosphereeffect.image

import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.storage.FileTransactions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal object BitmapStore {
    private const val TAG = "BitmapStore"

    @Throws(IOException::class)
    fun writeJpegAtomically(bitmap: Bitmap, destination: File, quality: Int) {
        require(quality in 0..100) { "JPEG quality must be between 0 and 100" }

        val directory = destination.parentFile
            ?: throw IOException("Destination has no parent directory")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create ${directory.absolutePath}")
        }

        val temporary = File.createTempFile("${destination.name}.", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw IOException("Bitmap encoder rejected the image")
                }
                output.flush()
                output.fd.sync()
            }

            FileTransactions.moveReplacing(temporary, destination)
        } finally {
            try {
                FileTransactions.deleteRecursively(temporary)
            } catch (cleanupError: IOException) {
                Log.w(TAG, "Could not remove temporary image ${temporary.absolutePath}", cleanupError)
            }
        }
    }
}
