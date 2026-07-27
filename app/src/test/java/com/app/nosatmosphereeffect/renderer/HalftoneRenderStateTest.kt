package com.app.nosatmosphereeffect.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HalftoneRenderStateTest {
    @Test
    fun sanitizedStateBoundsEveryNumericShaderInput() {
        val sanitized = HalftoneRenderState(
            progress = Float.POSITIVE_INFINITY,
            dimLevel = -3f,
            dotSize = 400f,
            backgroundOnly = false,
            hasSubject = true
        ).sanitized()

        assertEquals(0f, sanitized.progress, 0f)
        assertEquals(0f, sanitized.dimLevel, 0f)
        assertEquals(40f, sanitized.dotSize, 0f)
        assertFalse(sanitized.hasSubject)
    }

    @Test
    fun forwardAndReverseStrengthsMatchExistingShaderDirections() {
        val start = HalftoneRenderState(progress = 0f)
        val end = HalftoneRenderState(progress = 1f)

        assertEquals(1f, start.effectStrength(reverse = false), 0f)
        assertEquals(0f, end.effectStrength(reverse = false), 0f)
        assertEquals(0f, start.effectStrength(reverse = true), 0f)
        assertEquals(1f, end.effectStrength(reverse = true), 0f)
    }

    @Test
    fun subjectReadinessIsPreservedOnlyInBackgroundMode() {
        val protected = HalftoneRenderState(
            backgroundOnly = true,
            hasSubject = true
        ).sanitized()
        val wholeWallpaper = protected.copy(
            backgroundOnly = false
        ).sanitized()

        assertEquals(true, protected.hasSubject)
        assertEquals(false, wholeWallpaper.hasSubject)
    }
}
