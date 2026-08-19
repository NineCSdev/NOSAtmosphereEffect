package com.app.nosatmosphereeffect.renderer.vulkan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VulkanApiVersionTest {
    @Test
    fun decodesEverySupportedCoreVersion() {
        val expected = listOf(
            0x00401000 to "1.1",
            0x00402000 to "1.2",
            0x00403000 to "1.3",
            0x00404000 to "1.4"
        )

        expected.forEach { (encoded, display) ->
            val version = VulkanApiVersion.fromEncoded(encoded)
            assertEquals(encoded, version?.encoded)
            assertEquals(1, version?.major)
            assertEquals(display, version.toString())
        }
    }

    @Test
    fun preservesPatchVersionForDiagnostics() {
        val version = VulkanApiVersion.fromEncoded(0x0040302a)

        assertEquals(1, version?.major)
        assertEquals(3, version?.minor)
        assertEquals(42, version?.patch)
        assertEquals("1.3.42", version.toString())
    }

    @Test
    fun rejectsUnsupportedOrInvalidVersions() {
        assertNull(VulkanApiVersion.fromEncoded(0))
        assertNull(VulkanApiVersion.fromEncoded(0x00400000))
        assertNull(VulkanApiVersion.fromEncoded(0x00405000))
        assertNull(VulkanApiVersion.fromEncoded(0x20401000))
    }
}
