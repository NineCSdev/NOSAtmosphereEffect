package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

internal object VulkanNative {
    val libraryLoaded: Boolean by lazy {
        runCatching {
            System.loadLibrary("atmo_vulkan")
        }.isSuccess
    }

    external fun nativeProbe(): Int

    external fun nativeCreate(
        assets: AssetManager,
        reverse: Boolean
    ): Long

    external fun nativeSetSurface(
        handle: Long,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean

    external fun nativeGetApiVersion(handle: Long): Int

    external fun nativeUploadBitmap(
        handle: Long,
        bitmap: Bitmap
    ): Boolean

    external fun nativeSetState(
        handle: Long,
        progress: Float,
        dimLevel: Float,
        originX: Float,
        originY: Float,
        scrollOffsetX: Float,
        scrollWindowX: Float
    )

    external fun nativeRender(handle: Long): Int

    external fun nativeDestroySurface(handle: Long)

    external fun nativeDestroy(handle: Long)
}
