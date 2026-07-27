package com.app.nosatmosphereeffect.renderer.backend

enum class GraphicsBackend {
    OPENGL_ES,
    VULKAN
}

object GraphicsBackendSelector {
    private val vulkanEffects = setOf(
        "ORIGINAL",
        "REVERSE",
        "GLASS",
        "GLASS_REVERSE",
        "COLORFILL",
        "COLORFILL_REVERSE",
        "NEON",
        "NEON_REVERSE",
        "FROSTED",
        "FROSTED_REVERSE",
        "HALFTONE",
        "HALFTONE_REVERSE"
    )

    fun select(
        effectId: String,
        hasVulkan11: Boolean,
        nativeProbePassed: Boolean,
        blockedAfterFailure: Boolean
    ): GraphicsBackend {
        return if (
            effectId in vulkanEffects &&
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
