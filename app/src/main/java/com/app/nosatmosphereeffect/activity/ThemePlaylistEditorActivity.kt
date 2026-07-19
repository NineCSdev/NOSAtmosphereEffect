package com.app.nosatmosphereeffect.activity

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.exifinterface.media.ExifInterface
import com.app.nosatmosphereeffect.helper.PlaylistModeManager
import com.app.nosatmosphereeffect.helper.SystemColorSyncPreferences
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
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

class ThemePlaylistEditorActivity : ComponentActivity() {
    private val lightItems = mutableStateListOf<ThemePlaylistItem>()
    private val darkItems = mutableStateListOf<ThemePlaylistItem>()
    private var selectedPlaylist by mutableIntStateOf(0)
    private var effectId = "ORIGINAL"
    private var editingPosition = -1
    private var editingPlaylist = 0
    private var isEditExisting = false
    private var showApplyConfirm by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)

    private data class ThemePlaylistItem(
        val originalUri: Uri,
        val isEdited: Boolean = false,
        val editedFilePath: String? = null,
        val matrixState: FloatArray? = null,
        val fitMode: String = WallpaperFitHelper.MODE_FILL,
        val fillMode: String = WallpaperFitHelper.FILL_BLACK
    )

    private val pickMultipleImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                val target = itemsFor(selectedPlaylist)
                uris.forEach { uri -> target.add(ThemePlaylistItem(uri)) }
                Toast.makeText(
                    this,
                    "${uris.size} ${themeLabel(selectedPlaylist).lowercase()} images added",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val editImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val target = itemsFor(editingPlaylist)
            if (editingPosition !in target.indices) return@registerForActivityResult
            val path = result.data?.getStringExtra("CROPPED_IMAGE_PATH") ?: return@registerForActivityResult
            target[editingPosition] = target[editingPosition].copy(
                isEdited = true,
                editedFilePath = path,
                matrixState = result.data?.getFloatArrayExtra("MATRIX_STATE"),
                fitMode = result.data?.getStringExtra("FIT_MODE")
                    ?: WallpaperFitHelper.MODE_FILL,
                fillMode = result.data?.getStringExtra("FILL_MODE")
                    ?: WallpaperFitHelper.FILL_BLACK
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        effectId = intent.getStringExtra("EFFECT_ID") ?: "ORIGINAL"
        isEditExisting = intent.getBooleanExtra("EDIT_EXISTING", false)
        if (isEditExisting) loadExistingPlaylists()
        if (savedInstanceState != null) {
            selectedPlaylist = savedInstanceState.getInt("SELECTED_PLAYLIST", 0)
            editingPosition = savedInstanceState.getInt("EDITING_POSITION", -1)
            editingPlaylist = savedInstanceState.getInt("EDITING_PLAYLIST", 0)
        }

        setContent {
            AtmoEngineTheme {
                val activeItems = itemsFor(selectedPlaylist)
                PlaylistEditorScreen(
                    title = "Theme playlists",
                    effectId = effectId,
                    entries = activeItems.map { item ->
                        PlaylistEntry(
                            displayUri = item.editedFilePath?.takeIf { item.isEdited }
                                ?.let { Uri.fromFile(File(it)) }
                                ?: item.originalUri,
                            isEdited = item.isEdited
                        )
                    },
                    playlistTabs = listOf("Light", "Dark"),
                    playlistCounts = listOf(lightItems.size, darkItems.size),
                    selectedPlaylist = selectedPlaylist,
                    onPlaylistSelected = { selectedPlaylist = it },
                    applyLabel = "Set playlists",
                    applyEnabled = lightItems.isNotEmpty() && darkItems.isNotEmpty(),
                    onEditItem = { position -> launchEditor(position) },
                    onDeleteItem = { position ->
                        if (position in activeItems.indices) activeItems.removeAt(position)
                    },
                    onAddMore = { pickMultipleImages.launch("image/*") },
                    onApply = { showApplyConfirm = true },
                    onBack = { finish() }
                )

                if (showApplyConfirm) {
                    SimpleConfirmDialog(
                        title = "Apply theme playlists",
                        message = "On the next screen, select:\n\n" +
                            "Set Wallpaper › Home Screen and Lock Screen.",
                        confirmLabel = "Set wallpaper",
                        dismissLabel = "Cancel",
                        onConfirm = {
                            showApplyConfirm = false
                            applyPlaylists()
                        },
                        onDismiss = { showApplyConfirm = false }
                    )
                }
                if (isProcessing) {
                    ProcessingOverlay(message = "Preparing theme playlists…")
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SELECTED_PLAYLIST", selectedPlaylist)
        outState.putInt("EDITING_POSITION", editingPosition)
        outState.putInt("EDITING_PLAYLIST", editingPlaylist)
    }

    private fun launchEditor(position: Int) {
        val target = itemsFor(selectedPlaylist)
        val item = target.getOrNull(position) ?: return
        editingPosition = position
        editingPlaylist = selectedPlaylist
        editImageLauncher.launch(
            Intent(this, MultiImageCropActivity::class.java).apply {
                data = item.originalUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                item.matrixState?.let { putExtra("MATRIX_STATE", it) }
                putExtra("INITIAL_FIT_MODE", item.fitMode)
                putExtra("INITIAL_FILL_MODE", item.fillMode)
            }
        )
    }

    private fun applyPlaylists() {
        if (lightItems.isEmpty() || darkItems.isEmpty()) {
            Toast.makeText(this, "Add at least one image to each playlist", Toast.LENGTH_SHORT).show()
            return
        }
        isProcessing = true
        Thread {
            try {
                val lightTemp = writeCollection(
                    items = lightItems,
                    playlistTempName = "playlist_light_temp",
                    originalsTempName = "playlist_light_originals_temp"
                )
                val darkTemp = writeCollection(
                    items = darkItems,
                    playlistTempName = "playlist_dark_temp",
                    originalsTempName = "playlist_dark_originals_temp"
                )

                swapDirectory(lightTemp.first, File(filesDir, PlaylistModeManager.LIGHT_PLAYLIST_DIR))
                swapDirectory(lightTemp.second, File(filesDir, PlaylistModeManager.LIGHT_ORIGINALS_DIR))
                swapDirectory(darkTemp.first, File(filesDir, PlaylistModeManager.DARK_PLAYLIST_DIR))
                swapDirectory(darkTemp.second, File(filesDir, PlaylistModeManager.DARK_ORIGINALS_DIR))
                PlaylistModeManager.clearStandardCollections(this)

                File(filesDir, WallpaperFitHelper.NEXT_WALLPAPER_FILE).delete()
                WallpaperFitHelper.deleteNextSource(filesDir)

                val isNightMode = PlaylistModeManager.currentNightMode(this)
                val activeDir = if (isNightMode) {
                    PlaylistModeManager.darkPlaylistDir(this)
                } else {
                    PlaylistModeManager.lightPlaylistDir(this)
                }
                val originalsName = if (isNightMode) {
                    PlaylistModeManager.DARK_ORIGINALS_DIR
                } else {
                    PlaylistModeManager.LIGHT_ORIGINALS_DIR
                }
                val firstFile = File(activeDir, "wallpaper_0.jpg")
                check(firstFile.isFile) { "The active theme playlist has no wallpaper" }
                firstFile.copyTo(
                    File(filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE),
                    overwrite = true
                )
                WallpaperFitHelper.stageActiveSourceFromPlaylist(
                    filesDir,
                    firstFile.name,
                    originalsName
                )
                WallpaperFitHelper.setActiveModes(
                    this,
                    WallpaperFitHelper.MODE_FILL,
                    WallpaperFitHelper.FILL_BLACK
                )
                WallpaperFitHelper.setNextModes(
                    this,
                    WallpaperFitHelper.MODE_FILL,
                    WallpaperFitHelper.FILL_BLACK
                )

                val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                val preservedInterval = if (isEditExisting) {
                    prefs.getLong("rotation_interval_minutes", 0L).coerceAtLeast(0L)
                } else {
                    0L
                }
                SystemColorSyncPreferences.isEnabled(this)
                prefs.edit { clear() }
                if (!isEditExisting) {
                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit { clear() }
                }
                PlaylistModeManager.setMode(this, PlaylistModeManager.MODE_THEME)
                prefs.edit {
                    putLong("rotation_interval_minutes", preservedInterval)
                    putLong("last_rotation_timestamp", System.currentTimeMillis())
                    putInt(PlaylistModeManager.KEY_ACTIVE_THEME, if (isNightMode) 1 else 0)
                    putString("last_playlist_image_light", "wallpaper_0.jpg")
                    putString("last_playlist_image_dark", "wallpaper_0.jpg")
                }

                runOnUiThread {
                    isProcessing = false
                    sendBroadcast(
                        Intent("com.app.nosatmosphereeffect.RELOAD_WALLPAPER").setPackage(packageName)
                    )
                    Toast.makeText(
                        this,
                        "Theme playlists are ready",
                        Toast.LENGTH_LONG
                    ).show()
                    activateService()
                }
            } catch (failure: Throwable) {
                runOnUiThread {
                    isProcessing = false
                    Toast.makeText(
                        this,
                        "Could not prepare playlists: ${failure.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun writeCollection(
        items: List<ThemePlaylistItem>,
        playlistTempName: String,
        originalsTempName: String
    ): Pair<File, File> {
        val playlistDir = File(filesDir, playlistTempName).apply {
            deleteRecursively()
            mkdirs()
        }
        val originalsDir = File(filesDir, originalsTempName).apply {
            deleteRecursively()
            mkdirs()
        }
        val metadata = JSONArray()
        items.forEachIndexed { index, item ->
            val destination = File(playlistDir, "wallpaper_$index.jpg")
            copyUri(item.originalUri, File(originalsDir, "original_$index.jpg"))
            val editedFile = item.editedFilePath?.let(::File)
            if (item.isEdited && editedFile?.isFile == true) {
                editedFile.copyTo(destination, overwrite = true)
            } else {
                val bitmap = requireNotNull(decodeCenterCropBitmap(item.originalUri)) {
                    "Image ${index + 1} could not be decoded"
                }
                FileOutputStream(destination).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                }
                bitmap.recycle()
            }
            metadata.put(JSONObject().apply {
                put("original", "original_$index.jpg")
                put("isEdited", item.isEdited)
                put("fitMode", item.fitMode)
                put("fillMode", item.fillMode)
                item.matrixState?.let { matrix ->
                    put("matrix", JSONArray().apply {
                        matrix.forEach { value -> put(value.toDouble()) }
                    })
                }
            })
        }
        File(playlistDir, "metadata.json").writeText(metadata.toString())
        return playlistDir to originalsDir
    }

    private fun swapDirectory(temp: File, destination: File) {
        destination.deleteRecursively()
        if (!temp.renameTo(destination)) {
            check(temp.copyRecursively(destination, overwrite = true)) {
                "Could not store ${destination.name}"
            }
            temp.deleteRecursively()
        }
    }

    private fun loadExistingPlaylists() {
        loadCollection(
            PlaylistModeManager.lightPlaylistDir(this),
            File(filesDir, PlaylistModeManager.LIGHT_ORIGINALS_DIR),
            lightItems
        )
        loadCollection(
            PlaylistModeManager.darkPlaylistDir(this),
            File(filesDir, PlaylistModeManager.DARK_ORIGINALS_DIR),
            darkItems
        )
    }

    private fun loadCollection(
        playlistDir: File,
        originalsDir: File,
        destination: MutableList<ThemePlaylistItem>
    ) {
        val metadataFile = File(playlistDir, "metadata.json")
        if (!metadataFile.isFile) {
            PlaylistModeManager.imageFiles(playlistDir).forEach { file ->
                destination += ThemePlaylistItem(Uri.fromFile(file))
            }
            return
        }
        try {
            val metadata = JSONArray(metadataFile.readText())
            for (index in 0 until metadata.length()) {
                val item = metadata.getJSONObject(index)
                val original = File(
                    originalsDir,
                    item.optString("original", "original_$index.jpg")
                ).takeIf(File::isFile) ?: File(playlistDir, "wallpaper_$index.jpg")
                val isEdited = item.optBoolean("isEdited", false)
                val matrix = item.optJSONArray("matrix")?.let { values ->
                    FloatArray(values.length()) { position -> values.optDouble(position).toFloat() }
                }
                destination += ThemePlaylistItem(
                    originalUri = Uri.fromFile(original),
                    isEdited = isEdited,
                    editedFilePath = if (isEdited) {
                        File(playlistDir, "wallpaper_$index.jpg").absolutePath
                    } else {
                        null
                    },
                    matrixState = matrix,
                    fitMode = item.optString("fitMode", WallpaperFitHelper.MODE_FILL)
                        .ifBlank { WallpaperFitHelper.MODE_FILL },
                    fillMode = item.optString("fillMode", WallpaperFitHelper.FILL_BLACK)
                        .ifBlank { WallpaperFitHelper.FILL_BLACK }
                )
            }
        } catch (failure: Throwable) {
            failure.printStackTrace()
            destination.clear()
            PlaylistModeManager.imageFiles(playlistDir).forEach { file ->
                destination += ThemePlaylistItem(Uri.fromFile(file))
            }
        }
    }

    private fun decodeCenterCropBitmap(uri: Uri): Bitmap? {
        val bounds = windowManager.currentWindowMetrics.bounds
        val requestedWidth = bounds.width()
        val requestedHeight = bounds.height()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openUri(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = calculateInSampleSize(options, requestedWidth, requestedHeight)
        options.inJustDecodeBounds = false
        var bitmap = openUri(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        bitmap = applyExifRotation(uri, bitmap)

        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val screenRatio = requestedWidth.toFloat() / requestedHeight.toFloat()
        val scale = if (bitmapRatio > screenRatio) {
            requestedHeight.toFloat() / bitmap.height
        } else {
            requestedWidth.toFloat() / bitmap.width
        }
        val scaled = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { setScale(scale, scale) },
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        val x = max(0, (scaled.width - requestedWidth) / 2)
        val y = max(0, (scaled.height - requestedHeight) / 2)
        val result = Bitmap.createBitmap(
            scaled,
            x,
            y,
            min(requestedWidth, scaled.width - x),
            min(requestedHeight, scaled.height - y)
        )
        if (result !== scaled) scaled.recycle()
        return result
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = openUri(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: return bitmap
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees) },
                true
            ).also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        requestedWidth: Int,
        requestedHeight: Int
    ): Int {
        var sample = 1
        val halfHeight = options.outHeight / 2
        val halfWidth = options.outWidth / 2
        while (halfHeight / sample >= requestedHeight && halfWidth / sample >= requestedWidth) {
            sample *= 2
        }
        return sample
    }

    private fun copyUri(uri: Uri, destination: File) {
        val input = requireNotNull(openUri(uri)) { "Could not open an image" }
        input.use { source -> FileOutputStream(destination).use(source::copyTo) }
    }

    private fun openUri(uri: Uri): InputStream? {
        return if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path -> File(path).inputStream() }
        } else {
            contentResolver.openInputStream(uri)
        }
    }

    private fun activateService() {
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
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, serviceClass)
                )
            )
        } catch (_: Throwable) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } finally {
            finish()
        }
    }

    private fun itemsFor(index: Int) = if (index == 0) lightItems else darkItems

    private fun themeLabel(index: Int) = if (index == 0) "Light" else "Dark"
}
