package com.app.nosatmosphereeffect.service

import android.opengl.GLSurfaceView
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.renderer.ColorFillRenderer

class ColorFillReverseService : GLWallpaperService() {
    private var renderer: ColorFillRenderer? = null

    override fun getRenderer(): GLSurfaceView.Renderer {
        renderer = ColorFillRenderer(this, isReverse = true)
        return renderer!!
    }
}