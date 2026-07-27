package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface
import com.app.nosatmosphereeffect.renderer.GlassRenderState
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageBridge

internal object VulkanGlassNative {
    external fun nativeCreate(assets: AssetManager): Long

    external fun nativeSetSurface(
        handle: Long,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean

    external fun nativeGetApiVersion(handle: Long): Int

    external fun nativeUploadWallpaper(handle: Long, bitmap: Bitmap): Boolean

    external fun nativeUploadMask(handle: Long, bitmap: Bitmap): Boolean

    external fun nativeClearMask(handle: Long): Boolean

    external fun nativeSetState(
        handle: Long,
        progress: Float,
        lineCount: Float,
        lineThickness: Float,
        transitionStyle: Float,
        scrollOffsetX: Float,
        scrollWindowX: Float,
        dimLevel: Float,
        backgroundOnly: Boolean,
        hasSubject: Boolean
    ): Boolean

    external fun nativeRender(handle: Long): Int

    external fun nativeDestroySurface(handle: Long)

    external fun nativeDestroy(handle: Long)
}

internal object VulkanGlassBridge : VulkanSingleImageBridge<GlassRenderState> {
    override val effectLabel: String = "Glass"

    override fun create(assets: AssetManager): Long {
        return if (VulkanNative.libraryLoaded) {
            VulkanGlassNative.nativeCreate(assets)
        } else {
            0L
        }
    }

    override fun setSurface(
        handle: Long,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean {
        return VulkanGlassNative.nativeSetSurface(
            handle,
            surface,
            width,
            height
        )
    }

    override fun getApiVersion(handle: Long): Int {
        return VulkanGlassNative.nativeGetApiVersion(handle)
    }

    override fun uploadWallpaper(handle: Long, bitmap: Bitmap): Boolean {
        return VulkanGlassNative.nativeUploadWallpaper(handle, bitmap)
    }

    override fun setState(
        handle: Long,
        state: GlassRenderState,
        scrollOffsetX: Float,
        scrollWindowX: Float
    ) {
        val safe = state.sanitized()
        check(VulkanGlassNative.nativeSetState(
            handle = handle,
            progress = safe.progress,
            lineCount = safe.lineCount.toFloat(),
            lineThickness = safe.lineThickness,
            transitionStyle = safe.transitionStyleShaderValue,
            scrollOffsetX = scrollOffsetX,
            scrollWindowX = scrollWindowX,
            dimLevel = safe.dimLevel,
            backgroundOnly = safe.backgroundOnly,
            hasSubject = safe.hasSubject
        )) {
            "The native Vulkan Glass state could not be updated"
        }
    }

    override fun render(handle: Long): Int {
        return VulkanGlassNative.nativeRender(handle)
    }

    override fun destroySurface(handle: Long) {
        VulkanGlassNative.nativeDestroySurface(handle)
    }

    override fun destroy(handle: Long) {
        VulkanGlassNative.nativeDestroy(handle)
    }
}
