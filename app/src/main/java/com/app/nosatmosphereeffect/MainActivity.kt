package com.app.nosatmosphereeffect

import android.app.WallpaperManager
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.app.nosatmosphereeffect.activity.AdvancedSettingsActivity
import com.app.nosatmosphereeffect.activity.BlurToSharpCropActivity
import com.app.nosatmosphereeffect.activity.CropActivity
import com.app.nosatmosphereeffect.activity.EffectSelectionActivity
import com.app.nosatmosphereeffect.activity.PlaylistEditorActivity
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
import com.app.nosatmosphereeffect.ui.screens.MainScreen
import com.app.nosatmosphereeffect.ui.theme.AppearancePreferences
import com.app.nosatmosphereeffect.ui.theme.AppThemeMode
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme
import java.io.File

class MainActivity : ComponentActivity() {

    // --- UI state (observed by Compose) ---
    private var wallpaperActive by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var isPlaylistModeActive by mutableStateOf(false)
    private var syncColors by mutableStateOf(true)
    private var expressiveThemeEnabled by mutableStateOf(true)
    private var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
    private var pitchBlackEnabled by mutableStateOf(false)
    private var activeEffectId by mutableStateOf<String?>(null)
    private var previewBitmap by mutableStateOf<ImageBitmap?>(null)
    private var skipNextResumeStatusRefresh = false

