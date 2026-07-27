package com.app.nosatmosphereeffect.ui.model

import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimePhase
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatus
import com.app.nosatmosphereeffect.renderer.status.VulkanDeviceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererStatusUiModelTest {

    @Test
    fun vulkanStateKeepsTrimmedVersion() {
        val status = RendererStatusUiModel.vulkanActive(" 1.3 ")

        assertTrue(status.isVulkanActive)
        assertEquals(RendererBackendUi.VULKAN, status.backend)
        assertEquals("1.3", status.vulkanVersion)
    }

    @Test
    fun missingVersionNeverClaimsVulkanIsActive() {
        listOf(null, "", "   ").forEach { version ->
            val status = RendererStatusUiModel.vulkanActive(version)

            assertFalse(status.isVulkanActive)
            assertEquals(RendererBackendUi.OPENGL_ES, status.backend)
            assertNull(status.vulkanVersion)
        }
    }

    @Test
    fun explicitFallbackContainsNoVulkanVersion() {
        val status = RendererStatusUiModel.openGlFallback()

        assertFalse(status.isVulkanActive)
        assertEquals(RendererBackendUi.OPENGL_ES, status.backend)
        assertNull(status.vulkanVersion)
    }

    @Test
    fun contradictoryBackendStatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RendererStatusUiModel(RendererBackendUi.VULKAN, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RendererStatusUiModel(RendererBackendUi.OPENGL_ES, "1.3")
        }
    }

    @Test
    fun activeColorFillShowsTheVersionThatActuallyPresented() {
        val status = runtimeStatus(
            activeBackend = GraphicsBackend.VULKAN,
            activeVulkanVersion = 0x00404000
        )

        val ui = rendererStatusUiModel(
            wallpaperActive = true,
            activeEffectId = "COLORFILL",
            runtimeStatus = status
        )

        assertEquals(RendererBackendUi.VULKAN, ui?.backend)
        assertEquals("1.4", ui?.vulkanVersion)
    }

    @Test
    fun initializingColorFillDoesNotClaimThatVulkanIsActive() {
        val status = runtimeStatus(
            phase = RendererRuntimePhase.INITIALIZING,
            activeBackend = null,
            activeVulkanVersion = null
        )

        assertNull(
            rendererStatusUiModel(
                wallpaperActive = true,
                activeEffectId = "COLORFILL",
                runtimeStatus = status
            )
        )
    }

    @Test
    fun colorFillFallbackAndOtherEffectsReportOpenGl() {
        val fallback = runtimeStatus(
            activeBackend = GraphicsBackend.OPENGL_ES,
            activeVulkanVersion = null
        )

        val colorFillUi = rendererStatusUiModel(
            wallpaperActive = true,
            activeEffectId = "COLORFILL",
            runtimeStatus = fallback
        )
        val glassUi = rendererStatusUiModel(
            wallpaperActive = true,
            activeEffectId = "GLASS",
            runtimeStatus = RendererRuntimeStatus.idle()
        )

        assertEquals(RendererBackendUi.OPENGL_ES, colorFillUi?.backend)
        assertEquals(RendererBackendUi.OPENGL_ES, glassUi?.backend)
    }

    @Test
    fun inactiveWallpaperAndStaleEffectStatusStayHidden() {
        val activeStatus = runtimeStatus(
            activeBackend = GraphicsBackend.VULKAN,
            activeVulkanVersion = 0x00403000
        )

        assertNull(
            rendererStatusUiModel(
                wallpaperActive = false,
                activeEffectId = null,
                runtimeStatus = activeStatus
            )
        )
        assertNull(
            rendererStatusUiModel(
                wallpaperActive = true,
                activeEffectId = "COLORFILL_REVERSE",
                runtimeStatus = activeStatus
            )
        )
    }

    private fun runtimeStatus(
        phase: RendererRuntimePhase = RendererRuntimePhase.ACTIVE,
        activeBackend: GraphicsBackend?,
        activeVulkanVersion: Int?
    ): RendererRuntimeStatus {
        return RendererRuntimeStatus(
            phase = phase,
            effectId = "COLORFILL",
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = 0x00404000,
            selectedBackend = GraphicsBackend.VULKAN,
            activeBackend = activeBackend,
            activeVulkanApiVersion = activeVulkanVersion,
            fallbackReason = null,
            updatedAtEpochMillis = 1L
        )
    }
}
