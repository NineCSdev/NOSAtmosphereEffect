package com.app.nosatmosphereeffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeSession
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatusRepository
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanFrostedHost
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanSupport

class FrostedRenderController(
    context: Context,
    private val isReverse: Boolean
) {
    private val appContext = context.applicationContext
    private val effectId = if (isReverse) "FROSTED_REVERSE" else "FROSTED"
    private val lock = Any()

    private var state = FrostedRenderState()
    private var engine: GLWallpaperService.GLEngine? = null
    private var activeHost: WallpaperRenderHost? = null
    private var openGlRenderer: FrostedRenderer? = null
    private var vulkanHost: VulkanFrostedHost? = null
    private var runtimeSession: RendererRuntimeSession? = null
    private var closed = false

    fun attach(engine: GLWallpaperService.GLEngine) {
        synchronized(lock) {
            check(this.engine == null) { "The Frosted renderer is already attached" }
            check(!closed) { "The Frosted renderer has been released" }
            this.engine = engine
        }
        val selection = VulkanSupport.selectBackend(appContext, effectId)
        synchronized(lock) {
            runtimeSession = selection.runtimeSession
        }
        when (selection.backend) {
            GraphicsBackend.VULKAN -> attachVulkan(engine)
            GraphicsBackend.OPENGL_ES -> attachOpenGl(engine)
        }
    }

    fun configure(
        dimLevel: Float,
        enableNoise: Boolean,
        noiseScale: Float,
        noiseStrength: Float,
        blurRadius: Float
    ) {
        val result = synchronized(lock) {
            val previousRadius = state.blurRadiusPixels
            state = state.copy(
                dimLevel = dimLevel,
                enableNoise = enableNoise,
                noiseScale = noiseScale,
                noiseStrength = noiseStrength,
                blurRadius = blurRadius
            ).sanitized()
            state to (previousRadius != state.blurRadiusPixels)
        }
        applyState(result.first)
        if (result.second) reloadTexture()
    }

    fun setProgress(progress: Float) {
        val snapshot = synchronized(lock) {
            state = state.copy(progress = progress).sanitized()
            state
        }
        applyState(snapshot)
    }

    fun setDrawerBlurred(blurred: Boolean) {
        val snapshot = synchronized(lock) {
            state = state.copy(
                drawerBlur = if (blurred) 1f else 0f
            ).sanitized()
            state
        }
        applyState(snapshot)
    }

    fun reloadTexture() {
        val gl: FrostedRenderer?
        val vk: VulkanFrostedHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.reloadTexture()
        vk?.reloadTexture()
    }

    fun queuePlaylistTransition(bitmap: Bitmap) {
        val gl: FrostedRenderer?
        val vk: VulkanFrostedHost?
        val isClosed: Boolean
        val snapshot: FrostedRenderState
        synchronized(lock) {
            state = state.copy(progress = 0f).sanitized()
            snapshot = state
            gl = openGlRenderer
            vk = vulkanHost
            isClosed = closed
        }
        when {
            isClosed -> bitmap.recycleSafely()
            vk != null -> {
                vk.updateState(snapshot)
                vk.queuePlaylistTransition(bitmap)
            }
            gl != null -> gl.queuePlaylistTransition(bitmap)
            else -> bitmap.recycleSafely()
        }
    }

    fun release() {
        val vk: VulkanFrostedHost?
        val session: RendererRuntimeSession?
        synchronized(lock) {
            if (closed) return
            closed = true
            vk = vulkanHost
            session = runtimeSession
            vulkanHost = null
            openGlRenderer = null
            activeHost = null
            engine = null
            runtimeSession = null
        }
        vk?.close()
        session?.let {
            publishRendererStatus("releasing the renderer session", it) { runtimeSession ->
                RendererRuntimeStatusRepository.recordReleased(
                    context = appContext,
                    session = runtimeSession
                )
            }
        }
    }

    private fun attachVulkan(engine: GLWallpaperService.GLEngine) {
        publishRendererStatus("marking Vulkan as initializing") { session ->
            RendererRuntimeStatusRepository.recordVulkanInitializing(appContext, session)
        }
        val snapshot = synchronized(lock) { state }
        val host = VulkanFrostedHost(
            context = appContext,
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
        publishRendererStatus("marking OpenGL ES as active") { session ->
            RendererRuntimeStatusRepository.recordOpenGlActive(appContext, session)
        }
    }

    private fun fallbackToOpenGl(failedHost: WallpaperRenderHost, reason: String) {
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
            val renderer = createOpenGlRenderer()
            renderer to currentEngine.createOpenGlRenderHost(renderer)
        }.getOrElse { failure ->
            Log.e(TAG, "Unable to create the OpenGL ES Frosted fallback", failure)
            return
        }
        val (renderer, replacement) = fallback
        if (!currentEngine.replaceRenderHost(failedHost, replacement)) {
            Log.e(TAG, "Unable to attach the OpenGL ES Frosted fallback")
            return
        }
        synchronized(lock) {
            if (closed) return
            openGlRenderer = renderer
            vulkanHost = null
            activeHost = replacement
        }
        publishRendererStatus("publishing the OpenGL ES fallback") { session ->
            RendererRuntimeStatusRepository.recordOpenGlActive(
                context = appContext,
                session = session,
                reason = reason
            )
        }
        renderer.reloadTexture()
        currentEngine.requestRender()
        Log.w(TAG, "Frosted switched to OpenGL ES after Vulkan failed: $reason")
    }

    private fun onVulkanActive(host: WallpaperRenderHost, packedVersion: Int) {
        val isCurrentHost = synchronized(lock) {
            !closed && activeHost === host
        }
        if (!isCurrentHost) return
        publishRendererStatus("marking Vulkan as active") { session ->
            RendererRuntimeStatusRepository.recordVulkanActive(
                context = appContext,
                session = session,
                packedVersion = packedVersion
            )
        }
    }

    private fun createOpenGlRenderer(): FrostedRenderer {
        val snapshot = synchronized(lock) { state }
        return FrostedRenderer(appContext).apply {
            blurStrength = snapshot.progress
            dimLevel = snapshot.dimLevel
            enableNoise = snapshot.enableNoise
            noiseScale = snapshot.noiseScale
            noiseStrength = snapshot.noiseStrength
            blurRadius = snapshot.blurRadius
            setDrawerBlurred(snapshot.drawerBlur > 0.5f)
        }
    }

    private fun applyState(snapshot: FrostedRenderState) {
        val gl: FrostedRenderer?
        val vk: VulkanFrostedHost?
        synchronized(lock) {
            gl = openGlRenderer
            vk = vulkanHost
        }
        gl?.apply {
            blurStrength = snapshot.progress
            dimLevel = snapshot.dimLevel
            enableNoise = snapshot.enableNoise
            noiseScale = snapshot.noiseScale
            noiseStrength = snapshot.noiseStrength
            blurRadius = snapshot.blurRadius
            setDrawerBlurred(snapshot.drawerBlur > 0.5f)
        }
        vk?.updateState(snapshot)
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
        const val TAG = "FrostedController"
    }
}