    private val pickSingleImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { launchCropActivity(it) }
        }

    private val pickMultipleImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) launchMultiCropActivity(ArrayList(uris))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeSmartDefaults()
        expressiveThemeEnabled = AppearancePreferences.isExpressiveEnabled(this)
        themeMode = AppearancePreferences.getThemeMode(this)
        pitchBlackEnabled = AppearancePreferences.isPitchBlackEnabled(this)

        statusText = getString(R.string.status_instruction)
        checkWallpaperStatus()
        skipNextResumeStatusRefresh = true

        setContent {
            AtmoEngineTheme(
                expressive = expressiveThemeEnabled,
                themeMode = themeMode,
                pitchBlack = pitchBlackEnabled
            ) {
                MainScreen(
                    wallpaperActive = wallpaperActive,
                    statusText = statusText,
                    activeEffectId = activeEffectId,
                    previewBitmap = previewBitmap,
                    isPlaylistMode = isPlaylistModeActive,
                    syncColors = syncColors,
                    onSyncColorsChange = { updateSyncColors(it) },
                    expressiveThemeEnabled = expressiveThemeEnabled,
                    onExpressiveThemeChange = { updateExpressiveTheme(it) },
                    themeMode = themeMode,
                    onThemeModeChange = { updateThemeMode(it) },
                    pitchBlackEnabled = pitchBlackEnabled,
                    onPitchBlackChange = { updatePitchBlack(it) },
                    onSetupWallpaper = {
                        startActivity(Intent(this, EffectSelectionActivity::class.java))
                    },
                    onChangeEffect = {
                        val intent = Intent(this, EffectSelectionActivity::class.java)
                        intent.putExtra("UPDATE_EFFECT_ONLY", true)
                        startActivity(intent)
                    },
                    onPickSingleImage = { pickSingleImage.launch("image/*") },
                    onPickMultipleImages = { pickMultipleImages.launch("image/*") },
                    onEditExistingPlaylist = { launchEditExistingPlaylist() },
                    onAdvancedSettings = { openAdvancedSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        expressiveThemeEnabled = AppearancePreferences.isExpressiveEnabled(this)
        themeMode = AppearancePreferences.getThemeMode(this)
        pitchBlackEnabled = AppearancePreferences.isPitchBlackEnabled(this)
        if (skipNextResumeStatusRefresh) {
            skipNextResumeStatusRefresh = false
        } else {
            checkWallpaperStatus()
        }
    }

    private fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun initializeSmartDefaults() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.contains("poll_interval")) {
            val isSamsung = isSamsungDevice()
            val defaultPoll = if (isSamsung) 30000L else 50L
            val defaultDelay = if (isSamsung) 0L else 800L
            prefs.edit {
                putLong("poll_interval", defaultPoll)
                putLong("lock_delay", defaultDelay)
            }
        }
    }

    private fun checkWallpaperStatus() {
        val activeEffect = getActiveEffectType()
        if (activeEffect != null) {
            activeEffectId = activeEffect
            wallpaperActive = true
            statusText = "Wallpaper is active. Customize your experience below."
            // Determine current mode (single vs playlist).
            val playlistDir = File(filesDir, "playlist")
            isPlaylistModeActive = false
            if (playlistDir.exists() && playlistDir.isDirectory) {
                val files = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }
                if (!files.isNullOrEmpty() && files.size > 1) isPlaylistModeActive = true
            }

            // Detect mode change and force safe defaults for the colour sync toggle.
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val lastMode = prefs.getString("last_known_wallpaper_mode", "UNKNOWN")
            val currentMode = if (isPlaylistModeActive) "PLAYLIST" else "SINGLE"
            if (lastMode != currentMode) {
                if (isPlaylistModeActive) {
                    prefs.edit { putBoolean("notify_system_colors", false) }
                    sendConfigUpdate()
                } else {
                    prefs.edit { putBoolean("notify_system_colors", true) }
                    sendConfigUpdate()
                }
                prefs.edit { putString("last_known_wallpaper_mode", currentMode) }
            }

            syncColors = prefs.getBoolean("notify_system_colors", !isPlaylistModeActive)
            loadWallpaperPreview()
        } else {
            activeEffectId = null
            previewBitmap = null
            wallpaperActive = false
            statusText = getString(R.string.status_instruction)
        }
    }

    private fun updateExpressiveTheme(enabled: Boolean) {
        expressiveThemeEnabled = enabled
        AppearancePreferences.setExpressiveEnabled(this, enabled)
    }

    private fun updateThemeMode(mode: AppThemeMode) {
        themeMode = mode
        AppearancePreferences.setThemeMode(this, mode)
    }

    private fun updatePitchBlack(enabled: Boolean) {
        pitchBlackEnabled = enabled
        AppearancePreferences.setPitchBlackEnabled(this, enabled)
    }

    private fun loadWallpaperPreview() {
        val file = File(filesDir, "wallpaper.jpg")
        if (!file.exists()) {
            previewBitmap = null
            return
        }
        Thread {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
            runOnUiThread { previewBitmap = bitmap?.asImageBitmap() }
        }.start()
    }

    private fun updateSyncColors(enabled: Boolean) {
        syncColors = enabled
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit {
            putBoolean("notify_system_colors", enabled)
        }
        sendConfigUpdate()
    }

    private fun sendConfigUpdate() {
        val intent = Intent("com.app.nosatmosphereeffect.UPDATE_CONFIG")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun openAdvancedSettings() {
        val intent = Intent(this, AdvancedSettingsActivity::class.java)
        intent.putExtra("ACTIVE_EFFECT_TYPE", getActiveEffectType() ?: "ORIGINAL")
        intent.putExtra("IS_SAMSUNG", isSamsungDevice())
        intent.putExtra("IS_PLAYLIST_MODE", isPlaylistModeActive)
        startActivity(intent)
    }

    private fun getActiveEffectType(): String? {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo ?: return null
        if (info.packageName == packageName) {
            return when (info.component.className) {
                AtmosphereService::class.java.name -> "ORIGINAL"
                BlurToSharpService::class.java.name -> "REVERSE"
                FrostedService::class.java.name -> "FROSTED"
                FrostedReverseService::class.java.name -> "FROSTED_REVERSE"
                HalftoneService::class.java.name -> "HALFTONE"
                HalftoneReverseService::class.java.name -> "HALFTONE_REVERSE"
                ColorFillService::class.java.name -> "COLORFILL"
                ColorFillReverseService::class.java.name -> "COLORFILL_REVERSE"
                NeonService::class.java.name -> "NEON"
                NeonReverseService::class.java.name -> "NEON_REVERSE"
                else -> null
            }
        }
        return null
    }

    private fun launchEditExistingPlaylist() {
        val playlistDir = File(filesDir, "playlist")
        if (!playlistDir.exists()) return
        val files = playlistDir.listFiles { _, name -> name.endsWith(".jpg") }
        if (files.isNullOrEmpty()) return

        val effectId = getActiveEffectType() ?: "ORIGINAL"
        val intent = Intent(this, PlaylistEditorActivity::class.java)
        intent.putExtra("EDIT_EXISTING", true)
        intent.putExtra("EFFECT_ID", effectId)
        startActivity(intent)
    }

    private fun launchCropActivity(uri: Uri) {
        val effectId = getActiveEffectType() ?: "ORIGINAL"
        val intent = if (effectId.contains("REVERSE")) {
            Intent(this, BlurToSharpCropActivity::class.java)
        } else {
            Intent(this, CropActivity::class.java)
        }
        intent.data = uri
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra("EFFECT_ID", effectId)
        startActivity(intent)
    }

    private fun launchMultiCropActivity(uris: ArrayList<Uri>) {
        val effectId = getActiveEffectType() ?: "ORIGINAL"
        val intent = Intent(this, PlaylistEditorActivity::class.java)
        intent.data = uris[0]
        val clipData = ClipData.newUri(contentResolver, "Images", uris[0])
        for (i in 1 until uris.size) clipData.addItem(ClipData.Item(uris[i]))
        intent.clipData = clipData
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putParcelableArrayListExtra("IMAGE_URIS", uris)
        intent.putExtra("EFFECT_ID", effectId)
        startActivity(intent)
    }
}
