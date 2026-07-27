package com.app.nosatmosphereeffect.renderer.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphicsBackendSelectorTest {
    @Test
    fun healthyVulkanIsSelectedForBothColorFillDirections() {
        listOf("COLORFILL", "COLORFILL_REVERSE").forEach { effectId ->
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
    fun everyOtherEffectRemainsOnOpenGl() {
        listOf("ATMOSPHERE", "GLASS", "HALFTONE", "FROSTED", "NEON").forEach {
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
        unavailableSignals.forEach { (feature, probe, blocked) ->
            assertEquals(
                GraphicsBackend.OPENGL_ES,
                GraphicsBackendSelector.select(
                    effectId = "COLORFILL",
                    hasVulkan11 = feature,
                    nativeProbePassed = probe,
                    blockedAfterFailure = blocked
                )
            )
        }
    }
}
