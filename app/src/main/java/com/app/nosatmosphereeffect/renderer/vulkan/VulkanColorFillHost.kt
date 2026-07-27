package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.ColorFillRenderState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class VulkanColorFillHost(
    context: Context,
    private val reverse: Boolean,
    initialState: ColorFillRenderState,
    private val onFatalFailure: (VulkanColorFillHost, String) -> Unit,
    private val onVulkanActive: (VulkanColorFillHost, Int) -> Unit = { _, _ -> }
) : WallpaperRenderHost {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderThread = HandlerThread("AtmoVulkanColorFill").apply { start() }
    private val worker = Handler(renderThread.looper)
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val renderQueued = AtomicBoolean(false)
    private val activeReported = AtomicBoolean(false)

    private val latestState = AtomicReference(initialState.sanitized())

    @Volatile
    private var surfaceGeneration = 0L

    @Volatile
    private var latestSurface: Surface? = null

    @Volatile
    private var latestWidth = 0

    @Volatile
    private var latestHeight = 0

    private var nativeHandle = 0L
    private var ready = false
    private var readyGeneration = NO_SURFACE_GENERATION

    @Volatile
    var initializedApiVersion: VulkanApiVersion? = null
        private set

    private var paused = false
    private var needsReload = true
    private var pendingPlaylistBitmap: Bitmap? = null

    init {
        worker.post {
            if (!VulkanNative.libraryLoaded) {
                failOnWorker("The Vulkan native library could not be loaded")
                return@post
            }
            nativeHandle = runCatching {
                VulkanNative.nativeCreate(appContext.assets, reverse)
            }.getOrElse { failure ->
                Log.e(TAG, "Unable to create the native Color Fill engine", failure)
                0L
            }
            if (nativeHandle == 0L) {
                failOnWorker("The Vulkan Color Fill engine could not be created")
            }
        }
    }

    fun updateState(state: ColorFillRenderState) {
        val sanitized = state.sanitized()
        latestState.updateAndGet { current ->
            current.copy(
                progress = sanitized.progress,
                dimLevel = sanitized.dimLevel,
                originX = sanitized.originX,
                originY = sanitized.originY
            ).sanitized()
        }
        postIfActive {
            applyStateOnWorker()
        }
    }

    fun reloadTexture() {
        postIfActive {
            needsReload = true
            val generation = surfaceGeneration
            if (ready && loadActiveTextureOnWorker(generation)) {
                drawOnWorker(generation)
            }
        }
    }

    fun queuePlaylistTransition(bitmap: Bitmap) {
        if (closed.get() || failed.get()) {
            bitmap.recycleSafely()
            return
        }
        val accepted = worker.post {
            if (closed.get() || failed.get()) {
                bitmap.recycleSafely()
                return@post
            }
            val generation = surfaceGeneration
            if (!isReadyForGeneration(generation)) {
                pendingPlaylistBitmap?.recycleSafely()
                pendingPlaylistBitmap = bitmap
                return@post
            }
            if (uploadPlaylistBitmapOnWorker(bitmap, generation)) {
                drawOnWorker(generation)
            }
        }
        if (!accepted) bitmap.recycleSafely()
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
        latestSurface = holder.surface
    }

    override fun onSurfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        if (width <= 0 || height <= 0) return
        val generation = ++surfaceGeneration
        val surface = holder.surface
        latestSurface = surface
        latestWidth = width
        latestHeight = height
        postIfActive {
            if (generation != surfaceGeneration) return@postIfActive
            initializeSurfaceOnWorker(surface, width, height, generation)
        }
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
        ++surfaceGeneration
        latestSurface = null
        latestWidth = 0
        latestHeight = 0
        postIfActive {
            ready = false
            readyGeneration = NO_SURFACE_GENERATION
            initializedApiVersion = null
            if (nativeHandle != 0L) {
                VulkanNative.nativeDestroySurface(nativeHandle)
            }
        }
    }

    override fun onResume() {
        postIfActive {
            paused = false
            drawOnWorker()
        }
    }

    override fun onPause() {
        postIfActive {
            paused = true
        }
    }

    override fun requestRender() {
        if (closed.get() || failed.get()) return
        if (!renderQueued.compareAndSet(false, true)) return
        worker.post {
            renderQueued.set(false)
            if (!closed.get() && !failed.get()) {
                drawOnWorker()
            }
        }
    }

    override fun setWallpaperOffset(xOffset: Float) {
        latestState.updateAndGet { current ->
            current.copy(scrollOffsetX = xOffset).sanitized()
        }
        postIfActive {
            applyStateOnWorker()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (failed.get()) return
        worker.post {
            releaseNativeOnWorker()
            renderThread.quitSafely()
        }
    }

    private fun initializeSurfaceOnWorker(
        surface: Surface,
        width: Int,
        height: Int,
        generation: Long
    ) {
        if (nativeHandle == 0L) {
            failOnWorker("The Vulkan Color Fill engine is unavailable")
            return
        }
        if (!isCurrentSurface(surface, generation)) {
            discardStaleSurfaceOnWorker()
            return
        }

        ready = false
        readyGeneration = NO_SURFACE_GENERATION
        initializedApiVersion = null
        val initialized = runCatching {
            VulkanNative.nativeSetSurface(nativeHandle, surface, width, height)
        }.getOrElse { failure ->
            Log.e(TAG, "Unable to initialize the Vulkan wallpaper surface", failure)
            false
        }
        if (!isCurrentSurface(surface, generation)) {
            discardStaleSurfaceOnWorker()
            return
        }
        if (!initialized) {
            failOnWorker("The Vulkan swapchain could not be initialized")
            return
        }
        val apiVersion = runCatching {
            VulkanApiVersion.fromEncoded(
                VulkanNative.nativeGetApiVersion(nativeHandle)
            )
        }.getOrNull()
        if (apiVersion == null) {
            failOnWorker("The initialized Vulkan API version is unavailable")
            return
        }
        initializedApiVersion = apiVersion
        ready = true
        readyGeneration = generation

        val pending = pendingPlaylistBitmap
        pendingPlaylistBitmap = null
        val textureReady = if (pending != null) {
            uploadPlaylistBitmapOnWorker(pending, generation)
        } else {
            loadActiveTextureOnWorker(generation)
        }
        if (!textureReady) return
        if (!isCurrentSurface(surface, generation)) {
            discardStaleSurfaceOnWorker()
            return
        }
        drawOnWorker(generation)
    }

    private fun loadActiveTextureOnWorker(generation: Long): Boolean {
        val width = latestWidth
        val height = latestHeight
        if (!isReadyForGeneration(generation) || width <= 0 || height <= 0) {
            needsReload = true
            return false
        }
        val renderImage = runCatching {
            WallpaperFitHelper.loadForRender(appContext, width, height)
        }.getOrElse { failure ->
            if (isCurrentGeneration(generation)) {
                failOnWorker("The active wallpaper could not be prepared: ${failure.message}")
            }
            return false
        }
        return uploadRenderImageOnWorker(renderImage, generation)
    }

    private fun uploadPlaylistBitmapOnWorker(bitmap: Bitmap, generation: Long): Boolean {
        val width = latestWidth
        val height = latestHeight
        if (!isReadyForGeneration(generation) || width <= 0 || height <= 0) {
            pendingPlaylistBitmap?.recycleSafely()
            pendingPlaylistBitmap = bitmap
            return false
        }
        val renderImage = runCatching {
            WallpaperFitHelper.fitForRender(appContext, bitmap, width, height)
        }.getOrElse { failure ->
            bitmap.recycleSafely()
            if (isCurrentGeneration(generation)) {
                failOnWorker("The playlist wallpaper could not be prepared: ${failure.message}")
            }
            return false
        }
        return uploadRenderImageOnWorker(renderImage, generation)
    }

    private fun uploadRenderImageOnWorker(
        renderImage: WallpaperFitHelper.RenderImage,
        generation: Long
    ): Boolean {
        val bitmap = renderImage.bitmap
        if (!isReadyForGeneration(generation)) {
            bitmap.recycleSafely()
            needsReload = true
            return false
        }
        val uploadSucceeded = try {
            if (nativeHandle == 0L) {
                false
            } else {
                VulkanNative.nativeUploadBitmap(nativeHandle, bitmap)
            }
        } catch (failure: RuntimeException) {
            Log.e(TAG, "Unable to upload the Color Fill texture", failure)
            false
        } finally {
            bitmap.recycleSafely()
        }

        if (!isCurrentGeneration(generation)) {
            discardStaleSurfaceOnWorker()
            return false
        }
        if (!uploadSucceeded) {
            failOnWorker("The wallpaper texture could not be uploaded to Vulkan")
            return false
        }

        latestState.updateAndGet { current ->
            current.copy(scrollWindowX = renderImage.windowX).sanitized()
        }
        needsReload = false
        applyStateOnWorker()
        return true
    }

    private fun applyStateOnWorker() {
        val handle = nativeHandle
        if (handle == 0L || failed.get()) return
        val state = latestState.get()
        VulkanNative.nativeSetState(
            handle = handle,
            progress = state.progress,
            dimLevel = state.dimLevel,
            originX = state.originX,
            originY = state.originY,
            scrollOffsetX = state.scrollOffsetX,
            scrollWindowX = state.scrollWindowX
        )
    }

    private fun drawOnWorker(generation: Long = surfaceGeneration) {
        if (paused || failed.get() || nativeHandle == 0L) return
        if (!isReadyForGeneration(generation)) {
            discardStaleSurfaceOnWorker()
            return
        }
        if (needsReload && !loadActiveTextureOnWorker(generation)) return
        applyStateOnWorker()
        val result = runCatching {
            VulkanNative.nativeRender(nativeHandle)
        }.getOrDefault(RENDER_FATAL)
        if (!isCurrentGeneration(generation)) {
            discardStaleSurfaceOnWorker()
            return
        }
        when (result) {
            RENDER_SUCCESS -> reportVulkanActive(apiVersion = initializedApiVersion)
            RENDER_RECREATE -> {
                val surface = latestSurface
                val width = latestWidth
                val height = latestHeight
                if (surface == null || width <= 0 || height <= 0) return
                initializeSurfaceOnWorker(surface, width, height, generation)
            }
            else -> failOnWorker("The Vulkan driver failed while presenting Color Fill")
        }
    }

    private fun reportVulkanActive(apiVersion: VulkanApiVersion?) {
        if (apiVersion == null || !activeReported.compareAndSet(false, true)) return
        mainHandler.post {
            onVulkanActive(this, apiVersion.encoded)
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean {
        return generation == surfaceGeneration &&
            latestSurface?.isValid == true &&
            latestWidth > 0 &&
            latestHeight > 0
    }

    private fun isCurrentSurface(surface: Surface, generation: Long): Boolean {
        return latestSurface === surface && isCurrentGeneration(generation)
    }

    private fun isReadyForGeneration(generation: Long): Boolean {
        return ready && readyGeneration == generation && isCurrentGeneration(generation)
    }

    private fun discardStaleSurfaceOnWorker() {
        ready = false
        readyGeneration = NO_SURFACE_GENERATION
        initializedApiVersion = null
        needsReload = true
        if (nativeHandle != 0L) {
            runCatching { VulkanNative.nativeDestroySurface(nativeHandle) }
                .onFailure { failure ->
                    Log.w(TAG, "Unable to discard a stale Vulkan surface", failure)
                }
        }
    }

    private fun postIfActive(action: () -> Unit) {
        if (closed.get() || failed.get()) return
        worker.post {
            if (!closed.get() && !failed.get()) action()
        }
    }

    private fun failOnWorker(reason: String) {
        if (!failed.compareAndSet(false, true)) return
        Log.e(TAG, reason)
        releaseNativeOnWorker()
        renderThread.quitSafely()
        mainHandler.post {
            onFatalFailure(this, reason)
        }
    }

    private fun releaseNativeOnWorker() {
        ready = false
        readyGeneration = NO_SURFACE_GENERATION
        initializedApiVersion = null
        pendingPlaylistBitmap?.recycleSafely()
        pendingPlaylistBitmap = null
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) {
            runCatching { VulkanNative.nativeDestroy(handle) }
                .onFailure { failure ->
                    Log.e(TAG, "Unable to destroy the Vulkan Color Fill engine", failure)
                }
        }
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private companion object {
        const val TAG = "VulkanColorFill"
        const val RENDER_SUCCESS = 0
        const val RENDER_RECREATE = 1
        const val RENDER_FATAL = -1
        const val NO_SURFACE_GENERATION = -1L
    }
}
