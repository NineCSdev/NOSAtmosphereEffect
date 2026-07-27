package com.app.nosatmosphereeffect.renderer

data class HalftoneRenderState(
    val progress: Float = 0f,
    val dimLevel: Float = 0f,
    val dotSize: Float = 12f,
    val grayscale: Boolean = false,
    val backgroundOnly: Boolean = false,
    val hasSubject: Boolean = false
) {
    fun sanitized(): HalftoneRenderState {
        return copy(
            progress = progress.finiteOr(0f).coerceIn(0f, 1f),
            dimLevel = dimLevel.finiteOr(0f).coerceIn(0f, 1f),
            dotSize = dotSize.finiteOr(12f).coerceIn(0f, 40f),
            hasSubject = backgroundOnly && hasSubject
        )
    }

    fun effectStrength(reverse: Boolean): Float {
        val safeProgress = progress.finiteOr(0f).coerceIn(0f, 1f)
        return if (reverse) safeProgress else 1f - safeProgress
    }

    private fun Float.finiteOr(fallback: Float): Float {
        return if (isFinite()) this else fallback
    }
}
