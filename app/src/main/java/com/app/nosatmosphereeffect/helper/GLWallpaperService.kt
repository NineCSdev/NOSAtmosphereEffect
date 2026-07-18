package com.app.nosatmosphereeffect.helper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.max

/**
 * Implemented by renderers that can pan their wallpaper horizontally in response
 * to home-screen page swipes (launcher parallax). The base [GLWallpaperService.GLEngine]
 * forwards [WallpaperService.Engine.onOffsetsChanged] to the active renderer when
 * it implements this, so individual services need no scrolling code of their own.
 */
interface WallpaperScrollRenderer {
    /** @param xOffset launcher horizontal offset, 0.0 (first page) .. 1.0 (last page). */
    fun setWallpaperOffset(xOffset: Float)
}

abstract class GLWallpaperService : WallpaperService() {

    private data class WallpaperColorSource(
        val lastModified: Long,
        val length: Long
    )

    open inner class GLEngine : Engine() {
        private var glSurfaceView: WallpaperGLSurfaceView? = null
        private var activeRenderer: GLSurfaceView.Renderer? = null
        private val pauseHandler = Handler(Looper.getMainLooper())
        private val pauseRunnable = Runnable { glSurfaceView?.onPause() }
        private val systemColorHandler = Handler(Looper.getMainLooper())
        private var cachedSystemColors: WallpaperColors? = null
        private var cachedColorSource: WallpaperColorSource? = null
        private val publishSystemColors = Runnable {
            cachedSystemColors = null
            cachedColorSource = null
            if (SystemColorSyncPreferences.isEnabled(this@GLWallpaperService)) {
                notifyColorsChanged()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolder.setFormat(PixelFormat.OPAQUE)
            glSurfaceView = WallpaperGLSurfaceView(this@GLWallpaperService)
            // Ask the launcher to deliver horizontal offset callbacks (used for
            // wallpaper scrolling). Harmless when the launcher does not scroll.
            setOffsetNotificationsEnabled(true)
        }

        fun setRenderer(renderer: GLSurfaceView.Renderer) {
            activeRenderer = renderer
            glSurfaceView?.setRenderer(renderer)
            glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        fun requestRender() {
            glSurfaceView?.requestRender()
        }

        /**
         * Invalidates the active image palette and publishes it from the engine's
         * main thread. Calls made during playlist file I/O are safely marshalled.
         */
        protected fun notifySystemColorsChanged() {
            systemColorHandler.removeCallbacks(publishSystemColors)
            systemColorHandler.post(publishSystemColors)
        }

        final override fun onComputeColors(): WallpaperColors? {
            if (!SystemColorSyncPreferences.isEnabled(this@GLWallpaperService)) {
                cachedSystemColors = null
                cachedColorSource = null
                return null
            }

            val wallpaperFile = File(filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE)
            if (!wallpaperFile.isFile) {
                cachedSystemColors = null
                cachedColorSource = null
                return null
            }

            val source = WallpaperColorSource(
                lastModified = wallpaperFile.lastModified(),
                length = wallpaperFile.length()
            )
            cachedSystemColors?.let { colors ->
                if (cachedColorSource == source) return colors
            }

            val colors = decodeWallpaperColors(wallpaperFile)
            cachedSystemColors = colors
            cachedColorSource = if (colors != null) source else null
            return colors
        }

        private fun decodeWallpaperColors(file: File): WallpaperColors? {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sampleSize = 1
                while (max(bounds.outWidth, bounds.outHeight) / sampleSize > 512) {
                    sampleSize *= 2
                }

                val bitmap = BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                ) ?: return null
                try {
                    WallpaperColors.fromBitmap(bitmap)
                } finally {
                    bitmap.recycle()
                }
            } catch (_: Exception) {
                null
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            val r = activeRenderer
            if (r is WallpaperScrollRenderer) {
                r.setWallpaperOffset(xOffset)
                // Dirty-mode renderer: nudge it to redraw at the new offset.
                glSurfaceView?.requestRender()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                pauseHandler.removeCallbacks(pauseRunnable)
                glSurfaceView?.onResume()
            } else {
                // Draw one more frame in the renderer's current state, then pause a few
                // frames later instead of immediately. Pausing the GL thread the instant
                // we go invisible leaves a stale frame latched in the surface, which the
                // compositor flashes on the next wake. Present a fresh frame first.
                glSurfaceView?.requestRender()
                pauseHandler.removeCallbacks(pauseRunnable)
                pauseHandler.postDelayed(pauseRunnable, 80L)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            pauseHandler.removeCallbacks(pauseRunnable)
            systemColorHandler.removeCallbacks(publishSystemColors)
            glSurfaceView?.onPause()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            glSurfaceView?.surfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            glSurfaceView?.surfaceCreated(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            glSurfaceView?.surfaceDestroyed(holder)
        }

        inner class WallpaperGLSurfaceView(context: Context) : GLSurfaceView(context) {
            init {
                setEGLConfigChooser(8, 8, 8, 0, 16, 0)
                setEGLContextClientVersion(3)
                preserveEGLContextOnPause = true
            }

            override fun getHolder(): SurfaceHolder {
                return this@GLEngine.surfaceHolder
            }
        }
    }

    abstract fun getRenderer(): GLSurfaceView.Renderer
}
