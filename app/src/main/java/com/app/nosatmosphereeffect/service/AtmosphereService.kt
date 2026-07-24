package com.app.nosatmosphereeffect.service

import android.content.SharedPreferences
import android.graphics.Bitmap
import com.app.nosatmosphereeffect.helper.AtmosphereGlassPolicy
import com.app.nosatmosphereeffect.helper.GlassEffectPreferences
import com.app.nosatmosphereeffect.renderer.AtmosphereRenderer

class AtmosphereService : AnimatedEffectWallpaperService<AtmosphereRenderer>() {

    override val lockedProgress = 0f
    override val unlockedProgress = 1f
    override val defaultAnimationDurationMs = 2_500L

    override fun createEffectRenderer(): AtmosphereRenderer {
        return AtmosphereRenderer(applicationContext)
    }

    override fun configureRenderer(
        renderer: AtmosphereRenderer,
        preferences: SharedPreferences
    ) {
        val glassEnabled = preferences.readBoolean(
            AtmosphereGlassPolicy.ENABLED_KEY,
            false
        )
        val glassSettings = GlassEffectPreferences.readAndMigrate(preferences)
        renderer.atmosphereGlassEnabled = glassEnabled
        renderer.glassLineCount = glassSettings.lineCount
        renderer.glassLineThickness = glassSettings.lineThickness
        renderer.configureGlassBackgroundOnly(
            glassEnabled && glassSettings.backgroundOnly
        )
        renderer.dimLevel = preferences.readFloat("dim_level", 0.2f)
        renderer.blobSaturation = preferences.readFloat("blob_saturation", 1f)
        renderer.blobContrast = preferences.readFloat("blob_contrast", 1f)
        renderer.enableNoise = preferences.readBoolean("enable_noise", false)
        renderer.noiseScale = preferences.readFloat("noise_scale", 2_000f)
        renderer.noiseStrength = preferences.readFloat("noise_strength", 0.06f)
    }

    override fun setEffectProgress(renderer: AtmosphereRenderer, progress: Float) {
        renderer.blurStrength = progress
    }

    override fun reloadRenderer(renderer: AtmosphereRenderer) {
        renderer.reloadTexture()
    }

    override fun queuePlaylistTransition(renderer: AtmosphereRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }

    override fun onRendererAttached(
        renderer: AtmosphereRenderer,
        requestRender: () -> Unit
    ) {
        renderer.onRenderRetryRequested = requestRender
        renderer.onSubjectMaskUpdated = requestRender
    }

    override fun releaseRenderer(renderer: AtmosphereRenderer) {
        renderer.release()
    }

    private fun SharedPreferences.readBoolean(key: String, fallback: Boolean): Boolean {
        return try {
            getBoolean(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }
    }

    private fun SharedPreferences.readFloat(key: String, fallback: Float): Float {
        return try {
            getFloat(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }
    }
}
