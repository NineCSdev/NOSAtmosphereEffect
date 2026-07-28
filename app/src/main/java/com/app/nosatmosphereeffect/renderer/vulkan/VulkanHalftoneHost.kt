package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.graphics.Bitmap
import com.app.nosatmosphereeffect.helper.SubjectMaskCoordinator
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.HalftoneRenderState
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageBridge
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageHost

internal class VulkanHalftoneHost(
    context: Context,
    private val reverse: Boolean,
    initialState: HalftoneRenderState,
    onFatalFailure: (WallpaperRenderHost, String) -> Unit,
    onVulkanActive: (WallpaperRenderHost, Int) -> Unit,
    previewSource: (() -> Bitmap?)? = null
) : VulkanSingleImageHost<HalftoneRenderState>(
    context = context,
    threadName = "AtmoVulkanHalftone",
    initialState = initialState.sanitized(),
    bridge = HalftoneBridge(reverse),
    onFatalFailure = onFatalFailure,
    onVulkanActive = onVulkanActive,
    previewSource = previewSource
) {
    private val subjectMasks = SubjectMaskCoordinator(context, ::requestRender)

    init {
        subjectMasks.configure(initialState.backgroundOnly)
        startNativeEngine()
    }

    fun updateState(state: HalftoneRenderState) {
        val sanitized = state.sanitized()
        val backgroundModeChanged =
            subjectMasks.configure(sanitized.backgroundOnly)
        updateEffectState { current ->
            sanitized.copy(
                hasSubject = if (backgroundModeChanged) {
                    false
                } else {
                    current.hasSubject && sanitized.backgroundOnly
                }
            ).sanitized()
        }
        if (sanitized.backgroundOnly && backgroundModeChanged) {
            reloadTexture()
        }
    }

    override fun onWallpaperUploadedOnWorker(
        handle: Long,
        bitmap: Bitmap,
        textureGeneration: Long
    ): Boolean {
        updateEffectState { current ->
            current.copy(hasSubject = false).sanitized()
        }
        if (!VulkanHalftoneNative.nativeClearSubjectMask(handle)) {
            return false
        }
        subjectMasks.request(bitmap, textureGeneration)
        return true
    }

    override fun prepareFrameOnWorker(
        handle: Long,
        textureGeneration: Long
    ): Boolean {
        val pending = subjectMasks.takePending() ?: return true
        try {
            if (
                pending.generation != textureGeneration ||
                !subjectMasks.enabled
            ) {
                return true
            }
            if (!VulkanHalftoneNative.nativeUploadSubjectMask(handle, pending.bitmap)) {
                return false
            }
            updateEffectState { current ->
                current.copy(hasSubject = true).sanitized()
            }
            return true
        } finally {
            if (!pending.bitmap.isRecycled) pending.bitmap.recycle()
        }
    }

    override fun onSurfaceResetOnWorker() {
        subjectMasks.discardPending()
        updateEffectState { current ->
            current.copy(hasSubject = false).sanitized()
        }
    }

    override fun onEffectResourcesReleased() {
        subjectMasks.close()
    }
}

private class HalftoneBridge(
    private val reverse: Boolean
) : VulkanSingleImageBridge<HalftoneRenderState> {
    override val effectLabel = "Halftone"

    override fun create(assets: android.content.res.AssetManager): Long {
        if (!VulkanHalftoneNative.libraryLoaded) return 0L
        return VulkanHalftoneNative.nativeCreate(assets, reverse)
    }

    override fun setSurface(
        handle: Long,
        surface: android.view.Surface,
        width: Int,
        height: Int
    ): Boolean {
        return VulkanHalftoneNative.nativeSetSurface(
            handle,
            surface,
            width,
            height
        )
    }

    override fun getApiVersion(handle: Long): Int {
        return VulkanHalftoneNative.nativeGetApiVersion(handle)
    }

    override fun uploadWallpaper(handle: Long, bitmap: Bitmap): Boolean {
        return VulkanHalftoneNative.nativeUploadWallpaper(handle, bitmap)
    }

    override fun setState(
        handle: Long,
        state: HalftoneRenderState,
        scrollOffsetX: Float,
        scrollWindowX: Float
    ) {
        val safe = state.sanitized()
        VulkanHalftoneNative.nativeSetState(
            handle = handle,
            progress = safe.progress,
            dimLevel = safe.dimLevel,
            dotSize = safe.dotSize,
            grayscale = safe.grayscale,
            backgroundOnly = safe.backgroundOnly,
            hasSubject = safe.hasSubject,
            scrollOffsetX = scrollOffsetX,
            scrollWindowX = scrollWindowX
        )
    }

    override fun render(handle: Long): Int {
        return VulkanHalftoneNative.nativeRender(handle)
    }

    override fun destroySurface(handle: Long) {
        VulkanHalftoneNative.nativeDestroySurface(handle)
    }

    override fun destroy(handle: Long) {
        VulkanHalftoneNative.nativeDestroy(handle)
    }
}
