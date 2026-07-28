package com.app.nosatmosphereeffect.renderer.vulkan

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanAtmosphereNativeContractTest {
    @Test
    fun `state written before surface creation is cached instead of failing`() {
        val adapter = atmosphereAdapter()
        val cacheState = adapter.indexOf("atmosphere->latestParams = params;")
        val deferState = adapter.indexOf("if (!atmosphere->surfaceReady)")
        val reportAccepted = adapter.indexOf("return JNI_TRUE;", deferState)
        val uploadState = adapter.indexOf(
            "atmo::vulkan::setUniformData(",
            cacheState
        )

        assertTrue(cacheState >= 0)
        assertTrue(deferState > cacheState)
        assertTrue(reportAccepted > deferState)
        assertTrue(uploadState > reportAccepted)
    }

    @Test
    fun `cached state is replayed only after the surface creates its uniform buffer`() {
        val adapter = atmosphereAdapter()
        val createSurface = adapter.indexOf(
            "const bool surfaceCreated = atmo::vulkan::setSurface("
        )
        val markReady = adapter.indexOf(
            "atmosphere->surfaceReady = true;",
            createSurface
        )
        val replayState = adapter.indexOf(
            "atmosphere->hasLatestParams &&",
            markReady
        )
        val uploadState = adapter.indexOf(
            "atmo::vulkan::setUniformData(",
            replayState
        )

        assertTrue(createSurface >= 0)
        assertTrue(markReady > createSurface)
        assertTrue(replayState > markReady)
        assertTrue(uploadState > replayState)
    }

    @Test
    fun `surface destruction prevents writes to a released uniform buffer`() {
        val adapter = atmosphereAdapter()
        val destroyFunction = adapter.indexOf(
            "VulkanAtmosphereNative_nativeDestroySurface"
        )
        val markNotReady = adapter.indexOf(
            "atmosphere->surfaceReady = false;",
            destroyFunction
        )
        val destroySurface = adapter.indexOf(
            "atmo::vulkan::destroySurface(atmosphere->engine);",
            markNotReady
        )

        assertTrue(destroyFunction >= 0)
        assertTrue(markNotReady > destroyFunction)
        assertTrue(destroySurface > markNotReady)
    }

    private fun atmosphereAdapter(): String {
        return File("src/main/cpp/vulkan_atmosphere_jni.cpp").readText()
    }
}
