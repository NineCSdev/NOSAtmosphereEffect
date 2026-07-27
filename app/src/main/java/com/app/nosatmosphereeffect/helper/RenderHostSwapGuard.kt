package com.app.nosatmosphereeffect.helper

internal object RenderHostSwapGuard {
    fun prepare(
        host: WallpaperRenderHost,
        replay: (WallpaperRenderHost) -> Unit
    ): RuntimeException? {
        return try {
            replay(host)
            null
        } catch (failure: RuntimeException) {
            runCatching { host.close() }
            failure
        }
    }
}
