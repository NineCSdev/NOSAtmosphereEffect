package com.app.nosatmosphereeffect.service

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.GlassEffectPreferences
import com.app.nosatmosphereeffect.helper.GlassEffectPolicy
import com.app.nosatmosphereeffect.renderer.GlassRenderer

abstract class GlassWallpaperService protected constructor(
    reverseEffect: Boolean
) : AnimatedEffectWallpaperService<GlassRenderer>() {

    final override val effectId = if (reverseEffect) "GLASS_REVERSE" else "GLASS"
    final override val lockedProgress = GlassEffectPolicy.shaderProgress(0f, reverseEffect)
    final override val unlockedProgress = GlassEffectPolicy.shaderProgress(1f, reverseEffect)
    final override val defaultAnimationDurationMs = 1_200L
    final override val initialProgress: Float? = if (reverseEffect) 1f else null

    final override fun createEffectRenderer(): GlassRenderer {
        return GlassRenderer(applicationContext)
    }

    final override fun configureRenderer(
        renderer: GlassRenderer,
        preferences: SharedPreferences
    ) {
        renderer.dimLevel = preferences.readFloat("dim_level", 0f)
        val settings = GlassEffectPreferences.readAndMigrate(preferences)
        renderer.lineCount = settings.lineCount
        renderer.lineThickness = settings.lineThickness
        renderer.transitionStyle = settings.transitionStyle
        renderer.configureBackgroundOnly(settings.backgroundOnly)
    }

    final override fun setEffectProgress(renderer: GlassRenderer, progress: Float) {
        renderer.progress = progress
    }

    final override fun reloadRenderer(renderer: GlassRenderer) {
        renderer.reloadTexture()
    }

    final override fun queuePlaylistTransition(renderer: GlassRenderer, bitmap: Bitmap) {
        renderer.queuePlaylistTransition(bitmap)
    }

    final override fun onRendererAttached(
        renderer: GlassRenderer,
        requestRender: () -> Unit
    ) {
        renderer.onRenderRetryRequested = requestRender
        renderer.onSubjectMaskUpdated = requestRender
    }

    final override fun releaseRenderer(renderer: GlassRenderer) {
        renderer.release()
    }

    private fun SharedPreferences.readFloat(key: String, fallback: Float): Float {
        return try {
            getFloat(key, fallback)
        } catch (failure: ClassCastException) {
            Log.w(TAG, "Preference '$key' has the wrong type; using $fallback", failure)
            fallback
        }
    }

    private companion object {
        const val TAG = "GlassService"
    }
}

class GlassService : GlassWallpaperService(reverseEffect = false)

class GlassReverseService : GlassWallpaperService(reverseEffect = true)
