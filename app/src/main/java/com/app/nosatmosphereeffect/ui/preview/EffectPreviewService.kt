package com.app.nosatmosphereeffect.ui.preview

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.graphics.createBitmap
import com.app.nosatmosphereeffect.helper.CanvasSubjectSettings
import com.app.nosatmosphereeffect.renderer.AtmosphereRenderer
import com.app.nosatmosphereeffect.renderer.BlurToSharpRenderer
import com.app.nosatmosphereeffect.renderer.ColorFillRenderer
import com.app.nosatmosphereeffect.renderer.FrostedRenderer
import com.app.nosatmosphereeffect.renderer.HalftoneRenderer
import com.app.nosatmosphereeffect.renderer.NeonRenderer
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import java.util.concurrent.atomic.AtomicBoolean

enum class EffectPreviewSettingsMode {
    SAVED_ACTIVE,
    EFFECT_DEFAULTS
}

/**
 * Owns a small GLES surface backed by the exact renderer used by the live
 * wallpaper. Preview rendering stays in-process and never changes wallpaper
 * files or starts a wallpaper service.
 */
class EffectPreviewService(
    context: Context,
    private val effectId: String,
    source: Bitmap?,
    cornerRadiusPx: Float,
    private val settingsMode: EffectPreviewSettingsMode =
        EffectPreviewSettingsMode.SAVED_ACTIVE
) {
    private val appContext = context.applicationContext
    private val sourceBitmap = source?.copy(Bitmap.Config.ARGB_8888, false)
        ?: createDemoWallpaper()
    private val sourceProvider = {
        if (sourceBitmap.isRecycled) null else sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    private val productionRenderer = createRenderer()
    private val released = AtomicBoolean(false)
    private var resumed = false

    val view: GLSurfaceView = PreviewSurfaceView(context, cornerRadiusPx).apply {
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        holder.setFormat(PixelFormat.OPAQUE)
        setRenderer(productionRenderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    init {
        if (productionRenderer is NeonRenderer) {
            productionRenderer.onSketchUpdated = {
                if (!released.get()) view.post { view.requestRender() }
            }
        }
        setProgress(0f)
    }

    fun setProgress(lockToHomeProgress: Float) {
        if (released.get()) return
        val progress = lockToHomeProgress.coerceIn(0f, 1f)
        val rendererProgress = when (effectId) {
            "REVERSE", "FROSTED_REVERSE" -> 1f - progress
            "COLORFILL" -> 1f - progress
            else -> progress
        }

        view.queueEvent {
            if (released.get()) return@queueEvent
            when (val renderer = productionRenderer) {
                is AtmosphereRenderer -> renderer.blurStrength = rendererProgress
                is BlurToSharpRenderer -> renderer.blurStrength = rendererProgress
                is FrostedRenderer -> renderer.blurStrength = rendererProgress
                is HalftoneRenderer -> renderer.blurStrength = rendererProgress
                is ColorFillRenderer -> renderer.blurStrength = rendererProgress
                is NeonRenderer -> renderer.blurStrength = rendererProgress
            }
        }
        view.requestRender()
    }

    fun resume() {
        if (released.get() || resumed) return
        resumed = true
        view.onResume()
        view.requestRender()
    }

    fun pause() {
        if (released.get() || !resumed) return
        resumed = false
        view.onPause()
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        if (resumed) {
            resumed = false
            view.onPause()
        }
        (productionRenderer as? NeonRenderer)?.release()
        sourceBitmap.recycle()
    }

    private fun createRenderer(): GLSurfaceView.Renderer {
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return when (effectId) {
            "REVERSE" -> BlurToSharpRenderer(appContext, sourceProvider).apply {
                dimLevel = previewFloat(prefs, "dim_level", 0.2f)
                enableNoise = previewBoolean(prefs, "enable_noise", false)
                noiseScale = previewFloat(prefs, "noise_scale", 2000f)
                noiseStrength = previewFloat(prefs, "noise_strength", 0.06f)
                blobSaturation = previewFloat(prefs, "blob_saturation", 1f)
                blobContrast = previewFloat(prefs, "blob_contrast", 1f)
            }

            "FROSTED", "FROSTED_REVERSE" -> FrostedRenderer(appContext, sourceProvider).apply {
                dimLevel = previewFloat(prefs, "dim_level", 0.2f)
                enableNoise = previewBoolean(prefs, "enable_noise", false)
                noiseScale = previewFloat(prefs, "noise_scale", 2000f)
                noiseStrength = previewFloat(prefs, "noise_strength", 0.06f)
                blurRadius = previewFloat(prefs, "frosted_blur_radius", 200f)
            }

            "HALFTONE", "HALFTONE_REVERSE" -> HalftoneRenderer(
                appContext,
                isReverse = effectId == "HALFTONE_REVERSE",
                previewSource = sourceProvider
            ).apply {
                dimLevel = previewFloat(prefs, "dim_level", 0f)
                dotSize = previewFloat(prefs, "halftone_dot_size", 12f)
                grayscale = previewBoolean(prefs, "halftone_grayscale", false)
            }

            "COLORFILL", "COLORFILL_REVERSE" -> ColorFillRenderer(
                appContext,
                isReverse = effectId == "COLORFILL_REVERSE",
                previewSource = sourceProvider
            ).apply {
                dimLevel = previewFloat(prefs, "dim_level", 0f)
                originX = previewFloat(prefs, "origin_x", 0.5f)
                originY = previewFloat(prefs, "origin_y", 0.8f)
            }

            "NEON", "NEON_REVERSE" -> NeonRenderer(
                appContext,
                isReverse = effectId == "NEON_REVERSE",
                previewSource = sourceProvider
            ).apply {
                dimLevel = previewFloat(prefs, "dim_level", 0f)
                lineWidth = previewFloat(prefs, "neon_line_width", 1.5f)
                sensitivity = previewFloat(prefs, "neon_sensitivity", 0.5f)
                configureSubjectSegmentation(
                    previewBoolean(prefs, CanvasSubjectSettings.ENABLED_KEY, false)
                )
            }

            else -> AtmosphereRenderer(appContext, sourceProvider).apply {
                dimLevel = previewFloat(prefs, "dim_level", 0.2f)
                enableNoise = previewBoolean(prefs, "enable_noise", false)
                noiseScale = previewFloat(prefs, "noise_scale", 2000f)
                noiseStrength = previewFloat(prefs, "noise_strength", 0.06f)
                blobSaturation = previewFloat(prefs, "blob_saturation", 1f)
                blobContrast = previewFloat(prefs, "blob_contrast", 1f)
            }
        }
    }

    private fun previewFloat(
        preferences: SharedPreferences,
        key: String,
        defaultValue: Float
    ): Float = if (settingsMode == EffectPreviewSettingsMode.SAVED_ACTIVE) {
        preferences.getFloat(key, defaultValue)
    } else {
        defaultValue
    }

    private fun previewBoolean(
        preferences: SharedPreferences,
        key: String,
        defaultValue: Boolean
    ): Boolean = if (settingsMode == EffectPreviewSettingsMode.SAVED_ACTIVE) {
        preferences.getBoolean(key, defaultValue)
    } else {
        defaultValue
    }

    companion object {
        fun durationMillis(
            context: Context,
            effectId: String,
            settingsMode: EffectPreviewSettingsMode = EffectPreviewSettingsMode.SAVED_ACTIVE
        ): Int {
            val fallback = EffectCatalog.recommendedDurationMillis(effectId)
            if (settingsMode == EffectPreviewSettingsMode.EFFECT_DEFAULTS) {
                return fallback.coerceIn(150L, 10_000L).toInt()
            }
            val saved = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getLong("anim_duration", -1L)
            return (if (saved > 0L) saved else fallback).coerceIn(150L, 10_000L).toInt()
        }

        private fun createDemoWallpaper(): Bitmap {
            val width = 540
            val height = 960
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            canvas.drawColor(Color.rgb(30, 73, 82))
            paint.color = Color.rgb(239, 194, 102)
            canvas.drawCircle(width * 0.76f, height * 0.18f, width * 0.11f, paint)

            paint.color = Color.rgb(116, 100, 157)
            canvas.drawPath(Path().apply {
                moveTo(0f, height * 0.70f)
                lineTo(width * 0.34f, height * 0.38f)
                lineTo(width * 0.62f, height * 0.70f)
                close()
            }, paint)

            paint.color = Color.rgb(35, 70, 62)
            canvas.drawPath(Path().apply {
                moveTo(width * 0.28f, height * 0.72f)
                lineTo(width * 0.69f, height * 0.28f)
                lineTo(width.toFloat(), height * 0.72f)
                close()
            }, paint)

            paint.color = Color.rgb(204, 88, 79)
            canvas.drawRect(width * 0.09f, height * 0.50f, width * 0.36f, height * 0.78f, paint)
            paint.color = Color.rgb(249, 219, 157)
            repeat(3) { row ->
                repeat(3) { column ->
                    val left = width * (0.13f + column * 0.075f)
                    val top = height * (0.55f + row * 0.065f)
                    canvas.drawRect(left, top, left + width * 0.035f, top + height * 0.032f, paint)
                }
            }

            paint.color = Color.rgb(15, 38, 34)
            canvas.drawRect(0f, height * 0.72f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.rgb(106, 173, 140)
            paint.strokeWidth = width * 0.012f
            paint.style = Paint.Style.STROKE
            canvas.drawPath(Path().apply {
                moveTo(width * 0.52f, height * 0.91f)
                cubicTo(
                    width * 0.54f,
                    height * 0.78f,
                    width * 0.70f,
                    height * 0.80f,
                    width * 0.75f,
                    height * 0.68f
                )
            }, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width * 0.77f, height * 0.65f, width * 0.045f, paint)
            return bitmap
        }
    }

    private class PreviewSurfaceView(context: Context, private val radius: Float) :
        GLSurfaceView(context) {
        init {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            invalidateOutline()
        }
    }
}
