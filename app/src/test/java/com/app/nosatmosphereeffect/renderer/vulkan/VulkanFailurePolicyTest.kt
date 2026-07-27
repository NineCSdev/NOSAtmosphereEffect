package com.app.nosatmosphereeffect.renderer.vulkan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanFailurePolicyTest {
    @Test
    fun `a scoped driver failure blocks only that effect`() {
        assertTrue(
            VulkanFailurePolicy.isBlocked(
                effectId = "GLASS",
                currentFailureId = CURRENT,
                scopedFailureId = CURRENT,
                legacyFailureId = null
            )
        )
        assertFalse(
            VulkanFailurePolicy.isBlocked(
                effectId = "HALFTONE",
                currentFailureId = CURRENT,
                scopedFailureId = "another-build",
                legacyFailureId = null
            )
        )
    }

    @Test
    fun `the old global failure protects only color fill`() {
        assertTrue(
            VulkanFailurePolicy.isBlocked(
                effectId = "colorfill_reverse",
                currentFailureId = CURRENT,
                scopedFailureId = null,
                legacyFailureId = CURRENT
            )
        )
        assertFalse(
            VulkanFailurePolicy.isBlocked(
                effectId = "ORIGINAL",
                currentFailureId = CURRENT,
                scopedFailureId = null,
                legacyFailureId = CURRENT
            )
        )
    }

    private companion object {
        const val CURRENT = "device-and-version"
    }
}
