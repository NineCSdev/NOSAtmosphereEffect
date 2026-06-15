package com.app.nosatmosphereeffect.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import com.app.nosatmosphereeffect.ui.screens.AdvancedConfig
import com.app.nosatmosphereeffect.ui.screens.AdvancedResult
import com.app.nosatmosphereeffect.ui.screens.AdvancedSettingsScreen
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme

class AdvancedSettingsActivity : ComponentActivity() {

    private val rotationOptions = listOf(
        "System Theme (Light/Dark)", "Every Lock (Instant)", "1 Minute", "15 Minutes",
        "30 Minutes", "1 Hour", "3 Hours", "6 Hours", "12 Hours", "24 Hours"
    )
    private val rotationValues = longArrayOf(-1, 0, 1, 15, 30, 60, 180, 360, 720, 1440)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val activeEffect = intent.getStringExtra("ACTIVE_EFFECT_TYPE") ?: "ORIGINAL"
        val isSamsung = intent.getBooleanExtra("IS_SAMSUNG", false)
        val isPlaylistMode = intent.getBooleanExtra("IS_PLAYLIST_MODE", false)

        val isHalftone = activeEffect.contains("HALFTONE")
        val isColorFill = activeEffect.contains("COLORFILL")
        val showNoiseSwitch = !isHalftone && !isColorFill
        val showBlob = activeEffect == "ORIGINAL" || activeEffect == "REVERSE"

        val defaultDuration =
            if (activeEffect == "REVERSE" || isColorFill) 1500L
            else if (activeEffect == "ORIGINAL") 2500L
            else 500L
        val defaultPoll = if (isSamsung) 30000L else 50L
        val defaultDelay = if (isSamsung) 0L else 800L

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val wpPrefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

        val savedRotation = wpPrefs.getLong("rotation_interval_minutes", 0)
        val savedRotationIndex = rotationValues.indexOf(savedRotation).takeIf { it >= 0 } ?: 1

        val savedPoll = prefs.getLong("poll_interval", -1L)
        val savedDelay = prefs.getLong("lock_delay", -1L)
        val savedDuration = prefs.getLong("anim_duration", -1L)
        val savedNoiseScale = prefs.getFloat("noise_scale", -1f)
        val savedNoiseStrength = prefs.getFloat("noise_strength", -1f)

        val config = AdvancedConfig(
            showHalftone = isHalftone,
            showColorFill = isColorFill,
            showNoiseSwitch = showNoiseSwitch,
            showBlob = showBlob,
            isPlaylistMode = isPlaylistMode,
            rotationOptions = rotationOptions,
            initialRotationIndex = savedRotationIndex,
            poll = if (savedPoll != -1L) savedPoll.toString() else defaultPoll.toString(),
            delay = if (savedDelay != -1L) savedDelay.toString() else defaultDelay.toString(),
            duration = if (savedDuration != -1L) savedDuration.toString() else defaultDuration.toString(),
            enableNoise = prefs.getBoolean("enable_noise", false),
            noiseScale = if (savedNoiseScale != -1f) savedNoiseScale.toString() else "2000.0",
            noiseStrength = if (savedNoiseStrength != -1f) savedNoiseStrength.toString() else "0.06",
            dotSize = prefs.getFloat("halftone_dot_size", 12.0f),
            grayscale = prefs.getBoolean("halftone_grayscale", false),
            originX = prefs.getFloat("origin_x", 0.5f),
            originY = prefs.getFloat("origin_y", 0.8f),
            saturation = prefs.getFloat("blob_saturation", 1.0f),
            contrast = prefs.getFloat("blob_contrast", 1.0f)
        )

        setContent {
            AtmoEngineTheme {
                AdvancedSettingsScreen(
                    config = config,
                    onApply = { result ->
                        applySettings(result, prefs, wpPrefs, defaultPoll, defaultDelay, defaultDuration)
                    },
                    onReset = { resetSettings(prefs) },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun applySettings(
        result: AdvancedResult,
        prefs: android.content.SharedPreferences,
        wpPrefs: android.content.SharedPreferences,
        defaultPoll: Long,
        defaultDelay: Long,
        defaultDuration: Long
    ) {
        val poll = result.poll.toLongOrNull() ?: defaultPoll
        val delay = result.delay.toLongOrNull() ?: defaultDelay
        val duration = result.duration.toLongOrNull() ?: defaultDuration
        val noiseScale = result.noiseScale.toFloatOrNull() ?: 2000.0f
        val noiseStrength = result.noiseStrength.toFloatOrNull() ?: 0.06f
        val selectedRotationValue =
            rotationValues.getOrElse(result.rotationIndex) { rotationValues[1] }

        wpPrefs.edit { putLong("rotation_interval_minutes", selectedRotationValue) }

        prefs.edit {
            putLong("poll_interval", poll)
            putLong("lock_delay", delay)
            putLong("anim_duration", duration)
            putBoolean("enable_noise", result.enableNoise)
            putFloat("noise_scale", noiseScale)
            putFloat("noise_strength", noiseStrength)
            putFloat("halftone_dot_size", result.dotSize)
            putBoolean("halftone_grayscale", result.grayscale)
            putFloat("blob_saturation", result.saturation)
            putFloat("blob_contrast", result.contrast)
            putFloat("origin_x", result.originX)
            putFloat("origin_y", result.originY)
        }
        sendUpdateBroadcast()
    }

    private fun resetSettings(prefs: android.content.SharedPreferences) {
        prefs.edit {
            remove("poll_interval")
            remove("lock_delay")
            remove("anim_duration")
            remove("enable_noise")
            remove("noise_scale")
            remove("noise_strength")
            remove("halftone_dot_size")
            remove("halftone_grayscale")
            remove("blob_saturation")
            remove("blob_contrast")
            remove("origin_x")
            remove("origin_y")
        }
        sendUpdateBroadcast()
    }

    private fun sendUpdateBroadcast() {
        val intent = Intent("com.app.nosatmosphereeffect.UPDATE_CONFIG")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Toast.makeText(this, "Settings Applied!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
