package com.app.nosatmosphereeffect.service

import android.content.SharedPreferences
import android.graphics.Bitmap
import com.app.nosatmosphereeffect.helper.CanvasSubjectSettings
import com.app.nosatmosphereeffect.renderer.ColorFillRenderer
import com.app.nosatmosphereeffect.renderer.FrostedRenderer
import com.app.nosatmosphereeffect.renderer.HalftoneRenderer
import com.app.nosatmosphereeffect.renderer.NeonRenderer

abstract class ColorFillWallpaperService protected constructor(
    private val reverseEffect: Boolean
) : AnimatedEffectWallpaperService<ColorFillRenderer>() {

    final override val lockedProgress = if (reverseEffect) 0f else 1f
    final override val unlockedProgress = if (reverseEffect) 1f else 0f
    final override val defaultAnimationDurationMs = 1_500L

    final override fun createEffectRenderer(): ColorFillRenderer {
        return ColorFillRenderer(applicationContext, isReverse = reverseEffect)
    }

    final override fun configureRenderer(
        renderer: ColorFillRenderer,
        preferences: SharedPreferences
    ) {
        renderer.dimLevel = preferences.getFloat("dim_level", 0f)
        renderer.originX = preferences.getFloat("origin_x", 0.5f)
        renderer.originY = preferences.getFloat("origin_y", 0.8f)
    }

    final override fun setEffectProgress(renderer: ColorFillRenderer, progress: Float) {
        renderer.blurStrength = progress
    }

    final override fun reloadRenderer(renderer: ColorFillRenderer) {
        renderer.reloadTexture()
    }

    final override fun queuePlaylistTransition(renderer: ColorFillRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }
}

abstract class FrostedWallpaperService protected constructor(
    private val reverseEffect: Boolean
) : AnimatedEffectWallpaperService<FrostedRenderer>() {

    final override val lockedProgress = if (reverseEffect) 1f else 0f
    final override val unlockedProgress = if (reverseEffect) 0f else 1f
    final override val defaultAnimationDurationMs = 500L
    final override val initialProgress: Float? = if (reverseEffect) 1f else null
    final override val blurDrawerWhenHidden = reverseEffect

    final override fun createEffectRenderer(): FrostedRenderer {
        return FrostedRenderer(applicationContext)
    }

    final override fun configureRenderer(
        renderer: FrostedRenderer,
        preferences: SharedPreferences
    ) {
        renderer.dimLevel = preferences.getFloat("dim_level", 0.2f)
        renderer.enableNoise = preferences.getBoolean("enable_noise", false)
        renderer.noiseScale = preferences.getFloat("noise_scale", 2_000f)
        renderer.noiseStrength = preferences.getFloat("noise_strength", 0.06f)

        val savedRadius = preferences.getFloat("frosted_blur_radius", 200f)
        if (renderer.blurRadius != savedRadius) {
            renderer.blurRadius = savedRadius
            renderer.reloadTexture()
        }
    }

    final override fun setEffectProgress(renderer: FrostedRenderer, progress: Float) {
        renderer.blurStrength = progress
    }

    final override fun reloadRenderer(renderer: FrostedRenderer) {
        renderer.reloadTexture()
    }

    final override fun queuePlaylistTransition(renderer: FrostedRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }

    final override fun setDrawerBlurred(renderer: FrostedRenderer, blurred: Boolean) {
        renderer.setDrawerBlurred(blurred)
    }
}

abstract class HalftoneWallpaperService protected constructor(
    private val reverseEffect: Boolean
) : AnimatedEffectWallpaperService<HalftoneRenderer>() {

    final override val lockedProgress = 0f
    final override val unlockedProgress = 1f
    final override val defaultAnimationDurationMs = 500L

    final override fun createEffectRenderer(): HalftoneRenderer {
        return HalftoneRenderer(applicationContext, isReverse = reverseEffect)
    }

    final override fun configureRenderer(
        renderer: HalftoneRenderer,
        preferences: SharedPreferences
    ) {
        renderer.dimLevel = preferences.getFloat("dim_level", 0f)
        renderer.dotSize = preferences.getFloat("halftone_dot_size", 12f)
        renderer.grayscale = preferences.getBoolean("halftone_grayscale", false)
    }

    final override fun setEffectProgress(renderer: HalftoneRenderer, progress: Float) {
        renderer.blurStrength = progress
    }

    final override fun reloadRenderer(renderer: HalftoneRenderer) {
        renderer.reloadTexture()
    }

    final override fun queuePlaylistTransition(renderer: HalftoneRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }
}

abstract class NeonWallpaperService protected constructor(
    private val reverseEffect: Boolean
) : AnimatedEffectWallpaperService<NeonRenderer>() {

    final override val lockedProgress = 0f
    final override val unlockedProgress = 1f
    final override val defaultAnimationDurationMs = 1_000L

    final override fun createEffectRenderer(): NeonRenderer {
        return NeonRenderer(applicationContext, isReverse = reverseEffect)
    }

    final override fun configureRenderer(
        renderer: NeonRenderer,
        preferences: SharedPreferences
    ) {
        renderer.dimLevel = preferences.getFloat("dim_level", 0f)
        renderer.lineWidth = preferences.getFloat("neon_line_width", 1.5f)
        renderer.sensitivity = preferences.getFloat("neon_sensitivity", 0.5f)
        renderer.configureSubjectSegmentation(
            preferences.getBoolean(CanvasSubjectSettings.ENABLED_KEY, false)
        )
        renderer.rebuildSketch()
    }

    final override fun setEffectProgress(renderer: NeonRenderer, progress: Float) {
        renderer.blurStrength = progress
    }

    final override fun reloadRenderer(renderer: NeonRenderer) {
        renderer.reloadTexture()
    }

    final override fun queuePlaylistTransition(renderer: NeonRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }

    final override fun onRendererAttached(
        renderer: NeonRenderer,
        requestRender: () -> Unit
    ) {
        renderer.onSketchUpdated = requestRender
    }

    final override fun releaseRenderer(renderer: NeonRenderer) {
        renderer.release()
    }
}
