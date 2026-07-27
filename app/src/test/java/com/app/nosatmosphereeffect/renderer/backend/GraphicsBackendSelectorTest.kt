package com.app.nosatmosphereeffect.renderer.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphicsBackendSelectorTest {
    @Test
    fun healthyVulkanIsSelectedForEveryEffectDirection() {
        listOf(
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
        ).forEach { effectId ->
            assertEquals(
                GraphicsBackend.VULKAN,
                GraphicsBackendSelector.select(
                    effectId = effectId,
                    hasVulkan11 = true,
                    nativeProbePassed = true,
                    blockedAfterFailure = false
                )
            )
        }
    }

    @Test
    fun unknownEffectRemainsOnOpenGl() {
        listOf("", "UNKNOWN", "ATMOSPHERE", "original").forEach {
            assertEquals(
                GraphicsBackend.OPENGL_ES,
                GraphicsBackendSelector.select(
                    effectId = it,
                    hasVulkan11 = true,
                    nativeProbePassed = true,
                    blockedAfterFailure = false
                )
            )
        }
    }

    @Test
    fun missingFeatureFailedProbeOrPreviousFailureFallsBack() {
        val unavailableSignals = listOf(
            Triple(false, true, false),
            Triple(true, false, false),
            Triple(true, true, true)
        )
        listOf("ORIGINAL", "COLORFILL", "FROSTED_REVERSE").forEach { effectId ->
            unavailableSignals.forEach { (feature, probe, blocked) ->
                assertEquals(
                    GraphicsBackend.OPENGL_ES,
                    GraphicsBackendSelector.select(
                        effectId = effectId,
                        hasVulkan11 = feature,
                        nativeProbePassed = probe,
                        blockedAfterFailure = blocked
                    )
                )
            }
        }
    }
}
