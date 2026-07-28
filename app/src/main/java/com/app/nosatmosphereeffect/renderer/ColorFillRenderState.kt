package com.app.nosatmosphereeffect.renderer

data class ColorFillRenderState(
    val progress: Float = 0f,
    val dimLevel: Float = 0f,
    val originX: Float = 0.5f,
    val originY: Float = 0.8f,
    val scrollOffsetX: Float = 0.5f,
    val scrollWindowX: Float = 1f
) {
    fun sanitized(): ColorFillRenderState {
        return copy(
            progress = progress.finiteOr(0f).coerceIn(0f, 1f),
            dimLevel = dimLevel.finiteOr(0f).coerceIn(0f, 1f),
            originX = originX.finiteOr(0.5f).coerceIn(0f, 1f),
            originY = originY.finiteOr(0.8f).coerceIn(0f, 1f),
            scrollOffsetX = scrollOffsetX.finiteOr(0.5f).coerceIn(0f, 1f),
            scrollWindowX = scrollWindowX.finiteOr(1f).coerceIn(0.001f, 1f)
        )
    }

    private fun Float.finiteOr(fallback: Float): Float {
        return if (isFinite()) this else fallback
    }
}
