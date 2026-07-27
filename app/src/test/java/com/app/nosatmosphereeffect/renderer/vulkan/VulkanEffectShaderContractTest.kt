package com.app.nosatmosphereeffect.renderer.vulkan

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanEffectShaderContractTest {
    @Test
    fun `glass and atmosphere use the device-tested Android bitmap orientation`() {
        listOf(
            "glass/glass.vert",
            "atmosphere/atmosphere.vert"
        ).forEach { relativePath ->
            val shader = File("src/main/shaders/vulkan", relativePath).readText()
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
            val shader = File("src/main/shaders/vulkan", relativePath).readText()
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
            "src/main/shaders/vulkan/atmosphere/atmosphere.frag"
        ).readText()

        assertTrue(shader.contains("smoothstep(0.0, 0.2, progress)"))
        assertTrue(shader.contains("smoothstep(0.18, 0.5, progress)"))
        assertTrue(shader.contains("smoothstep(0.15, 0.3, progress)"))
        assertTrue(shader.contains("blobPositionsAndSizes[16]"))
    }
}
