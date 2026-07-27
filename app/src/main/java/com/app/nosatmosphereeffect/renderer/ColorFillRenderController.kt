package com.app.nosatmosphereeffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatusRepository
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanColorFillHost
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanSupport

class ColorFillRenderController(
    context: Context,
    private val isReverse: Boolean
) {
    private val appContext = context.applicationContext
    private val effectId = if (isReverse) "COLORFILL_REVERSE" else "COLORFILL"
    private val lock = Any()

    private var state = ColorFillRenderState()
    private var engine: GLWallpaperService.GLEngine? = null
    private var activeHost: WallpaperRenderHost? = null
    private var openGlRenderer: ColorFillRenderer? = null
    private var vulkanHost: VulkanColorFillHost? = null
    private var closed = false

    fun attach(engine: GLWallpaperService.GLEngine) {
        synchronized(lock) {
            check(this.engine == null) { "The Color Fill renderer is already attached" }
            check(!closed) { "The Color Fill renderer has been released" }
            this.engine = engine
        }

        when (VulkanSupport.selectBackend(appContext, effectId)) {
            GraphicsBackend.VULKAN -> attachVulkan(engine)
            GraphicsBackend.OPENGL_ES -> attachOpenGl(engine)
        }
    }

    fun configure(dimLevel: Float, originX: Float, originY: Float) {
        val snapshot = synchronized(lock) {
            state = state.copy(
                dimLevel = dimLevel,
                originX = originX,
                originY = originY
            ).sanitized()
            state
        }
        applyState(snapshot)
    }

    fun setProgress(progress: Float) {
        val snapshot = synchronized(lock) {
            state = state.copy(progress = progress).sanitized()
            state
        }
        applyState(snapshot)
    }

    fun reloadTexture() {
        val gl: ColorFillRenderer?
        val vk: VulkanColorFillHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.reloadTexture()
        vk?.reloadTexture()
    }

    fun queuePlaylistTransition(bitmap: Bitmap) {
        val gl: ColorFillRenderer?
        val vk: VulkanColorFillHost?
        val isClosed: Boolean
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
            isClosed = closed
        }
        when {
            isClosed -> bitmap.recycleSafely()
            vk != null -> vk.queuePlaylistTransition(bitmap)
            gl != null -> gl.queuePlaylistTransition(bitmap)
            else -> bitmap.recycleSafely()
        }
    }

    fun release() {
        val vk: VulkanColorFillHost?
        synchronized(lock) {
            if (closed) return
            closed = true
            vk = vulkanHost
            vulkanHost = null
            openGlRenderer = null
            activeHost = null
            engine = null
        }
        vk?.close()
        publishRendererStatus("releasing the renderer") {
            RendererRuntimeStatusRepository.recordReleased(appContext, effectId)
        }
    }

    private fun attachVulkan(engine: GLWallpaperService.GLEngine) {
        publishRendererStatus("marking Vulkan as initializing") {
            RendererRuntimeStatusRepository.recordVulkanInitializing(appContext, effectId)
        }
        val snapshot = synchronized(lock) { state }
        val host = VulkanColorFillHost(
            context = appContext,
            reverse = isReverse,
            initialState = snapshot,
            onFatalFailure = ::fallbackToOpenGl,
            onVulkanActive = ::onVulkanActive
        )
        synchronized(lock) {
            if (closed) {
                host.close()
                return
            }
            vulkanHost = host
            activeHost = host
        }
        engine.installRenderHost(host)
    }

    private fun attachOpenGl(engine: GLWallpaperService.GLEngine) {
        val renderer = createOpenGlRenderer()
        val host = engine.setRenderer(renderer)
        synchronized(lock) {
            if (closed) {
                host.close()
                return
            }
            openGlRenderer = renderer
            activeHost = host
        }
        publishRendererStatus("marking OpenGL ES as active") {
            RendererRuntimeStatusRepository.recordOpenGlActive(appContext, effectId)
        }
    }

    private fun fallbackToOpenGl(failedHost: VulkanColorFillHost, reason: String) {
        val currentEngine: GLWallpaperService.GLEngine
        synchronized(lock) {
            if (closed || activeHost !== failedHost) return
            currentEngine = engine ?: return
        }

        runCatching { VulkanSupport.recordFailure(appContext, reason) }
            .onFailure { failure ->
                Log.w(TAG, "Unable to persist the Vulkan fallback state", failure)
            }
        val fallback = runCatching {
            val renderer = createOpenGlRenderer()
            renderer to currentEngine.createOpenGlRenderHost(renderer)
        }.getOrElse { failure ->
            Log.e(TAG, "Unable to create the OpenGL ES fallback renderer", failure)
            return
        }
        val (renderer, replacement) = fallback
        if (!currentEngine.replaceRenderHost(failedHost, replacement)) {
            Log.e(TAG, "Unable to attach the OpenGL ES fallback renderer")
            return
        }

        synchronized(lock) {
            if (closed) return
            openGlRenderer = renderer
            vulkanHost = null
            activeHost = replacement
        }
        publishRendererStatus("publishing the OpenGL ES fallback") {
            RendererRuntimeStatusRepository.recordOpenGlActive(
                context = appContext,
                effectId = effectId,
                reason = reason
            )
        }
        renderer.reloadTexture()
        currentEngine.requestRender()
        Log.w(TAG, "Color Fill switched to OpenGL ES after Vulkan failed: $reason")
    }

    private fun onVulkanActive(host: VulkanColorFillHost, packedVersion: Int) {
        val isCurrentHost = synchronized(lock) {
            !closed && activeHost === host
        }
        if (!isCurrentHost) return
        publishRendererStatus("marking Vulkan as active") {
            RendererRuntimeStatusRepository.recordVulkanActive(
                context = appContext,
                effectId = effectId,
                packedVersion = packedVersion
            )
        }
    }

    private fun createOpenGlRenderer(): ColorFillRenderer {
        val snapshot = synchronized(lock) { state }
        return ColorFillRenderer(appContext, isReverse = isReverse).apply {
            blurStrength = snapshot.progress
            dimLevel = snapshot.dimLevel
            originX = snapshot.originX
            originY = snapshot.originY
        }
    }

    private fun applyState(snapshot: ColorFillRenderState) {
        val gl: ColorFillRenderer?
        val vk: VulkanColorFillHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.apply {
            blurStrength = snapshot.progress
            dimLevel = snapshot.dimLevel
            originX = snapshot.originX
            originY = snapshot.originY
        }
        vk?.updateState(snapshot)
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private inline fun publishRendererStatus(
        operation: String,
        publish: () -> Unit
    ) {
        runCatching(publish)
            .onFailure { failure ->
                Log.w(TAG, "Unable to update renderer status while $operation", failure)
            }
    }

    private companion object {
        const val TAG = "ColorFillController"
    }
}
