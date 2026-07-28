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
                    blockedAfterFailure = false,
                    preference = GraphicsBackendPreference.AUTOMATIC
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
                    blockedAfterFailure = false,
                    preference = GraphicsBackendPreference.AUTOMATIC
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
                        blockedAfterFailure = blocked,
                        preference = GraphicsBackendPreference.VULKAN
                    )
                )
            }
        }
    }

    @Test
    fun explicitOpenGlAlwaysWinsWithoutVulkanChecks() {
        assertEquals(
            GraphicsBackend.OPENGL_ES,
            GraphicsBackendSelector.select(
                effectId = "ORIGINAL",
                hasVulkan11 = true,
                nativeProbePassed = true,
                blockedAfterFailure = false,
                preference = GraphicsBackendPreference.OPENGL_ES
            )
        )
    }

    @Test
    fun explicitVulkanStillHonorsRuntimeFailureBlock() {
        assertEquals(
            GraphicsBackend.OPENGL_ES,
            GraphicsBackendSelector.select(
                effectId = "GLASS",
                hasVulkan11 = true,
                nativeProbePassed = true,
                blockedAfterFailure = true,
                preference = GraphicsBackendPreference.VULKAN
            )
        )
    }

    @Test
    fun explicitVulkanUsesVulkanWhenEverySafetyCheckPasses() {
        assertEquals(
            GraphicsBackend.VULKAN,
            GraphicsBackendSelector.select(
                effectId = "COLORFILL",
                hasVulkan11 = true,
                nativeProbePassed = true,
                blockedAfterFailure = false,
                preference = GraphicsBackendPreference.VULKAN
            )
        )
    }

    @Test
    fun malformedStoredPreferenceUsesAutomaticMode() {
        assertEquals(
            GraphicsBackendPreference.AUTOMATIC,
            GraphicsBackendPreference.fromStoredValue("metal")
        )
        assertEquals(
            GraphicsBackendPreference.AUTOMATIC,
            GraphicsBackendPreference.fromStoredValue(null)
        )
        GraphicsBackendPreference.entries.forEach { preference ->
            assertEquals(
                preference,
                GraphicsBackendPreference.fromStoredValue(preference.storedValue)
            )
        }
    }
}
