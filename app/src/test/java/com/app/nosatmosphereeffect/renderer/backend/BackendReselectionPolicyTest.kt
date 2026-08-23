package com.app.nosatmosphereeffect.renderer.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendReselectionPolicyTest {
    @Test
    fun unchangedPreferenceDoesNotTouchTheActiveHost() {
        assertEquals(
            BackendReselectionAction.NONE,
            BackendReselectionPolicy.decide(
                appliedPreference = GraphicsBackendPreference.AUTOMATIC,
                requestedPreference = GraphicsBackendPreference.AUTOMATIC,
                activeBackend = GraphicsBackend.VULKAN,
                resolvedBackend = GraphicsBackend.OPENGL_ES
            )
        )
    }

    @Test
    fun preferenceCanChangeWithoutReplacingAnEquivalentBackend() {
        assertEquals(
            BackendReselectionAction.REFRESH_ACTIVE_SESSION,
            BackendReselectionPolicy.decide(
                appliedPreference = GraphicsBackendPreference.AUTOMATIC,
                requestedPreference = GraphicsBackendPreference.VULKAN,
                activeBackend = GraphicsBackend.VULKAN,
                resolvedBackend = GraphicsBackend.VULKAN
            )
        )
    }

    @Test
    fun sameOpenGlHostRefreshesStatusWhenPreferenceChanges() {
        listOf(
            GraphicsBackendPreference.VULKAN to GraphicsBackendPreference.OPENGL_ES,
            GraphicsBackendPreference.OPENGL_ES to GraphicsBackendPreference.AUTOMATIC
        ).forEach { (applied, requested) ->
            assertEquals(
                BackendReselectionAction.REFRESH_ACTIVE_SESSION,
                BackendReselectionPolicy.decide(
                    appliedPreference = applied,
                    requestedPreference = requested,
                    activeBackend = GraphicsBackend.OPENGL_ES,
                    resolvedBackend = GraphicsBackend.OPENGL_ES
                )
            )
        }
    }

    @Test
    fun differentResolvedBackendRequiresAHostSwapInBothDirections() {
        listOf(
            GraphicsBackend.OPENGL_ES to GraphicsBackend.VULKAN,
            GraphicsBackend.VULKAN to GraphicsBackend.OPENGL_ES
        ).forEach { (active, resolved) ->
            assertEquals(
                BackendReselectionAction.SWAP_BACKEND,
                BackendReselectionPolicy.decide(
                    appliedPreference = GraphicsBackendPreference.OPENGL_ES,
                    requestedPreference = GraphicsBackendPreference.AUTOMATIC,
                    activeBackend = active,
                    resolvedBackend = resolved
                )
            )
        }
    }
}
