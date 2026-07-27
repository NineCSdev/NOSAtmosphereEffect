package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.FrostedRenderState
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageBridge
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageHost

internal class VulkanFrostedHost(
    context: Context,
    initialState: FrostedRenderState,
    onFatalFailure: (WallpaperRenderHost, String) -> Unit,
    onVulkanActive: (WallpaperRenderHost, Int) -> Unit
) : VulkanSingleImageHost<FrostedRenderState>(
    context = context,
    threadName = "AtmoVulkanFrosted",
    initialState = initialState.sanitized(),
    bridge = FrostedBridge,
    onFatalFailure = onFatalFailure,
    onVulkanActive = onVulkanActive
) {
    init {
        startNativeEngine()
    }

    fun updateState(state: FrostedRenderState) {
        updateEffectState { state.sanitized() }
    }

    override fun onWallpaperUploadedOnWorker(
        handle: Long,
        bitmap: Bitmap,
        textureGeneration: Long
    ): Boolean {
        val radius = currentEffectState().blurRadiusPixels
        if (radius < 1) {
            return VulkanFrostedNative.nativeUploadBlurred(handle, bitmap)
        }

        val blurred = try {
            AtmosphereImageProcessor.createBlurredBitmap(
                source = bitmap,
                radius = radius
            )
        } catch (failure: RuntimeException) {
            Log.e(TAG, "Unable to preblur the Vulkan Frosted wallpaper", failure)
            return false
        } catch (failure: OutOfMemoryError) {
            Log.e(TAG, "Not enough memory to preblur the Frosted wallpaper", failure)
            return false
        }
        return try {
            VulkanFrostedNative.nativeUploadBlurred(handle, blurred)
        } finally {
            if (!blurred.isRecycled) blurred.recycle()
        }
    }

    private companion object {
        const val TAG = "VulkanFrostedHost"
    }
}

private object FrostedBridge :
    VulkanSingleImageBridge<FrostedRenderState> {
    override val effectLabel = "Frosted"

    override fun create(assets: android.content.res.AssetManager): Long {
        if (!VulkanFrostedNative.libraryLoaded) return 0L
        return VulkanFrostedNative.nativeCreate(assets)
    }

    override fun setSurface(
        handle: Long,
        surface: android.view.Surface,
        width: Int,
        height: Int
    ): Boolean {
        return VulkanFrostedNative.nativeSetSurface(
            handle,
            surface,
            width,
            height
        )
    }

    override fun getApiVersion(handle: Long): Int {
        return VulkanFrostedNative.nativeGetApiVersion(handle)
    }

    override fun uploadWallpaper(handle: Long, bitmap: Bitmap): Boolean {
        return VulkanFrostedNative.nativeUploadSharp(handle, bitmap)
    }

    override fun setState(
        handle: Long,
        state: FrostedRenderState,
        scrollOffsetX: Float,
        scrollWindowX: Float
    ) {
        val safe = state.sanitized()
        VulkanFrostedNative.nativeSetState(
            handle = handle,
            progress = safe.progress,
            dimLevel = safe.dimLevel,
            enableNoise = safe.enableNoise,
            noiseScale = safe.noiseScale,
            noiseStrength = safe.noiseStrength,
            drawerBlur = safe.drawerBlur,
            scrollOffsetX = scrollOffsetX,
            scrollWindowX = scrollWindowX
        )
    }

    override fun render(handle: Long): Int {
        return VulkanFrostedNative.nativeRender(handle)
    }

    override fun destroySurface(handle: Long) {
        VulkanFrostedNative.nativeDestroySurface(handle)
    }

    override fun destroy(handle: Long) {
        VulkanFrostedNative.nativeDestroy(handle)
    }
}
