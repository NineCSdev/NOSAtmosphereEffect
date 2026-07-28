package com.app.nosatmosphereeffect.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AtmoIconMotionMathTest {

    @Test
    fun releasedIconsRemainAtRest() {
        AtmoIconMotion.entries.forEach { motion ->
            assertEquals(
                AtmoIconTransform(),
                AtmoIconMotionMath.pressTransform(
                    motion = motion,
                    pressed = false,
                    motionEnabled = true
                )
            )
        }
    }

    @Test
    fun disabledMotionRemainsAtRest() {
        AtmoIconMotion.entries.forEach { motion ->
            assertEquals(
                AtmoIconTransform(),
                AtmoIconMotionMath.pressTransform(
                    motion = motion,
                    pressed = true,
                    motionEnabled = false
                )
            )
        }
    }

    @Test
    fun semanticPressMotionsUseExpectedDirection() {
        val spin = AtmoIconMotionMath.pressTransform(
            AtmoIconMotion.SPIN,
            pressed = true,
            motionEnabled = true
        )
        val tilt = AtmoIconMotionMath.pressTransform(
            AtmoIconMotion.TILT,
            pressed = true,
            motionEnabled = true
        )
        val back = AtmoIconMotionMath.pressTransform(
            AtmoIconMotion.BACK,
            pressed = true,
            motionEnabled = true
        )

        assertEquals(22.5f, spin.rotationDegrees, 0f)
        assertEquals(-10f, tilt.rotationDegrees, 0f)
        assertEquals(-3.5f, back.translationXDp, 0f)
    }

    @Test
    fun onlyEnabledSpinMotionGetsClickRotation() {
        AtmoIconMotion.entries.forEach { motion ->
            val expected = if (motion == AtmoIconMotion.SPIN) 360f else 0f
            assertEquals(
                expected,
                AtmoIconMotionMath.clickRotationDegrees(motion, motionEnabled = true),
                0f
            )
        }
        assertEquals(
            0f,
            AtmoIconMotionMath.clickRotationDegrees(
                AtmoIconMotion.SPIN,
                motionEnabled = false
            ),
            0f
        )
    }
}
