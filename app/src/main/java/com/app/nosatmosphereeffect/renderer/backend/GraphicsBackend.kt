package com.app.nosatmosphereeffect.renderer.backend

enum class GraphicsBackend {
    OPENGL_ES,
    VULKAN
}

object GraphicsBackendSelector {
    fun select(
        effectId: String,
        hasVulkan11: Boolean,
        nativeProbePassed: Boolean,
        blockedAfterFailure: Boolean
    ): GraphicsBackend {
        val supportsNativeColorFill = effectId == "COLORFILL" ||
            effectId == "COLORFILL_REVERSE"
        return if (
            supportsNativeColorFill &&
            hasVulkan11 &&
            nativeProbePassed &&
            !blockedAfterFailure
        ) {
            GraphicsBackend.VULKAN
        } else {
            GraphicsBackend.OPENGL_ES
        }
    }
}
