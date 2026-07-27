package com.app.nosatmosphereeffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeSession
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatusRepository
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanNeonHost
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanSupport

class NeonRenderController(
    context: Context,
    private val isReverse: Boolean
) {
    private val appContext = context.applicationContext
    private val effectId = if (isReverse) "NEON_REVERSE" else "NEON"
    private val lock = Any()

    private var state = NeonRenderState()
    private var engine: GLWallpaperService.GLEngine? = null
    private var activeHost: WallpaperRenderHost? = null
    private var openGlRenderer: NeonRenderer? = null
    private var vulkanHost: VulkanNeonHost? = null
    private var runtimeSession: RendererRuntimeSession? = null
    private var closed = false

    fun attach(engine: GLWallpaperService.GLEngine) {
        synchronized(lock) {
            check(this.engine == null) { "The Canvas renderer is already attached" }
            check(!closed) { "The Canvas renderer has been released" }
            this.engine = engine
        }

        val selection = VulkanSupport.selectBackend(appContext, effectId)
        synchronized(lock) { runtimeSession = selection.runtimeSession }
        when (selection.backend) {
            GraphicsBackend.VULKAN -> attachVulkan(engine)
            GraphicsBackend.OPENGL_ES -> attachOpenGl(engine)
        }
    }

    fun configure(
        dimLevel: Float,
        lineWidth: Float,
        sensitivity: Float,
        subjectSegmentationEnabled: Boolean
    ) {
        val snapshot = synchronized(lock) {
            state = state.copy(
                dimLevel = dimLevel,
                lineWidth = lineWidth,
                sensitivity = sensitivity,
                subjectSegmentationEnabled = subjectSegmentationEnabled
            ).sanitized()
            state
        }

        val gl: NeonRenderer?
        val vk: VulkanNeonHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.apply {
            this.dimLevel = snapshot.dimLevel
            this.lineWidth = snapshot.lineWidth
            this.sensitivity = snapshot.sensitivity
            configureSubjectSegmentation(snapshot.subjectSegmentationEnabled)
            rebuildSketch()
        }
        vk?.updateState(snapshot)
    }

    fun setProgress(progress: Float) {
        val snapshot = synchronized(lock) {
            state = state.copy(progress = progress).sanitized()
            state
        }
        val gl: NeonRenderer?
        val vk: VulkanNeonHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.blurStrength = snapshot.progress
        vk?.updateState(snapshot)
    }

    fun reloadTexture() {
        val gl: NeonRenderer?
        val vk: VulkanNeonHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.reloadTexture()
        vk?.reloadTexture()
    }

    fun queuePlaylistTransition(bitmap: Bitmap) {
        val gl: NeonRenderer?
        val vk: VulkanNeonHost?
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
        val vk: VulkanNeonHost?
        val gl: NeonRenderer?
        val session: RendererRuntimeSession?
        synchronized(lock) {
            if (closed) return
            closed = true
            vk = vulkanHost
            gl = openGlRenderer
            session = runtimeSession
            vulkanHost = null
            openGlRenderer = null
            activeHost = null
            engine = null
            runtimeSession = null
        }
        vk?.close()
        gl?.release()
        publishRendererStatus("releasing the renderer session", session) {
            RendererRuntimeStatusRepository.recordReleased(appContext, it)
        }
    }

    private fun attachVulkan(engine: GLWallpaperService.GLEngine) {
        publishRendererStatus("marking Vulkan as initializing") {
            RendererRuntimeStatusRepository.recordVulkanInitializing(
                appContext,
                it
            )
        }
        val snapshot = synchronized(lock) { state }
        val host = VulkanNeonHost(
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
        val renderer = createOpenGlRenderer(engine)
        val host = engine.setRenderer(renderer)
        synchronized(lock) {
            if (closed) {
                host.close()
                renderer.release()
                return
            }
            openGlRenderer = renderer
            activeHost = host
        }
        publishRendererStatus("marking OpenGL ES as active") {
            RendererRuntimeStatusRepository.recordOpenGlActive(
                appContext,
                it
            )
        }
    }

    private fun fallbackToOpenGl(
        failedHost: WallpaperRenderHost,
        reason: String
    ) {
        val currentEngine: GLWallpaperService.GLEngine
        synchronized(lock) {
            if (closed || activeHost !== failedHost) return
            currentEngine = engine ?: return
        }

        runCatching { VulkanSupport.recordFailure(appContext, effectId, reason) }
            .onFailure { failure ->
                Log.w(TAG, "Unable to persist the Vulkan fallback state", failure)
            }
        val fallback = runCatching {
            val renderer = createOpenGlRenderer(currentEngine)
            renderer to currentEngine.createOpenGlRenderHost(renderer)
        }.getOrElse { failure ->
            Log.e(TAG, "Unable to create the OpenGL ES Canvas fallback", failure)
            return
        }
        val (renderer, replacement) = fallback
        if (!currentEngine.replaceRenderHost(failedHost, replacement)) {
            renderer.release()
            Log.e(TAG, "Unable to attach the OpenGL ES Canvas fallback")
            return
        }

        synchronized(lock) {
            if (closed) {
                renderer.release()
                return
            }
            openGlRenderer = renderer
            vulkanHost = null
            activeHost = replacement
        }
        publishRendererStatus("publishing the OpenGL ES fallback") {
            RendererRuntimeStatusRepository.recordOpenGlActive(
                context = appContext,
                session = it,
                reason = reason
            )
        }
        renderer.reloadTexture()
        currentEngine.requestRender()
        Log.w(TAG, "Canvas switched to OpenGL ES after Vulkan failed: $reason")
    }

    private fun onVulkanActive(
        host: WallpaperRenderHost,
        packedVersion: Int
    ) {
        val isCurrentHost = synchronized(lock) {
            !closed && activeHost === host
        }
        if (!isCurrentHost) return
        publishRendererStatus("marking Vulkan as active") {
            RendererRuntimeStatusRepository.recordVulkanActive(
                context = appContext,
                session = it,
                packedVersion = packedVersion
            )
        }
    }

    private fun createOpenGlRenderer(
        renderEngine: GLWallpaperService.GLEngine
    ): NeonRenderer {
        val snapshot = synchronized(lock) { state }
        return NeonRenderer(appContext, isReverse = isReverse).apply {
            blurStrength = snapshot.progress
            dimLevel = snapshot.dimLevel
            lineWidth = snapshot.lineWidth
            sensitivity = snapshot.sensitivity
            configureSubjectSegmentation(snapshot.subjectSegmentationEnabled)
            onSketchUpdated = renderEngine::requestRender
        }
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private inline fun publishRendererStatus(
        operation: String,
        session: RendererRuntimeSession? = synchronized(lock) { runtimeSession },
        publish: (RendererRuntimeSession) -> Unit
    ) {
        if (session == null) return
        runCatching { publish(session) }
            .onFailure { failure ->
                Log.w(TAG, "Unable to update renderer status while $operation", failure)
            }
    }

    private companion object {
        const val TAG = "CanvasController"
    }
}
