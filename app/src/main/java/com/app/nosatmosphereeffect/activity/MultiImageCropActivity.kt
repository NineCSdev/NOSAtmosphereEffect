package com.app.nosatmosphereeffect.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.FitChooser
import com.app.nosatmosphereeffect.helper.TouchImageView
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MultiImageCropActivity : AppCompatActivity() {

    private lateinit var cropView: TouchImageView
    private var sourceUri: Uri? = null
    private var fitSelection: FitChooser.Selection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it full screen like CropActivity
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val windowController = WindowCompat.getInsetsController(window, window.decorView)
        windowController.hide(WindowInsetsCompat.Type.systemBars())
        windowController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_crop_multi)

        cropView = findViewById(R.id.cropImageView)
        val btnDone = findViewById<Button>(R.id.btnSaveCrop) // Ensure ID matches layout

        sourceUri = intent.data
        val savedMatrix = intent.getFloatArrayExtra("MATRIX_STATE") // Receive State

        // Restore this image's previously chosen fit mode (per-image, not global).
        val initialFit = intent.getStringExtra("INITIAL_FIT_MODE") ?: WallpaperFitHelper.MODE_FILL
        val initialFill = intent.getStringExtra("INITIAL_FILL_MODE") ?: WallpaperFitHelper.FILL_BLACK
        fitSelection = FitChooser.attach(this, initialFit, initialFill)

        if (sourceUri != null) {
            loadImage(sourceUri!!, savedMatrix)
        } else {
            Toast.makeText(this, "No Image Data Found", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnDone.setOnClickListener {
            val croppedBitmap = cropView.getCroppedBitmap()
                // Get the current zoom state to save it
                val currentMatrix = cropView.getCurrentMatrixValues()
                saveAndReturnResult(croppedBitmap, currentMatrix)
        }
    }

    private fun loadImage(uri: Uri, savedMatrix: FloatArray?) {
        // Decode on a background thread with downsampling so large originals
        // don't blow the memory budget (full-res decode here used to crash).
        Thread {
            val rotatedBitmap = decodeSampledBitmapFromUri(this, uri, 4096, 4096)
            runOnUiThread {
                if (rotatedBitmap != null) {
                    // KEY FIX: Use setInitialImage instead of setImageBitmap
                    // This initializes bounds and restores zoom state if provided
                    cropView.setInitialImage(rotatedBitmap, savedMatrix)
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
    }

    // Memory-safe loader mirroring CropActivity: scales huge photos down before decode.
    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        var inputStream: InputStream? = null
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            inputStream = context.contentResolver.openInputStream(uri)
            val rawBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (rawBitmap == null) return null
            return handleExifRotation(uri, rawBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { inputStream?.close() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        val maxImageDimension = kotlin.math.max(height, width)
        val maxTextureSize = kotlin.math.min(reqWidth, reqHeight)
        if (maxImageDimension > maxTextureSize) {
            val factor = maxImageDimension.toFloat() / maxTextureSize.toFloat()
            while (inSampleSize < factor) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun saveAndReturnResult(bitmap: Bitmap, matrixValues: FloatArray) {
        try {
            val filename = "cropped_playlist_${System.currentTimeMillis()}.jpg"
            val destFile = File(cacheDir, filename)

            val out = FileOutputStream(destFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            val resultIntent = Intent()
            resultIntent.putExtra("CROPPED_IMAGE_PATH", destFile.absolutePath)
            // Return the Matrix State so we can zoom out later
            resultIntent.putExtra("MATRIX_STATE", matrixValues)
            // Return this image's chosen fit mode so the playlist can store it per-image.
            resultIntent.putExtra("FIT_MODE", fitSelection?.fitMode ?: WallpaperFitHelper.MODE_FILL)
            resultIntent.putExtra("FILL_MODE", fitSelection?.fillMode ?: WallpaperFitHelper.FILL_BLACK)

            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val input = contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(input)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            input.close()

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(rotation) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            return bitmap
        }
    }
}