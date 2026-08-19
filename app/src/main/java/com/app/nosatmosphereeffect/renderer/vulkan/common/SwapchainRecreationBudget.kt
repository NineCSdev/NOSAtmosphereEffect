package com.app.nosatmosphereeffect.renderer.vulkan.common

internal class SwapchainRecreationBudget(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
) {
    private var attempts = 0

    init {
        require(maxAttempts > 0)
    }

    fun tryAcquire(): Boolean {
        if (attempts >= maxAttempts) return false
        attempts++
        return true
    }

    fun reset() {
        attempts = 0
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
    }
}
