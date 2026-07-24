package com.app.nosatmosphereeffect.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.app.nosatmosphereeffect.helper.AtmosphereGlassPolicy
import com.app.nosatmosphereeffect.helper.GlassEffectPreferences
import com.app.nosatmosphereeffect.helper.GlassEffectSettings
import com.app.nosatmosphereeffect.helper.MatrixStatePolicy
import com.app.nosatmosphereeffect.helper.PlaylistModeManager
import com.app.nosatmosphereeffect.helper.SystemColorSyncPreferences
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import com.app.nosatmosphereeffect.storage.FileTransactions
import com.app.nosatmosphereeffect.storage.PlaylistCollectionStore
import com.app.nosatmosphereeffect.storage.PlaylistImageSource
import com.app.nosatmosphereeffect.storage.SharedPreferencesTransactions
import com.app.nosatmosphereeffect.storage.WallpaperStorageCoordinator
import com.app.nosatmosphereeffect.ui.screens.PlaylistEditorScreen
import com.app.nosatmosphereeffect.ui.screens.PlaylistEntry
import com.app.nosatmosphereeffect.ui.screens.ProcessingOverlay
import com.app.nosatmosphereeffect.ui.screens.SimpleConfirmDialog
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException

private typealias ThemePlaylistItem = PlaylistDraftItem

