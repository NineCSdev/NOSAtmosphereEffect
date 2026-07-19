package com.app.nosatmosphereeffect.activity

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.core.view.WindowCompat
import androidx.exifinterface.media.ExifInterface
import com.app.nosatmosphereeffect.helper.SystemColorSyncPreferences
import com.app.nosatmosphereeffect.helper.PlaylistModeManager
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import com.app.nosatmosphereeffect.service.AtmosphereService
import com.app.nosatmosphereeffect.service.BlurToSharpService
import com.app.nosatmosphereeffect.service.ColorFillReverseService
import com.app.nosatmosphereeffect.service.ColorFillService
import com.app.nosatmosphereeffect.service.FrostedReverseService
import com.app.nosatmosphereeffect.service.FrostedService
import com.app.nosatmosphereeffect.service.HalftoneReverseService
import com.app.nosatmosphereeffect.service.HalftoneService
import com.app.nosatmosphereeffect.service.NeonReverseService
import com.app.nosatmosphereeffect.service.NeonService
import com.app.nosatmosphereeffect.ui.screens.PlaylistEditorScreen
import com.app.nosatmosphereeffect.ui.screens.PlaylistEntry
import com.app.nosatmosphereeffect.ui.screens.ProcessingOverlay
import com.app.nosatmosphereeffect.ui.screens.SimpleConfirmDialog
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

class PlaylistEditorActivity : ComponentActivity() {

    private val playlistItems = mutableStateListOf<PlaylistItem>()
    private var effectId: String = "ORIGINAL"
    private var editingPosition = -1

    // True when we entered via "Edit Playlist" on an already-applied playlist.
    // In that case applying keeps the user's advanced settings (see applyPlaylist);
    // a brand-new playlist starts fresh.
    private var isEditExisting = false

