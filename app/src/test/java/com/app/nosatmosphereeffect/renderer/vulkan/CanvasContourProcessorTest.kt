package com.app.nosatmosphereeffect.renderer.vulkan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasContourProcessorTest {
    @Test
    fun workSizePreservesAspectAndCapsTheLongestSide() {
        assertEquals(
            CanvasContourProcessor.Size(800, 600),
            CanvasContourProcessor.workingSize(800, 600)
        )
        assertEquals(
            CanvasContourProcessor.Size(900, 1_600),
            CanvasContourProcessor.workingSize(1_080, 1_920)
        )
    }

    @Test
    fun detailSettingLowersThresholdAndLod() {
        assertTrue(
            CanvasContourProcessor.thresholdFor(1f) <
                CanvasContourProcessor.thresholdFor(0f)
        )
        assertTrue(
            CanvasContourProcessor.lodFor(1f) <
                CanvasContourProcessor.lodFor(0f)
        )
    }

    @Test
    fun flatImageProducesNoEdgesAndMaximumDistance() {
        val image = solidImage(48, 48, 0xFF808080.toInt())

        val result = CanvasContourProcessor.process(image, sensitivity = 0.5f)

        assertEquals(0, result.resolvedEdgeCount)
        assertTrue(result.normalizedDistance.all { (it.toInt() and 0xFF) == 255 })
    }

    @Test
    fun highContrastBoundaryProducesStableContour() {
        val width = 64
        val height = 48
        val pixels = IntArray(width * height) { index ->
            if (index % width < width / 2) {
                0xFF080808.toInt()
            } else {
                0xFFF4F4F4.toInt()
            }
        }

        val result = CanvasContourProcessor.process(
            CanvasContourProcessor.PixelImage(width, height, pixels),
            sensitivity = 0.5f
        )

        assertTrue(result.resolvedEdgeCount >= height / 2)
        val centerDistance = result.normalizedDistance[
            (height / 2) * width + width / 2
        ].toInt() and 0xFF
        assertTrue(centerDistance < 128)
    }

    @Test
    fun subjectMaskKeepsSilhouetteAndSuppressesOutsideTexture() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if ((x / 4 + y / 4) % 2 == 0) {
                0xFFF4F4F4.toInt()
            } else {
                0xFF101010.toInt()
            }
        }
        val maskValues = ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                x in 19..44 && y in 15..48 -> 255.toByte()
                x in 18..45 && y in 14..49 -> 128.toByte()
                else -> 0
            }
        }

        val result = CanvasContourProcessor.process(
            source = CanvasContourProcessor.PixelImage(width, height, pixels),
            sensitivity = 0.8f,
            subjectMask = CanvasContourProcessor.SubjectMask(
                width,
                height,
                maskValues
            )
        )

        assertTrue(result.resolvedEdgeCount > 0)
        assertEquals(255, result.normalizedDistance[0].toInt() and 0xFF)
        val silhouetteDistance =
            result.normalizedDistance[32 * width + 18].toInt() and 0xFF
        assertTrue(silhouetteDistance < 128)
    }

    private fun solidImage(
        width: Int,
        height: Int,
        color: Int
    ): CanvasContourProcessor.PixelImage {
        return CanvasContourProcessor.PixelImage(
            width,
            height,
            IntArray(width * height) { color }
        )
    }
}
