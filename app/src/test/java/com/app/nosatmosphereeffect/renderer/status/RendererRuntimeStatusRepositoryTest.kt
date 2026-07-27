package com.app.nosatmosphereeffect.renderer.status

import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRuntimeStatusRepositoryTest {
    @Test
    fun selectionAndInitializationNeverClaimVulkanIsActive() {
        val tracker = tracker()

        val selected = tracker.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.VULKAN,
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = VULKAN_1_3
        )
        val initializing = tracker.recordVulkanInitializing(COLOR_FILL)

        assertEquals(RendererRuntimePhase.SELECTED, selected.phase)
        assertEquals(GraphicsBackend.VULKAN, selected.selectedBackend)
        assertNull(selected.activeBackend)
        assertFalse(selected.isVulkanActive)
        assertEquals(RendererRuntimePhase.INITIALIZING, initializing.phase)
        assertNull(initializing.activeBackend)
        assertFalse(initializing.isVulkanActive)
    }

    @Test
    fun firstSuccessfulPresentCanPublishTheActiveVulkanVersion() {
        val tracker = tracker()
        tracker.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.VULKAN,
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = VULKAN_1_3
        )
        tracker.recordVulkanInitializing(COLOR_FILL)

        val active = tracker.recordVulkanActive(COLOR_FILL, VULKAN_1_2)

        assertEquals(RendererRuntimePhase.ACTIVE, active.phase)
        assertEquals(GraphicsBackend.VULKAN, active.activeBackend)
        assertEquals(VULKAN_1_2, active.activeVulkanApiVersion)
        assertEquals(VULKAN_1_3, active.probedVulkanApiVersion)
        assertTrue(active.isVulkanActive)
    }

    @Test
    fun openGlFallbackRetainsSelectionAndReasonButClearsVulkanVersion() {
        val tracker = tracker()
        tracker.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.VULKAN,
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = VULKAN_1_3
        )
        tracker.recordVulkanInitializing(COLOR_FILL)

        val fallback = tracker.recordOpenGlActive(
            effectId = COLOR_FILL,
            reason = "  Swapchain creation failed  "
        )

        assertEquals(RendererRuntimePhase.ACTIVE, fallback.phase)
        assertEquals(GraphicsBackend.VULKAN, fallback.selectedBackend)
        assertEquals(GraphicsBackend.OPENGL_ES, fallback.activeBackend)
        assertNull(fallback.activeVulkanApiVersion)
        assertEquals("Swapchain creation failed", fallback.fallbackReason)
        assertFalse(fallback.isVulkanActive)
    }

    @Test
    fun anOpenGlSelectionCanDescribeAnUnsupportedDeviceWithoutPretendingItIsActive() {
        val tracker = tracker()

        val selected = tracker.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.OPENGL_ES,
            vulkanCapability = VulkanDeviceCapability.UNSUPPORTED,
            probedVulkanApiVersion = null,
            fallbackReason = "Vulkan 1.1 is not advertised by this device"
        )

        assertEquals(VulkanDeviceCapability.UNSUPPORTED, selected.vulkanCapability)
        assertEquals(GraphicsBackend.OPENGL_ES, selected.selectedBackend)
        assertNull(selected.activeBackend)
        assertFalse(selected.isVulkanActive)
    }

    @Test
    fun persistedActiveStateNeedsConfirmationFromTheNewProcess() {
        val store = FakeStore()
        val firstProcess = tracker(store = store, processSessionId = "process-a")
        firstProcess.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.VULKAN,
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = VULKAN_1_3
        )
        firstProcess.recordVulkanActive(COLOR_FILL, VULKAN_1_2)

        val secondProcess = tracker(store = store, processSessionId = "process-b")
        val restored = secondProcess.currentStatus()

        assertEquals(RendererRuntimePhase.AWAITING_CONFIRMATION, restored.phase)
        assertEquals(GraphicsBackend.VULKAN, restored.selectedBackend)
        assertNull(restored.activeBackend)
        assertNull(restored.activeVulkanApiVersion)
        assertFalse(restored.isVulkanActive)

        val confirmed = secondProcess.recordVulkanActive(COLOR_FILL, VULKAN_1_2)
        assertTrue(confirmed.isVulkanActive)
    }

    @Test
    fun listenersReceiveTheSnapshotImmediatelyAndEveryAcceptedTransition() {
        val tracker = tracker()
        val observed = mutableListOf<RendererRuntimeStatus>()
        val listener = RendererRuntimeStatusListener(observed::add)

        tracker.addListener(listener)
        tracker.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.OPENGL_ES,
            vulkanCapability = VulkanDeviceCapability.UNSUPPORTED,
            probedVulkanApiVersion = null,
            fallbackReason = "No compatible Vulkan driver"
        )
        tracker.recordOpenGlActive(COLOR_FILL, "No compatible Vulkan driver")
        tracker.removeListener(listener)
        tracker.recordReleased(COLOR_FILL)

        assertEquals(
            listOf(
                RendererRuntimePhase.IDLE,
                RendererRuntimePhase.SELECTED,
                RendererRuntimePhase.ACTIVE
            ),
            observed.map(RendererRuntimeStatus::phase)
        )
    }

    @Test
    fun aLateCallbackFromAnotherEffectCannotReplaceTheCurrentStatus() {
        val tracker = tracker()
        val glassSelection = tracker.recordSelection(
            effectId = "GLASS",
            selectedBackend = GraphicsBackend.OPENGL_ES,
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = VULKAN_1_3
        )

        val result = tracker.recordVulkanActive(COLOR_FILL, VULKAN_1_2)

        assertEquals(glassSelection, result)
        assertEquals("GLASS", tracker.currentStatus().effectId)
        assertNull(tracker.currentStatus().activeBackend)
    }

    @Test
    fun releasingAnOldEffectCannotClearTheNewRenderer() {
        val tracker = tracker()
        tracker.recordSelection(
            effectId = "GLASS",
            selectedBackend = GraphicsBackend.OPENGL_ES,
            vulkanCapability = VulkanDeviceCapability.SUPPORTED,
            probedVulkanApiVersion = VULKAN_1_3
        )
        tracker.recordOpenGlActive("GLASS")

        val result = tracker.recordReleased(COLOR_FILL)

        assertEquals(RendererRuntimePhase.ACTIVE, result.phase)
        assertEquals("GLASS", result.effectId)
        assertEquals(GraphicsBackend.OPENGL_ES, result.activeBackend)
    }

    @Test
    fun releasingOneOfTwoEnginesForTheSameEffectKeepsTheOtherActive() {
        val tracker = tracker()
        repeat(2) {
            tracker.recordSelection(
                effectId = COLOR_FILL,
                selectedBackend = GraphicsBackend.VULKAN,
                vulkanCapability = VulkanDeviceCapability.SUPPORTED,
                probedVulkanApiVersion = VULKAN_1_3
            )
        }
        tracker.recordVulkanActive(COLOR_FILL, VULKAN_1_2)

        val afterFirstRelease = tracker.recordReleased(COLOR_FILL)
        val afterFinalRelease = tracker.recordReleased(COLOR_FILL)

        assertEquals(RendererRuntimePhase.ACTIVE, afterFirstRelease.phase)
        assertTrue(afterFirstRelease.isVulkanActive)
        assertEquals(RendererRuntimePhase.RELEASED, afterFinalRelease.phase)
        assertNull(afterFinalRelease.activeBackend)
        assertFalse(afterFinalRelease.isVulkanActive)
    }

    @Test
    fun invalidVulkanSelectionAndVersionAreRejected() {
        val tracker = tracker()

        expectIllegalArgument {
            tracker.recordSelection(
                effectId = COLOR_FILL,
                selectedBackend = GraphicsBackend.VULKAN,
                vulkanCapability = VulkanDeviceCapability.UNKNOWN,
                probedVulkanApiVersion = null
            )
        }
        expectIllegalArgument {
            tracker.recordVulkanActive(COLOR_FILL, 0)
        }
    }

    @Test
    fun persistenceAndListenerFailuresDoNotCrashTheRenderer() {
        var reportedFailures = 0
        val tracker = RendererRuntimeStatusTracker(
            store = object : RendererRuntimeStatusStore {
                override fun load(): StoredRendererRuntimeStatus? = null

                override fun save(storedStatus: StoredRendererRuntimeStatus) {
                    error("disk full")
                }
            },
            processSessionId = "process-a",
            clock = { 42L },
            onError = { reportedFailures++ }
        )
        tracker.addListener { error("detached screen") }

        val status = tracker.recordSelection(
            effectId = COLOR_FILL,
            selectedBackend = GraphicsBackend.OPENGL_ES,
            vulkanCapability = VulkanDeviceCapability.UNSUPPORTED,
            probedVulkanApiVersion = null
        )

        assertEquals(RendererRuntimePhase.SELECTED, status.phase)
        assertEquals(3, reportedFailures)
    }

    private fun tracker(
        store: RendererRuntimeStatusStore = FakeStore(),
        processSessionId: String = "process-a"
    ): RendererRuntimeStatusTracker {
        var now = 100L
        return RendererRuntimeStatusTracker(
            store = store,
            processSessionId = processSessionId,
            clock = { now++ }
        )
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }

    private class FakeStore : RendererRuntimeStatusStore {
        var storedStatus: StoredRendererRuntimeStatus? = null

        override fun load(): StoredRendererRuntimeStatus? = storedStatus

        override fun save(storedStatus: StoredRendererRuntimeStatus) {
            this.storedStatus = storedStatus
        }
    }

    private companion object {
        const val COLOR_FILL = "COLORFILL"
        const val VULKAN_1_2 = 0x00402000
        const val VULKAN_1_3 = 0x00403000
    }
}
