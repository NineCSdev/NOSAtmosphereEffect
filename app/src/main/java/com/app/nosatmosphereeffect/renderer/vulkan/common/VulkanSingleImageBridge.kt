package com.app.nosatmosphereeffect.renderer.vulkan.common

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

internal interface VulkanSingleImageBridge<State : Any> {
    val effectLabel: String

    fun create(assets: AssetManager): Long

    fun setSurface(
        handle: Long,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean

    fun getApiVersion(handle: Long): Int

    fun uploadWallpaper(
        handle: Long,
        bitmap: Bitmap
    ): Boolean

    fun setState(
        handle: Long,
        state: State,
        scrollOffsetX: Float,
        scrollWindowX: Float
    )

    fun render(handle: Long): Int

    fun destroySurface(handle: Long)

    fun destroy(handle: Long)
}
