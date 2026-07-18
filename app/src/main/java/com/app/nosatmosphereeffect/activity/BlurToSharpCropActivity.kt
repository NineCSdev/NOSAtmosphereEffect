package com.app.nosatmosphereeffect.activity

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import com.app.nosatmosphereeffect.helper.SystemColorSyncPreferences
import com.app.nosatmosphereeffect.helper.TouchImageView
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import com.app.nosatmosphereeffect.service.BlurToSharpService
import com.app.nosatmosphereeffect.service.ColorFillReverseService
import com.app.nosatmosphereeffect.service.FrostedReverseService
import com.app.nosatmosphereeffect.service.HalftoneReverseService
import com.app.nosatmosphereeffect.service.NeonReverseService
import com.app.nosatmosphereeffect.ui.screens.CropController
import com.app.nosatmosphereeffect.ui.screens.CropScreen
import com.app.nosatmosphereeffect.ui.screens.WallpaperPreviewDialog
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class BlurToSharpCropActivity : ComponentActivity() {
    private var effectId: String = "REVERSE" // Default
    private var sourceBitmap: Bitmap? = null // Un-cropped source, saved for fit modes
    private val controller = CropController()

    private var pendingBitmap: Bitmap? = null
    private var showApplyConfirm by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        val windowController = WindowCompat.getInsetsController(window, window.decorView)
        windowController.isAppearanceLightStatusBars = false
        windowController.isAppearanceLightNavigationBars = false
        windowController.hide(WindowInsetsCompat.Type.systemBars())
        windowController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        effectId = intent.getStringExtra("EFFECT_ID") ?: "REVERSE"

        val uri = intent.data ?: run {
            Toast.makeText(this, "No Image Data Found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AtmoEngineTheme {
                CropScreen(
                    controller = controller,
                    buttonLabel = "Preview transition",
                    initialFit = WallpaperFitHelper.MODE_FILL,
                    initialFill = WallpaperFitHelper.FILL_BLACK,
                    onViewCreated = { loadImageInto(uri) },
                    onFitChanged = { f, fl -> controller.setFitMode(f, fl) },
                    onBack = { finish() },
                    onConfirm = {
                        val cropped = controller.getCroppedBitmap()
                        if (cropped != null) {
                            pendingBitmap = cropped
                            showApplyConfirm = true
                        }
                    }
                )

                if (showApplyConfirm) {
                    pendingBitmap?.let { preview ->
                        WallpaperPreviewDialog(
                        bitmap = preview,
                        effectId = effectId,
                        onConfirm = {
                            showApplyConfirm = false
                            pendingBitmap?.let { applyWallpaper(it) }
                        },
                        onDismiss = {
                            showApplyConfirm = false
                            pendingBitmap?.recycle()
                            pendingBitmap = null
                        }
                        )
                    }
                }
            }
        }
    }

    private fun loadImageInto(uri: Uri) {
        // Use a background thread to load heavy images to prevent UI freeze
        Thread {
            try {
                val correctedBitmap = decodeSampledBitmapFromUri(this, uri, 4096, 4096)
                runOnUiThread {
                    if (correctedBitmap != null) {
                        sourceBitmap = correctedBitmap
                        controller.setInitialImage(correctedBitmap)
                    } else {
                        Toast.makeText(this, "Could not load image format.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
    }

    // --- ROBUST IMAGE LOADER ---
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

            return handleExifRotation(context, uri, rawBitmap)
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return null
        } finally {
            try { inputStream?.close() } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return bitmap

            val exifInterface = ExifInterface(inputStream)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationInDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationInDegrees == 0f) return bitmap

            val matrix = Matrix()
            matrix.postRotate(rotationInDegrees)
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return bitmap
        } finally {
            inputStream?.close()
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

    private fun applyWallpaper(bitmap: Bitmap) {
        Toast.makeText(this, "Applying...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                SystemColorSyncPreferences.isEnabled(this)
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit().clear().apply()

                val playlistDir = File(filesDir, "playlist")
                if (playlistDir.exists()) playlistDir.deleteRecursively()

                val nextWallpaper = File(filesDir, "next_wallpaper.jpg")
                if (nextWallpaper.exists()) nextWallpaper.delete()
                WallpaperFitHelper.deleteNextSource(filesDir)

                saveFixedWallpaper(bitmap)
                WallpaperFitHelper.saveActiveSource(this, sourceBitmap)
                WallpaperFitHelper.setActiveModes(this, WallpaperFitHelper.MODE_FILL, WallpaperFitHelper.FILL_BLACK)
                WallpaperFitHelper.setNextModes(this, WallpaperFitHelper.MODE_FILL, WallpaperFitHelper.FILL_BLACK)

                runOnUiThread {
                    val intent = Intent("com.app.nosatmosphereeffect.RELOAD_WALLPAPER")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    Toast.makeText(this, "Setup complete! Now lock and unlock the screen to activate.", Toast.LENGTH_LONG).show()
                    activateService()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveFixedWallpaper(bitmap: Bitmap) {
        val file = File(filesDir, "wallpaper.jpg")
        if (file.exists()) file.delete()
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
    }

    private fun activateService() {
        try {
            val serviceClass = if (effectId == "FROSTED_REVERSE") {
                FrostedReverseService::class.java
            } else if (effectId == "HALFTONE_REVERSE") {
                HalftoneReverseService::class.java
            } else if (effectId == "COLORFILL_REVERSE") {
                ColorFillReverseService::class.java
            } else if (effectId == "NEON_REVERSE") {
                NeonReverseService::class.java
            } else {
                BlurToSharpService::class.java
            }

            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, serviceClass)
            )
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            startActivity(intent)
        } finally {
            finish()
        }
    }
}
