package com.app.nosatmosphereeffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.helper.GlassTransitionStyle
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeSession
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatusRepository
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanGlassHost
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanSupport

class GlassRenderController(
    context: Context,
    private val reverse: Boolean
) {
    private val appContext = context.applicationContext
    private val effectId = if (reverse) "GLASS_REVERSE" else "GLASS"
    private val lock = Any()

    private var state = GlassRenderState()
    private var engine: GLWallpaperService.GLEngine? = null
    private var activeHost: WallpaperRenderHost? = null
    private var openGlRenderer: GlassRenderer? = null
    private var vulkanHost: VulkanGlassHost? = null
    private var runtimeSession: RendererRuntimeSession? = null
    private var closed = false

    fun attach(engine: GLWallpaperService.GLEngine) {
        synchronized(lock) {
            check(this.engine == null) { "The Glass renderer is already attached" }
            check(!closed) { "The Glass renderer has been released" }
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
        lineCount: Int,
        lineThickness: Float,
        transitionStyle: GlassTransitionStyle,
        backgroundOnly: Boolean
    ) {
        val snapshot = synchronized(lock) {
            state = state.copy(
                dimLevel = dimLevel,
                lineCount = lineCount,
                lineThickness = lineThickness,
                transitionStyle = transitionStyle,
                backgroundOnly = backgroundOnly
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
        synchronized(lock) {
            openGlRenderer to vulkanHost
        }.let { (gl, vk) ->
            gl?.reloadTexture()
            vk?.reloadTexture()
        }
    }

    fun queuePlaylistTransition(bitmap: Bitmap) {
        val targets = synchronized(lock) {
            Triple(openGlRenderer, vulkanHost, closed)
        }
        when {
            targets.third -> bitmap.recycleSafely()
            targets.second != null -> targets.second?.queuePlaylistTransition(bitmap)
            targets.first != null -> targets.first?.queuePlaylistTransition(bitmap)
            else -> bitmap.recycleSafely()
        }
    }

    fun release() {
        val gl: GlassRenderer?
        val vk: VulkanGlassHost?
        val session: RendererRuntimeSession?
        synchronized(lock) {
            if (closed) return
            closed = true
            gl = openGlRenderer
            vk = vulkanHost
            session = runtimeSession
            openGlRenderer = null
            vulkanHost = null
            activeHost = null
            engine = null
            runtimeSession = null
        }
        gl?.release()
        vk?.close()
        publishStatus(session) {
            RendererRuntimeStatusRepository.recordReleased(appContext, it)
        }
    }

    private fun attachVulkan(engine: GLWallpaperService.GLEngine) {
        publishStatus {
            RendererRuntimeStatusRepository.recordVulkanInitializing(
                appContext,
                it
            )
        }
        val host = VulkanGlassHost(
            context = appContext,
            initialState = synchronized(lock) { state },
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
                renderer.release()
                host.close()
                return
            }
            openGlRenderer = renderer
            activeHost = host
        }
        publishStatus {
            RendererRuntimeStatusRepository.recordOpenGlActive(appContext, it)
        }
    }

    private fun fallbackToOpenGl(failedHost: VulkanGlassHost, reason: String) {
        val currentEngine = synchronized(lock) {
            if (closed || activeHost !== failedHost) return
            engine ?: return
        }
        runCatching { VulkanSupport.recordFailure(appContext, effectId, reason) }
            .onFailure { failure ->
                Log.w(TAG, "Unable to persist the Vulkan fallback state", failure)
            }
        val fallback = runCatching {
            val renderer = createOpenGlRenderer(currentEngine)
            renderer to currentEngine.createOpenGlRenderHost(renderer)
        }.getOrElse { failure ->
            Log.e(TAG, "Unable to create the OpenGL ES Glass fallback", failure)
            return
        }
        val (renderer, replacement) = fallback
        if (!currentEngine.replaceRenderHost(failedHost, replacement)) {
            renderer.release()
            Log.e(TAG, "Unable to attach the OpenGL ES Glass fallback")
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
        publishStatus {
            RendererRuntimeStatusRepository.recordOpenGlActive(
                context = appContext,
                session = it,
                reason = reason
            )
        }
        renderer.reloadTexture()
        currentEngine.requestRender()
        Log.w(TAG, "Glass switched to OpenGL ES after Vulkan failed: $reason")
    }

    private fun onVulkanActive(host: VulkanGlassHost, packedVersion: Int) {
        if (synchronized(lock) { !closed && activeHost === host }) {
            publishStatus {
                RendererRuntimeStatusRepository.recordVulkanActive(
                    context = appContext,
                    session = it,
                    packedVersion = packedVersion
                )
            }
        }
    }

    private fun createOpenGlRenderer(
        engine: GLWallpaperService.GLEngine
    ): GlassRenderer {
        val snapshot = synchronized(lock) { state }
        return GlassRenderer(appContext).apply {
            progress = snapshot.progress
            dimLevel = snapshot.dimLevel
            lineCount = snapshot.lineCount
            lineThickness = snapshot.lineThickness
            transitionStyle = snapshot.transitionStyle
            configureBackgroundOnly(snapshot.backgroundOnly)
            onRenderRetryRequested = engine::requestRender
            onSubjectMaskUpdated = engine::requestRender
        }
    }

    private fun applyState(snapshot: GlassRenderState) {
        val targets = synchronized(lock) {
            openGlRenderer to vulkanHost
        }
        targets.first?.apply {
            progress = snapshot.progress
            dimLevel = snapshot.dimLevel
            lineCount = snapshot.lineCount
            lineThickness = snapshot.lineThickness
            transitionStyle = snapshot.transitionStyle
            configureBackgroundOnly(snapshot.backgroundOnly)
        }
        targets.second?.updateState(snapshot)
    }

    private inline fun publishStatus(
        session: RendererRuntimeSession? = synchronized(lock) { runtimeSession },
        block: (RendererRuntimeSession) -> Unit
    ) {
        if (session == null) return
        runCatching { block(session) }.onFailure { failure ->
            Log.w(TAG, "Unable to publish the Glass renderer status", failure)
        }
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private companion object {
        const val TAG = "GlassRenderController"
    }
}
