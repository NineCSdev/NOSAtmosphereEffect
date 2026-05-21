package com.app.nosatmosphereeffect.service

import android.opengl.GLSurfaceView
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.renderer.ColorFillRenderer

class ColorFillService : GLWallpaperService() {
    private var renderer: ColorFillRenderer? = null

    override fun getRenderer(): GLSurfaceView.Renderer {
        renderer = ColorFillRenderer(this, isReverse = false)
        return renderer!!
    }

    // You can wire up BroadcastReceivers here (just like your other services)
    // to listen to lock/unlock broadcast events and update `renderer?.blurStrength = ...`
}