package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

internal object VulkanFrostedNative {
    val libraryLoaded: Boolean
        get() = VulkanNative.libraryLoaded

    external fun nativeCreate(assets: AssetManager): Long

    external fun nativeSetSurface(
        handle: Long,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean

    external fun nativeGetApiVersion(handle: Long): Int

    external fun nativeUploadSharp(
        handle: Long,
        bitmap: Bitmap
    ): Boolean

    external fun nativeUploadBlurred(
        handle: Long,
        bitmap: Bitmap
    ): Boolean

    external fun nativeSetState(
        handle: Long,
        progress: Float,
        dimLevel: Float,
        enableNoise: Boolean,
        noiseScale: Float,
        noiseStrength: Float,
        drawerBlur: Float,
        scrollOffsetX: Float,
        scrollWindowX: Float
    )

    external fun nativeRender(handle: Long): Int

    external fun nativeDestroySurface(handle: Long)

    external fun nativeDestroy(handle: Long)
}
