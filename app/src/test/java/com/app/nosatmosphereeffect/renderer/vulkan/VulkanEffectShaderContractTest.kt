package com.app.nosatmosphereeffect.renderer.vulkan

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanEffectShaderContractTest {
    @Test
    fun `glass and atmosphere use the device-tested Android bitmap orientation`() {
        listOf(
            "glass/glass.vert",
            "atmosphere/atmosphere.vert"
        ).forEach { relativePath ->
            val shader = File("src/main/shaders", relativePath).readText()
            assertTrue(relativePath, shader.contains("vec2(0.0, 0.0)"))
            assertTrue(relativePath, shader.contains("vec2(2.0, 0.0)"))
            assertTrue(relativePath, shader.contains("vec2(0.0, 2.0)"))
        }
    }

    @Test
    fun `Vulkan shaders retain the measured glass rib constants`() {
        listOf(
            "glass/glass.frag",
            "atmosphere/atmosphere.frag"
        ).forEach { relativePath ->
            val shader = File("src/main/shaders", relativePath).readText()
            assertTrue(relativePath, shader.contains("1.08"))
            assertTrue(relativePath, shader.contains("1.80"))
            assertTrue(relativePath, shader.contains("0.25"))
            assertTrue(relativePath, shader.contains("0.036"))
            assertTrue(relativePath, shader.contains("0.016"))
        }
    }

    @Test
    fun `atmosphere shader retains OpenGL phase thresholds and blob capacity`() {
        val shader = File(
            "src/main/shaders/atmosphere/atmosphere.frag"
        ).readText()

        assertTrue(shader.contains("smoothstep(0.0, 0.2, progress)"))
        assertTrue(shader.contains("smoothstep(0.18, 0.5, progress)"))
        assertTrue(shader.contains("smoothstep(0.15, 0.3, progress)"))
        assertTrue(shader.contains("blobPositionsAndSizes[16]"))
    }

    @Test
    fun `halftone Vulkan progress matches the GLES forward and reverse directions`() {
        val vulkanShader = File(
            "src/main/shaders/halftone/halftone.frag"
        ).readText()
        val forwardGlesShader = File(
            "src/main/assets/shaders/halftone/halftone_to_sharp.frag"
        ).readText()
        val reverseGlesShader = File(
            "src/main/assets/shaders/halftone/sharp_to_halftone.frag"
        ).readText()

        assertTrue(
            forwardGlesShader.contains(
                "float t = clamp(uBlurStrength, 0.0, 1.0)"
            )
        )
        assertTrue(
            forwardGlesShader.contains(
                "mix(sharp, halftoneOutput, t)"
            )
        )
        assertTrue(
            reverseGlesShader.contains(
                "effectStrength = 1.0 - clamp(uBlurStrength, 0.0, 1.0)"
            )
        )
        assertTrue(
            vulkanShader.contains(
                "effectStrength = reverse ? 1.0 - progress : progress"
            )
        )
        assertFalse(
            vulkanShader.contains(
                "effectStrength = reverse ? progress : 1.0 - progress"
            )
        )
    }
}