class ThemePlaylistEditorActivity : ComponentActivity() {
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val draftState: ThemePlaylistDraftState by viewModels()
    private val lightItems get() = draftState.lightItems
    private val darkItems get() = draftState.darkItems
    private var selectedPlaylist by mutableIntStateOf(0)
    private var effectId = "ORIGINAL"
    private var editingPosition = -1
    private var editingPlaylist = 0
    private var isEditExisting = false
    private var showApplyConfirm by mutableStateOf(false)
    private var isProcessing
        get() = draftState.isProcessing
        set(value) {
            draftState.isProcessing = value
        }

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
            val path = result.data?.getStringExtra("CROPPED_IMAGE_PATH") ?: return@registerForActivityResult
            if (editingPosition !in target.indices) {
                deleteCachedEdit(path)
                return@registerForActivityResult
            }
            val previous = target[editingPosition]
            target[editingPosition] = previous.copy(
                isEdited = true,
                editedFilePath = path,
                matrixState = MatrixStatePolicy.copyIfValid(
                    result.data?.getFloatArrayExtra("MATRIX_STATE")
                ),
                fitMode = result.data?.getStringExtra("FIT_MODE")
                    ?: WallpaperFitHelper.MODE_FILL,
                fillMode = result.data?.getStringExtra("FILL_MODE")
                    ?: WallpaperFitHelper.FILL_BLACK
            )
            if (previous.editedFilePath != path) {
                deleteCachedEdit(previous.editedFilePath)
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
        if (savedInstanceState != null) {
            selectedPlaylist = savedInstanceState.getInt("SELECTED_PLAYLIST", 0)
            editingPosition = savedInstanceState.getInt("EDITING_POSITION", -1)
            editingPlaylist = savedInstanceState.getInt("EDITING_PLAYLIST", 0)
        }
        if (!draftState.initialized) {
            val restoredAtmosphereGlass =
                if (savedInstanceState?.containsKey(STATE_ATMOSPHERE_GLASS) == true) {
                    savedInstanceState.getBoolean(STATE_ATMOSPHERE_GLASS)
                } else {
                    null
                }
            draftState.atmosphereGlassEnabled =
                AtmosphereGlassPolicy.resolveEnabled(
                    effectId,
                    restoredAtmosphereGlass
                        ?: if (isEditExisting) readStoredAtmosphereGlass() else false
                )
            val restoredLight = PlaylistDraftStateCodec.decode(
                savedInstanceState?.getParcelableArrayList(
                    STATE_LIGHT_ITEMS,
                    Bundle::class.java
                )
            )
            val restoredDark = PlaylistDraftStateCodec.decode(
                savedInstanceState?.getParcelableArrayList(
                    STATE_DARK_ITEMS,
                    Bundle::class.java
                )
            )
            if (restoredLight != null && restoredDark != null) {
                lightItems.addAll(restoredLight)
                darkItems.addAll(restoredDark)
            } else if (isEditExisting) {
                loadExistingPlaylists()
            }
            draftState.initialized = true
        }

        setContent {
            AtmoEngineTheme {
                BackHandler(enabled = isProcessing) {}
                LaunchedEffect(draftState.applyCompleted, draftState.applyError) {
                    when {
                        draftState.applyCompleted -> showSuccessfulApply()
                        draftState.applyError != null -> {
                            val message = draftState.applyError
                            draftState.applyError = null
                            Toast.makeText(
                                this@ThemePlaylistEditorActivity,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                val activeItems = itemsFor(selectedPlaylist)
                PlaylistEditorScreen(
                    title = "Theme playlists",
                    effectId = effectId,
                    showAtmosphereGlassOption =
                        EffectCatalog.supportsAtmosphereGlass(effectId),
                    atmosphereGlassEnabled = draftState.atmosphereGlassEnabled,
                    onAtmosphereGlassEnabledChange = { enabled ->
                        draftState.atmosphereGlassEnabled =
                            AtmosphereGlassPolicy.resolveEnabled(effectId, enabled)
                    },
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
                        if (position in activeItems.indices) {
                            deleteCachedEdit(activeItems.removeAt(position).editedFilePath)
                        }
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
        outState.putBoolean(
            STATE_ATMOSPHERE_GLASS,
            draftState.atmosphereGlassEnabled
        )
        outState.putParcelableArrayList(
            STATE_LIGHT_ITEMS,
            PlaylistDraftStateCodec.encode(lightItems)
        )
        outState.putParcelableArrayList(
            STATE_DARK_ITEMS,
            PlaylistDraftStateCodec.encode(darkItems)
        )
    }

    override fun onDestroy() {
        if (isChangingConfigurations) {
            ioExecutor.shutdown()
        } else {
            ioExecutor.shutdownNow()
        }
        if (isFinishing && !isChangingConfigurations) {
            lightItems.forEach { item -> deleteCachedEdit(item.editedFilePath) }
            darkItems.forEach { item -> deleteCachedEdit(item.editedFilePath) }
        }
        super.onDestroy()
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

        val lightSnapshot = lightItems.map(::snapshot)
        val darkSnapshot = darkItems.map(::snapshot)
        val atmosphereGlassEnabled = AtmosphereGlassPolicy.resolveEnabled(
            effectId,
            draftState.atmosphereGlassEnabled
        )
        val bounds = windowManager.currentWindowMetrics.bounds
        isProcessing = true
        draftState.applyCompleted = false
        draftState.applyError = null
        ioExecutor.execute {
            try {
                WallpaperStorageCoordinator.runExclusive {
                    val fileTransactions = mutableListOf<FileTransactions.ReplacementTransaction>()
                    val appPreferences =
                        getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
                    val preferenceSnapshots = SharedPreferencesTransactions.snapshot(
                        listOf(
                            appPreferences,
                            getSharedPreferences(WALLPAPER_PREFERENCES, Context.MODE_PRIVATE),
                            getSharedPreferences(
                                WallpaperFitHelper.PREFS_NAME,
                                Context.MODE_PRIVATE
                            )
                        )
                    )
                    val glassSettings = GlassEffectPreferences.read(appPreferences)
                    var preferencesTouched = false
                    try {
                        fileTransactions += persistCollections(
                            lightSnapshot,
                            darkSnapshot,
                            bounds.width(),
                            bounds.height()
                        )

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
                        fileTransactions += PlaylistCollectionStore.beginActivatingFirst(
                            this,
                            activeDir,
                            File(filesDir, originalsName),
                            File(filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE),
                            File(filesDir, WallpaperFitHelper.ACTIVE_SOURCE_FILE)
                        )
                        preferencesTouched = true
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

                        resetPreferences(
                            appPreferences,
                            isNightMode,
                            atmosphereGlassEnabled,
                            glassSettings
                        )
                        FileTransactions.commitAll(fileTransactions)
                    } catch (failure: Exception) {
                        FileTransactions.rollbackAll(fileTransactions, failure)
                        if (preferencesTouched) {
                            SharedPreferencesTransactions.restoreAll(
                                preferenceSnapshots,
                                failure
                            )
                        }
                        throw failure
                    }
                    cleanupObsoleteStandardData()
                }

                runOnUiThread {
                    isProcessing = false
                    draftState.applyCompleted = true
                }
            } catch (error: IOException) {
                reportApplyFailure(
                    "Unable to persist theme playlists",
                    error,
                    "The playlists could not be saved. Check available storage and try again."
                )
            } catch (error: SecurityException) {
                reportApplyFailure(
                    "Theme playlist image permission was rejected",
                    error,
                    "Atmo Engine no longer has permission to read one of the images."
                )
            } catch (error: JSONException) {
                reportApplyFailure(
                    "Unable to create theme playlist metadata",
                    error,
                    "The playlist metadata could not be created."
                )
            } catch (error: RuntimeException) {
                reportApplyFailure(
                    "Unexpected theme playlist apply failure",
                    error,
                    "The playlists could not be prepared."
                )
            }
        }
    }

    private fun snapshot(item: ThemePlaylistItem): ThemePlaylistItem {
        return item.copy(matrixState = item.matrixState?.copyOf())
    }

    private fun persistCollections(
        light: List<ThemePlaylistItem>,
        dark: List<ThemePlaylistItem>,
        width: Int,
        height: Int
    ): FileTransactions.ReplacementTransaction {
        val token = UUID.randomUUID().toString()
        val stagedLight = File(filesDir, ".playlist-light-$token.staged")
        val stagedLightOriginals =
            File(filesDir, ".playlist-light-originals-$token.staged")
        val stagedDark = File(filesDir, ".playlist-dark-$token.staged")
        val stagedDarkOriginals =
            File(filesDir, ".playlist-dark-originals-$token.staged")
        val stagingDirectories = listOf(
            stagedLight,
            stagedLightOriginals,
            stagedDark,
            stagedDarkOriginals
        )

        try {
            PlaylistCollectionStore.stage(
                this,
                light.map(::toSource),
                stagedLight,
                stagedLightOriginals,
                width,
                height
            )
            PlaylistCollectionStore.stage(
                this,
                dark.map(::toSource),
                stagedDark,
                stagedDarkOriginals,
                width,
                height
            )
            return FileTransactions.beginReplacingDirectories(
                listOf(
                    stagedLight to PlaylistModeManager.lightPlaylistDir(this),
                    stagedLightOriginals to File(
                        filesDir,
                        PlaylistModeManager.LIGHT_ORIGINALS_DIR
                    ),
                    stagedDark to PlaylistModeManager.darkPlaylistDir(this),
                    stagedDarkOriginals to File(
                        filesDir,
                        PlaylistModeManager.DARK_ORIGINALS_DIR
                    )
                )
            )
        } catch (failure: Exception) {
            stagingDirectories.forEach { directory ->
                try {
                    FileTransactions.deleteRecursively(directory)
                } catch (cleanupError: Exception) {
                    failure.addSuppressed(cleanupError)
                }
            }
            throw failure
        }
    }

    private fun toSource(item: ThemePlaylistItem) = PlaylistImageSource(
        originalUri = item.originalUri,
        isEdited = item.isEdited,
        editedFilePath = item.editedFilePath,
        matrixState = item.matrixState,
        fitMode = item.fitMode,
        fillMode = item.fillMode
    )

    private fun resetPreferences(
        appPreferences: SharedPreferences,
        isNightMode: Boolean,
        atmosphereGlassEnabled: Boolean,
        glassSettings: GlassEffectSettings
    ) {
        val wallpaperPreferences =
            getSharedPreferences(WALLPAPER_PREFERENCES, Context.MODE_PRIVATE)
        val preservedInterval = if (isEditExisting) {
            wallpaperPreferences.getLong(KEY_ROTATION_INTERVAL, 0L).coerceAtLeast(0L)
        } else {
            0L
        }

        SystemColorSyncPreferences.isEnabled(this)
        val appPreferencesEditor = appPreferences.edit()
        if (!isEditExisting) {
            appPreferencesEditor.clear()
        }
        appPreferencesEditor.putBoolean(
            AtmosphereGlassPolicy.ENABLED_KEY,
            atmosphereGlassEnabled
        )
        if (
            !GlassEffectPreferences.write(appPreferencesEditor, glassSettings)
                .commit()
        ) {
            throw IOException("Could not persist effect preferences")
        }
        if (
            !wallpaperPreferences.edit()
                .clear()
                .putString(PlaylistModeManager.KEY_MODE, PlaylistModeManager.MODE_THEME)
                .putLong(KEY_ROTATION_INTERVAL, preservedInterval)
                .putLong(KEY_LAST_ROTATION, System.currentTimeMillis())
                .putInt(PlaylistModeManager.KEY_ACTIVE_THEME, if (isNightMode) 1 else 0)
                .putString(KEY_LAST_LIGHT_IMAGE, "wallpaper_0.jpg")
                .putString(KEY_LAST_DARK_IMAGE, "wallpaper_0.jpg")
                .commit()
        ) {
            throw IOException("Could not initialize theme playlist preferences")
        }
    }

    private fun reportApplyFailure(
        logMessage: String,
        error: Throwable,
        userMessage: String
    ) {
        Log.e(TAG, logMessage, error)
        runOnUiThread {
            isProcessing = false
            draftState.applyError = userMessage
        }
    }

    private fun showSuccessfulApply() {
        if (!draftState.applyCompleted) return
        draftState.applyCompleted = false
        sendBroadcast(Intent(ACTION_RELOAD_WALLPAPER).setPackage(packageName))
        Toast.makeText(this, "Theme playlists are ready", Toast.LENGTH_LONG).show()
        activateService()
    }

    private fun deleteCachedEdit(path: String?) {
        try {
            PlaylistDraftCache.delete(this, path)
        } catch (error: Exception) {
            Log.w(TAG, "Could not remove a temporary theme-playlist edit", error)
        }
    }

    private fun cleanupObsoleteStandardData() {
        val tasks = listOf<() -> Unit>(
            { PlaylistModeManager.clearStandardCollections(this) },
            {
                Files.deleteIfExists(
                    File(filesDir, WallpaperFitHelper.NEXT_WALLPAPER_FILE).toPath()
                )
            },
            { WallpaperFitHelper.deleteNextSource(filesDir) }
        )
        tasks.forEach { cleanup ->
            try {
                cleanup()
            } catch (error: Exception) {
                Log.w(TAG, "Theme mode is active, but obsolete standard data remains", error)
            }
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
                val wallpaper = File(playlistDir, "wallpaper_$index.jpg")
                val original = File(
                    originalsDir,
                    item.optString("original", "original_$index.jpg")
                )
                val source = when {
                    original.isFile -> original
                    wallpaper.isFile -> wallpaper
                    else -> {
                        Log.w(TAG, "Skipping theme playlist entry $index because its files are missing")
                        continue
                    }
                }
                val isEdited = item.optBoolean("isEdited", false) && wallpaper.isFile
                val matrix = item.optJSONArray("matrix")?.let { values ->
                    FloatArray(values.length()) { position -> values.optDouble(position).toFloat() }
                }?.takeIf(MatrixStatePolicy::isValid)
                destination += ThemePlaylistItem(
                    originalUri = Uri.fromFile(source),
                    isEdited = isEdited,
                    editedFilePath = if (isEdited) {
                        wallpaper.absolutePath
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
            if (destination.isEmpty()) loadLegacyCollection(playlistDir, destination)
        } catch (error: IOException) {
            recoverCollection(playlistDir, destination, error)
        } catch (error: JSONException) {
            recoverCollection(playlistDir, destination, error)
        } catch (error: RuntimeException) {
            recoverCollection(playlistDir, destination, error)
        }
    }

    private fun recoverCollection(
        playlistDir: File,
        destination: MutableList<ThemePlaylistItem>,
        error: Throwable
    ) {
        Log.e(TAG, "Stored theme playlist metadata is invalid; loading images without edits", error)
        destination.clear()
        loadLegacyCollection(playlistDir, destination)
        Toast.makeText(
            this,
            "Some saved crop details could not be restored.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun loadLegacyCollection(
        playlistDir: File,
        destination: MutableList<ThemePlaylistItem>
    ) {
        PlaylistModeManager.imageFiles(playlistDir).forEach { file ->
            destination += ThemePlaylistItem(Uri.fromFile(file))
        }
    }

    private fun activateService() {
        if (WallpaperEffectServices.launchPicker(this, effectId)) {
            finish()
        } else {
            Toast.makeText(
                this,
                "No live wallpaper picker is available on this device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun itemsFor(index: Int) = if (index == 0) lightItems else darkItems

    private fun themeLabel(index: Int) = if (index == 0) "Light" else "Dark"

    private fun readStoredAtmosphereGlass(): Boolean {
        return try {
            getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(AtmosphereGlassPolicy.ENABLED_KEY, false)
        } catch (error: ClassCastException) {
            Log.w(TAG, "Stored Atmosphere glass option has the wrong type", error)
            false
        }
    }

    private companion object {
        const val TAG = "ThemePlaylistEditor"
        const val APP_PREFERENCES = "app_prefs"
        const val WALLPAPER_PREFERENCES = "wallpaper_prefs"
        const val KEY_ROTATION_INTERVAL = "rotation_interval_minutes"
        const val KEY_LAST_ROTATION = "last_rotation_timestamp"
        const val KEY_LAST_LIGHT_IMAGE = "last_playlist_image_light"
        const val KEY_LAST_DARK_IMAGE = "last_playlist_image_dark"
        const val STATE_LIGHT_ITEMS = "light_playlist_items"
        const val STATE_DARK_ITEMS = "dark_playlist_items"
        const val STATE_ATMOSPHERE_GLASS = "theme_playlist_atmosphere_glass"
        const val ACTION_RELOAD_WALLPAPER =
            "com.app.nosatmosphereeffect.RELOAD_WALLPAPER"
    }
}
