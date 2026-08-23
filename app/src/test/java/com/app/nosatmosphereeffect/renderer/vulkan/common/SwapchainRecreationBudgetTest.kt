package com.app.nosatmosphereeffect.renderer.vulkan.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapchainRecreationBudgetTest {

    @Test
    fun retriesAreRejectedAfterTheConfiguredLimit() {
        val budget = SwapchainRecreationBudget(maxAttempts = 3)

        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
    }

    @Test
    fun aSuccessfulFrameResetsTheRetryBudget() {
        val budget = SwapchainRecreationBudget(maxAttempts = 1)

        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())

        budget.reset()

        assertTrue(budget.tryAcquire())
    }
}