    private var showApplyConfirm by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)

    data class PlaylistItem(
        val originalUri: Uri,
        var isEdited: Boolean = false,
        var editedFilePath: String? = null,
        var matrixState: FloatArray? = null,
        var fitMode: String = WallpaperFitHelper.MODE_FILL,
        var fillMode: String = WallpaperFitHelper.FILL_BLACK
    )

    private val pickMultipleImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                uris.forEach { playlistItems.add(PlaylistItem(it)) }
                Toast.makeText(this, "${uris.size} images added", Toast.LENGTH_SHORT).show()
            }
        }

    private val editImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val resultUriString = result.data?.getStringExtra("CROPPED_IMAGE_PATH")
                val matrixState = result.data?.getFloatArrayExtra("MATRIX_STATE")
                val fitMode = result.data?.getStringExtra("FIT_MODE")
                val fillMode = result.data?.getStringExtra("FILL_MODE")

                if (resultUriString != null && editingPosition != -1 && editingPosition < playlistItems.size) {
                    // Replace with a copy so Compose observes the change and recomposes.
                    playlistItems[editingPosition] = playlistItems[editingPosition].copy(
                        isEdited = true,
                        editedFilePath = resultUriString,
                        matrixState = matrixState,
                        fitMode = fitMode ?: WallpaperFitHelper.MODE_FILL,
                        fillMode = fillMode ?: WallpaperFitHelper.FILL_BLACK
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        effectId = intent.getStringExtra("EFFECT_ID") ?: "ORIGINAL"

        isEditExisting = intent.getBooleanExtra("EDIT_EXISTING", false)
        if (isEditExisting) {
            loadExistingPlaylist()
        } else {
            val uris = intent.getParcelableArrayListExtra("IMAGE_URIS", Uri::class.java)
            uris?.forEach { playlistItems.add(PlaylistItem(it)) }
        }

        if (savedInstanceState != null) {
            editingPosition = savedInstanceState.getInt("EDITING_POS", -1)
        }

        setContent {
            AtmoEngineTheme {
                PlaylistEditorScreen(
                    effectId = effectId,
                    entries = playlistItems.map { item ->
                        val displayUri = if (item.isEdited && item.editedFilePath != null) {
                            Uri.parse("file://${item.editedFilePath}")
                        } else {
                            item.originalUri
                        }
                        PlaylistEntry(displayUri = displayUri, isEdited = item.isEdited)
                    },
                    onEditItem = { pos ->
                        editingPosition = pos
                        launchEditActivity(playlistItems[pos])
                    },
                    onDeleteItem = { pos ->
                        if (pos in playlistItems.indices) playlistItems.removeAt(pos)
                    },
                    onAddMore = { pickMultipleImages.launch("image/*") },
                    onApply = { showApplyDialog() },
                    onBack = { finish() }
                )

                if (showApplyConfirm) {
                    SimpleConfirmDialog(
                        title = "Apply Wallpaper",
                        message = "On the next screen, please select:\n\n" +
                            "Set Wallpaper › Home Screen and Lock Screen.\n\n" +
                            "(This ensures the lock-screen effect works correctly.)",
                        confirmLabel = "Set Wallpaper",
                        dismissLabel = "Cancel",
                        onConfirm = {
                            showApplyConfirm = false
                            applyFromDialog()
                        },
                        onDismiss = { showApplyConfirm = false }
                    )
                }

                if (isProcessing) {
                    ProcessingOverlay(message = "Processing playlist…")
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("EDITING_POS", editingPosition)
    }

    private fun launchEditActivity(item: PlaylistItem) {
        val intent = Intent(this, MultiImageCropActivity::class.java)
        intent.data = item.originalUri
        if (item.matrixState != null) {
            intent.putExtra("MATRIX_STATE", item.matrixState)
        }
        // Restore this image's previously chosen fit mode.
        intent.putExtra("INITIAL_FIT_MODE", item.fitMode)
        intent.putExtra("INITIAL_FILL_MODE", item.fillMode)
        editImageLauncher.launch(intent)
    }

    private fun applyPlaylist() {
        isProcessing = true

        Thread {
            try {
                // 1. USE A TEMPORARY FOLDER INSTEAD OF DELETING THE ACTIVE ONE YET
                val tempDir = File(filesDir, "playlist_temp")
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()

                val tempOriginalsDir = File(filesDir, "playlist_originals_temp")
                if (tempOriginalsDir.exists()) tempOriginalsDir.deleteRecursively()
                tempOriginalsDir.mkdirs()

                // 2. CLEANUP STALE SINGLE-IMAGE DATA (Important!)
                val nextWallpaper = File(filesDir, "next_wallpaper.jpg")
                if (nextWallpaper.exists()) nextWallpaper.delete()
                WallpaperFitHelper.deleteNextSource(filesDir)

                val metaArray = JSONArray()

                // 3. Process each item
                playlistItems.forEachIndexed { index, item ->
                    val destFile = File(tempDir, "wallpaper_$index.jpg")
                    val origFile = File(tempOriginalsDir, "original_$index.jpg")

                    try {
                        contentResolver.openInputStream(item.originalUri)?.use { input ->
                            FileOutputStream(origFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (item.isEdited && item.editedFilePath != null) {
                        val srcEdited = File(item.editedFilePath!!)
                        if (srcEdited.exists() && srcEdited.absolutePath != destFile.absolutePath) {
                            srcEdited.copyTo(destFile, overwrite = true)
                        }
                    } else {
                        val bitmap = decodeCenterCropBitmap(item.originalUri)
                        if (bitmap != null) {
                            FileOutputStream(destFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                            }
                        }
                    }

                    val metaObj = JSONObject()
                    metaObj.put("original", "original_$index.jpg")
                    metaObj.put("isEdited", item.isEdited)
                    metaObj.put("fitMode", item.fitMode)
                    metaObj.put("fillMode", item.fillMode)
                    if (item.matrixState != null) {
                        val matrixJson = JSONArray()
                        item.matrixState!!.forEach { matrixJson.put(it.toDouble()) }
                        metaObj.put("matrix", matrixJson)
                    }
                    metaArray.put(metaObj)
                }

                File(tempDir, "metadata.json").writeText(metaArray.toString())

                // 4. SWAP THE FOLDERS SAFELY
                val playlistDir = File(filesDir, "playlist")
                if (playlistDir.exists()) playlistDir.deleteRecursively()
                tempDir.renameTo(playlistDir)

                val originalsDir = File(filesDir, "playlist_originals")
                if (originalsDir.exists()) originalsDir.deleteRecursively()
                tempOriginalsDir.renameTo(originalsDir)
                PlaylistModeManager.clearThemeCollections(this@PlaylistEditorActivity)

                // 5. Set Main Wallpaper
                val firstFile = File(playlistDir, "wallpaper_0.jpg")
                val activeWallpaper = File(filesDir, "wallpaper.jpg")
                if (firstFile.exists()) {
                    firstFile.copyTo(activeWallpaper, overwrite = true)
                    // Un-cropped original kept for foldable re-fit of Fill images.
                    WallpaperFitHelper.stageActiveSourceFromPlaylist(filesDir, firstFile.name)
                    // Each image's chosen fit mode is baked into its wallpaper_N.jpg, so
                    // the renderer just displays it (Fill). The per-image mode lives in
                    // metadata.json so re-editing can restore the chooser + preview.
                    WallpaperFitHelper.setActiveModes(
                        this@PlaylistEditorActivity,
                        WallpaperFitHelper.MODE_FILL,
                        WallpaperFitHelper.FILL_BLACK
                    )
                }

                // 6. RESET PREFERENCES FOR A FRESH START.
                //
                // Editing an EXISTING playlist only tweaks the images — the effect and
                // mode are unchanged — so we KEEP the user's advanced settings (dim/
                // brightness, blur, colour sync, all fine-tuning in app_prefs) and their
                // rotation interval. Every other entry point (new playlist, changing
                // effect, single <-> playlist) still wipes everything, because those
                // genuinely begin a new configuration.
                //
                // Runtime rotation pointers are always re-seeded below because the
                // underlying wallpaper_N.jpg files have just been rewritten.
                val wallpaperPrefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                val preservedRotation =
                    if (isEditExisting) {
                        wallpaperPrefs.getLong("rotation_interval_minutes", 0L).coerceAtLeast(0L)
                    } else {
                        null
                    }

                SystemColorSyncPreferences.isEnabled(this@PlaylistEditorActivity)
                wallpaperPrefs.edit().clear().apply()
                if (!isEditExisting) {
                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                }
                if (preservedRotation != null) {
                    wallpaperPrefs.edit().putLong("rotation_interval_minutes", preservedRotation).apply()
                }
                PlaylistModeManager.setMode(
                    this@PlaylistEditorActivity,
                    PlaylistModeManager.MODE_STANDARD
                )

                wallpaperPrefs.edit()
                    .putString("last_playlist_image", "wallpaper_0.jpg")
                    .putLong("last_rotation_timestamp", System.currentTimeMillis())
                    .apply()

                runOnUiThread {
                    isProcessing = false
                    Toast.makeText(this, "Setup complete! Now lock and unlock the screen to activate.", Toast.LENGTH_LONG).show()
                    val intent = Intent("com.app.nosatmosphereeffect.RELOAD_WALLPAPER")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    // Proceed to "Reapply" by showing the preview screen
                    activateService()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isProcessing = false
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun decodeCenterCropBitmap(uri: Uri): Bitmap? {
        val metrics = windowManager.currentWindowMetrics.bounds
        val reqW = metrics.width()
        val reqH = metrics.height()

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        options.inSampleSize = calculateInSampleSize(options, reqW, reqH)
        options.inJustDecodeBounds = false

        var bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        bitmap = handleExifRotation(this, uri, bitmap)

        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val screenRatio = reqW.toFloat() / reqH.toFloat()

        val matrix = Matrix()
        val scale: Float
        if (bitmapRatio > screenRatio) {
            scale = reqH.toFloat() / bitmap.height.toFloat()
        } else {
            scale = reqW.toFloat() / bitmap.width.toFloat()
        }

        matrix.setScale(scale, scale)
        val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val x = max(0, (scaledBitmap.width - reqW) / 2)
        val y = max(0, (scaledBitmap.height - reqH) / 2)
        val finalW = min(reqW, scaledBitmap.width - x)
        val finalH = min(reqH, scaledBitmap.height - y)

        return Bitmap.createBitmap(scaledBitmap, x, y, finalW, finalH)
    }

    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return bitmap
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

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun activateService() {
        try {
            val serviceClass = when (effectId) {
                "ORIGINAL" -> AtmosphereService::class.java
                "REVERSE" -> BlurToSharpService::class.java
                "FROSTED" -> FrostedService::class.java
                "FROSTED_REVERSE" -> FrostedReverseService::class.java
                "HALFTONE" -> HalftoneService::class.java
                "HALFTONE_REVERSE" -> HalftoneReverseService::class.java
                "COLORFILL" -> ColorFillService::class.java
                "COLORFILL_REVERSE" -> ColorFillReverseService::class.java
                "NEON" -> NeonService::class.java
                "NEON_REVERSE" -> NeonReverseService::class.java
                else -> AtmosphereService::class.java
            }
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, serviceClass))
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } finally {
            finish()
        }
    }

    private fun showApplyDialog() {
        showApplyConfirm = true
    }

    private fun loadExistingPlaylist() {
        val playlistDir = File(filesDir, "playlist")
        val originalsDir = File(filesDir, "playlist_originals")
        val metaFile = File(playlistDir, "metadata.json")

        if (metaFile.exists()) {
            try {
                val jsonStr = metaFile.readText()
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val origName = obj.getString("original")
                    val isEdited = obj.getBoolean("isEdited")

                    val origFile = File(originalsDir, origName)
                    val originalUri = Uri.parse("file://${origFile.absolutePath}")

                    var editedPath: String? = null
                    if (isEdited) {
                        editedPath = File(playlistDir, "wallpaper_$i.jpg").absolutePath
                    }

                    var matrixState: FloatArray? = null
                    if (obj.has("matrix")) {
                        val matrixArray = obj.getJSONArray("matrix")
                        matrixState = FloatArray(matrixArray.length())
                        for (j in 0 until matrixArray.length()) {
                            matrixState[j] = matrixArray.getDouble(j).toFloat()
                        }
                    }

                    val fitMode = obj.optString("fitMode", WallpaperFitHelper.MODE_FILL)
                        .ifEmpty { WallpaperFitHelper.MODE_FILL }
                    val fillMode = obj.optString("fillMode", WallpaperFitHelper.FILL_BLACK)
                        .ifEmpty { WallpaperFitHelper.FILL_BLACK }

                    playlistItems.add(PlaylistItem(originalUri, isEdited, editedPath, matrixState, fitMode, fillMode))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Fallback for older playlists created before this update
            val files = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }
            if (!files.isNullOrEmpty()) {
                files.sortBy { it.nameWithoutExtension.substringAfter('_').toIntOrNull() ?: 0 }
                files.forEach { file ->
                    playlistItems.add(PlaylistItem(Uri.parse("file://${file.absolutePath}")))
                }
            }
        }
    }

    private fun applyFromDialog() {
        if (playlistItems.isEmpty()) {
            Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show()
        } else {
            applyPlaylist()
        }
    }
}
