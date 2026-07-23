package com.app.nosatmosphereeffect.service

import android.content.SharedPreferences
import android.graphics.Bitmap
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
        renderer.dimLevel = preferences.getFloat("dim_level", 0.2f)
        renderer.blobSaturation = preferences.getFloat("blob_saturation", 1f)
        renderer.blobContrast = preferences.getFloat("blob_contrast", 1f)
        renderer.enableNoise = preferences.getBoolean("enable_noise", false)
        renderer.noiseScale = preferences.getFloat("noise_scale", 2_000f)
        renderer.noiseStrength = preferences.getFloat("noise_strength", 0.06f)
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
}
