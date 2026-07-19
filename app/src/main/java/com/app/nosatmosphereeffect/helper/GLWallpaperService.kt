package com.app.nosatmosphereeffect.helper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import java.util.concurrent.Executors

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
        private val systemColorExecutor = Executors.newSingleThreadExecutor()
        private var cachedSystemColors: WallpaperColors? = null
        private var cachedColorSource: WallpaperColorSource? = null
        private var pendingColorSource: WallpaperColorSource? = null
        private var colorRequestVersion = 0L
        private var engineDestroyed = false
        private val publishSystemColors = Runnable {
            requestSystemColorExtraction(invalidateCache = true)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolder.setFormat(PixelFormat.OPAQUE)
            glSurfaceView = WallpaperGLSurfaceView(this@GLWallpaperService)
            // Ask the launcher to deliver horizontal offset callbacks (used for
            // wallpaper scrolling). Harmless when the launcher does not scroll.
            setOffsetNotificationsEnabled(true)
            requestSystemColorExtraction(invalidateCache = true)
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
         * Invalidates and re-extracts the active image palette. Calls made during
         * playlist file I/O are safely marshalled onto the engine's main thread.
         */
        protected fun notifySystemColorsChanged() {
            PaletteSyncDiagnostics.record(
                this@GLWallpaperService,
                PaletteSyncDiagnostics.STAGE_REFRESH_QUEUED,
                "${this@GLWallpaperService::class.java.simpleName} queued a palette refresh"
            )
            systemColorHandler.removeCallbacks(publishSystemColors)
            systemColorHandler.post(publishSystemColors)
        }

        final override fun onComputeColors(): WallpaperColors? {
            if (!SystemColorSyncPreferences.isEnabled(this@GLWallpaperService)) {
                PaletteSyncDiagnostics.record(
                    this@GLWallpaperService,
                    PaletteSyncDiagnostics.STAGE_DISABLED,
                    "System color sync is disabled"
                )
                clearSystemColorState()
                return null
            }

            val wallpaperFile = File(filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE)
            val source = colorSourceFor(wallpaperFile)
            if (source == null) {
                PaletteSyncDiagnostics.record(
                    this@GLWallpaperService,
                    PaletteSyncDiagnostics.STAGE_MISSING_WALLPAPER,
                    "The active wallpaper file is missing or unreadable",
                    "FileNotFoundException: Active wallpaper image is missing or unreadable"
                )
                clearSystemColorState()
                return null
            }

            if (cachedColorSource == source) return cachedSystemColors

            requestSystemColorExtraction(invalidateCache = false)
            return null
        }

        private fun requestSystemColorExtraction(invalidateCache: Boolean) {
            if (engineDestroyed) return

            if (invalidateCache) {
                cachedSystemColors = null
                cachedColorSource = null
            }
            if (!SystemColorSyncPreferences.isEnabled(this@GLWallpaperService)) {
                PaletteSyncDiagnostics.record(
                    this@GLWallpaperService,
                    PaletteSyncDiagnostics.STAGE_DISABLED,
                    "System color sync is disabled"
                )
                clearSystemColorState()
                colorRequestVersion++
                return
            }

            val wallpaperFile = File(filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE)
            val source = colorSourceFor(wallpaperFile) ?: run {
                PaletteSyncDiagnostics.record(
                    this@GLWallpaperService,
                    PaletteSyncDiagnostics.STAGE_MISSING_WALLPAPER,
                    "The active wallpaper file is missing or unreadable",
                    "FileNotFoundException: Active wallpaper image is missing or unreadable"
                )
                clearSystemColorState()
                colorRequestVersion++
                return
            }
            if (!invalidateCache && (cachedColorSource == source || pendingColorSource == source)) {
                return
            }

            pendingColorSource = source
            val requestVersion = ++colorRequestVersion
            PaletteSyncDiagnostics.record(
                this@GLWallpaperService,
                PaletteSyncDiagnostics.STAGE_EXTRACTING,
                "${this@GLWallpaperService::class.java.simpleName} is extracting colors"
            )
            systemColorExecutor.execute {
                val extraction = runCatching {
                    WallpaperColorExtractor.extract(wallpaperFile)
                        ?: error("Wallpaper image could not be decoded")
                }
                systemColorHandler.post {
                    if (engineDestroyed || requestVersion != colorRequestVersion) return@post
                    pendingColorSource = null

                    if (!SystemColorSyncPreferences.isEnabled(this@GLWallpaperService)) {
                        clearSystemColorState()
                        return@post
                    }

                    val latestSource = colorSourceFor(wallpaperFile)
                    if (latestSource != source) {
                        requestSystemColorExtraction(invalidateCache = true)
                        return@post
                    }

                    val colors = extraction.getOrNull()
                    cachedSystemColors = colors
                    cachedColorSource = source
                    if (colors != null) {
                        try {
                            notifyColorsChanged()
                            PaletteSyncDiagnostics.record(
                                this@GLWallpaperService,
                                PaletteSyncDiagnostics.STAGE_PUBLISHED,
                                "${this@GLWallpaperService::class.java.simpleName} completed notifyColorsChanged()",
                                clearError = true
                            )
                        } catch (failure: Throwable) {
                            PaletteSyncDiagnostics.record(
                                this@GLWallpaperService,
                                PaletteSyncDiagnostics.STAGE_PUBLISH_FAILED,
                                "Android rejected the wallpaper color callback",
                                failure.toDiagnosticText()
                            )
                        }
                    } else {
                        val failure = extraction.exceptionOrNull()
                        PaletteSyncDiagnostics.record(
                            this@GLWallpaperService,
                            PaletteSyncDiagnostics.STAGE_EXTRACTION_FAILED,
                            "The wallpaper engine could not publish colors",
                            failure?.toDiagnosticText() ?: "Unknown extraction failure"
                        )
                        runCatching { notifyColorsChanged() }.onFailure { publishFailure ->
                            PaletteSyncDiagnostics.record(
                                this@GLWallpaperService,
                                PaletteSyncDiagnostics.STAGE_PUBLISH_FAILED,
                                "Android rejected the empty wallpaper color callback",
                                publishFailure.toDiagnosticText()
                            )
                        }
                    }
                }
            }
        }

        private fun colorSourceFor(file: File): WallpaperColorSource? {
            if (!file.isFile) return null
            return WallpaperColorSource(
                lastModified = file.lastModified(),
                length = file.length()
            )
        }

        private fun clearSystemColorState() {
            cachedSystemColors = null
            cachedColorSource = null
            pendingColorSource = null
        }

        private fun Throwable.toDiagnosticText(): String {
            val readableMessage = message?.takeIf { it.isNotBlank() }
            return if (readableMessage == null) {
                javaClass.simpleName
            } else {
                "${javaClass.simpleName}: $readableMessage"
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
            engineDestroyed = true
            colorRequestVersion++
            pauseHandler.removeCallbacks(pauseRunnable)
            systemColorHandler.removeCallbacks(publishSystemColors)
            systemColorExecutor.shutdownNow()
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
