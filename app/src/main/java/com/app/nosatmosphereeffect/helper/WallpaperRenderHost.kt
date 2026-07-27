package com.app.nosatmosphereeffect.helper

import android.view.SurfaceHolder

interface WallpaperRenderHost {
    fun onSurfaceCreated(holder: SurfaceHolder)

    fun onSurfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    )

    fun onSurfaceDestroyed(holder: SurfaceHolder)

    fun onResume()

    fun onPause()

    fun requestRender()

    fun setWallpaperOffset(xOffset: Float)

    fun close()
}
