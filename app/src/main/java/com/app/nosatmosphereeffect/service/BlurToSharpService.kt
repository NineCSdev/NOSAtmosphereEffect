package com.app.nosatmosphereeffect.service

import android.content.SharedPreferences
import android.graphics.Bitmap
import com.app.nosatmosphereeffect.helper.AtmosphereGlassPolicy
import com.app.nosatmosphereeffect.helper.GlassEffectPreferences
import com.app.nosatmosphereeffect.renderer.BlurToSharpRenderer

class BlurToSharpService : GlAnimatedEffectWallpaperService<BlurToSharpRenderer>() {

    override val effectId = "REVERSE"
    override val lockedProgress = 1f
    override val unlockedProgress = 0f
    override val defaultAnimationDurationMs = 1_500L
    override val initialProgress: Float = 1f
    override val blurDrawerWhenHidden = true

    override fun createEffectRenderer(): BlurToSharpRenderer {
        return BlurToSharpRenderer(applicationContext)
    }

    override fun configureRenderer(
        renderer: BlurToSharpRenderer,
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

    override fun setEffectProgress(renderer: BlurToSharpRenderer, progress: Float) {
        renderer.blurStrength = progress
    }

    override fun setFixedEffectState(
        renderer: BlurToSharpRenderer,
        effectApplied: Boolean
    ) {
        if (!effectApplied) {
            renderer.atmosphereGlassEnabled = false
        }
        super.setFixedEffectState(renderer, effectApplied)
    }

    override fun reloadRenderer(renderer: BlurToSharpRenderer) {
        renderer.reloadTexture()
    }

    override fun queuePlaylistTransition(renderer: BlurToSharpRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }

    override fun setDrawerBlurred(renderer: BlurToSharpRenderer, blurred: Boolean) {
        renderer.setDrawerBlurred(blurred)
    }

    override fun onRendererAttached(
        renderer: BlurToSharpRenderer,
        requestRender: () -> Unit
    ) {
        renderer.onRenderRetryRequested = requestRender
        renderer.onSubjectMaskUpdated = requestRender
    }

    override fun releaseRenderer(renderer: BlurToSharpRenderer) {
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
