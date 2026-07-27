package com.app.nosatmosphereeffect.helper

import android.view.SurfaceHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RenderHostSwapGuardTest {
    @Test
    fun successfulReplayKeepsPreparedHostOpen() {
        val host = FakeRenderHost()
        var replayed: WallpaperRenderHost? = null

        val failure = RenderHostSwapGuard.prepare(host) {
            replayed = it
        }

        assertNull(failure)
        assertSame(host, replayed)
        assertEquals(0, host.closeCount)
    }

    @Test
    fun failedReplayClosesReplacementAndReturnsCause() {
        val host = FakeRenderHost()
        val expected = IllegalStateException("surface is gone")

        val failure = RenderHostSwapGuard.prepare(host) {
            throw expected
        }

        assertSame(expected, failure)
        assertEquals(1, host.closeCount)
    }

    private class FakeRenderHost : WallpaperRenderHost {
        var closeCount = 0

        override fun onSurfaceCreated(holder: SurfaceHolder) = Unit

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) = Unit

        override fun onSurfaceDestroyed(holder: SurfaceHolder) = Unit

        override fun onResume() = Unit

        override fun onPause() = Unit

        override fun requestRender() = Unit

        override fun setWallpaperOffset(xOffset: Float) = Unit

        override fun close() {
            closeCount++
        }
    }
}
