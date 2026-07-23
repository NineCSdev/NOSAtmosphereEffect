package com.app.nosatmosphereeffect.service

import android.content.SharedPreferences
import android.graphics.Bitmap
import com.app.nosatmosphereeffect.renderer.BlurToSharpRenderer

class BlurToSharpService : AnimatedEffectWallpaperService<BlurToSharpRenderer>() {

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
        renderer.dimLevel = preferences.getFloat("dim_level", 0.2f)
        renderer.blobSaturation = preferences.getFloat("blob_saturation", 1f)
        renderer.blobContrast = preferences.getFloat("blob_contrast", 1f)
        renderer.enableNoise = preferences.getBoolean("enable_noise", false)
        renderer.noiseScale = preferences.getFloat("noise_scale", 2_000f)
        renderer.noiseStrength = preferences.getFloat("noise_strength", 0.06f)
    }

    override fun setEffectProgress(renderer: BlurToSharpRenderer, progress: Float) {
        renderer.blurStrength = progress
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
}
