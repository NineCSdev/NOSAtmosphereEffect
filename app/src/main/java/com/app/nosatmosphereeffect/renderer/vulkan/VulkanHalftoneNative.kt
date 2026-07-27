package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

internal object VulkanHalftoneNative {
    val libraryLoaded: Boolean
        get() = VulkanNative.libraryLoaded

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

    external fun nativeUploadWallpaper(
        handle: Long,
        bitmap: Bitmap
    ): Boolean

    external fun nativeUploadSubjectMask(
        handle: Long,
        bitmap: Bitmap
    ): Boolean

    external fun nativeClearSubjectMask(handle: Long): Boolean

    external fun nativeSetState(
        handle: Long,
        progress: Float,
        dimLevel: Float,
        dotSize: Float,
        grayscale: Boolean,
        backgroundOnly: Boolean,
        hasSubject: Boolean,
        scrollOffsetX: Float,
        scrollWindowX: Float
    )

    external fun nativeRender(handle: Long): Int

    external fun nativeDestroySurface(handle: Long)

    external fun nativeDestroy(handle: Long)
}
