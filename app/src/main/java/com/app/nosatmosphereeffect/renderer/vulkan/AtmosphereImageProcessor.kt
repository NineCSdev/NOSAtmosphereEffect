package com.app.nosatmosphereeffect.renderer.vulkan

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

internal object AtmosphereImageProcessor {
    const val BLUR_RADIUS = 200

    fun createBlurredBitmap(
        source: Bitmap,
        radius: Int = BLUR_RADIUS
    ): Bitmap {
        require(!source.isRecycled) { "The Atmosphere source bitmap was recycled" }
        require(source.width > 0 && source.height > 0) {
            "The Atmosphere source bitmap is empty"
        }

        val width = source.width
        val height = source.height
        val sourcePixels = IntArray(width * height)
        val horizontal = IntArray(sourcePixels.size)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        triangularBlurPixels(
            source = sourcePixels,
            destination = horizontal,
            width = width,
            height = height,
            radius = radius,
            horizontal = true
        )
        triangularBlurPixels(
            source = horizontal,
            destination = sourcePixels,
            width = width,
            height = height,
            radius = radius,
            horizontal = false
        )

        return createBitmap(width, height).apply {
            setPixels(sourcePixels, 0, width, 0, 0, width, height)
        }
    }

    internal fun triangularBlurPixels(
        source: IntArray,
        destination: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        require(width > 0 && height > 0)
        require(source.size == width * height)
        require(destination.size == source.size)

        val safeRadius = radius.coerceAtLeast(1)
        val lineLength = if (horizontal) width else height
        val prefix = LongArray(lineLength + 1)
        val weightedPrefix = LongArray(lineLength + 1)
        destination.fill(OPAQUE_ALPHA)
        if (horizontal) {
            repeat(height) { row ->
                repeatColorChannel { shift ->
                    blurLine(
                        length = width,
                        radius = safeRadius,
                        read = { column ->
                            source[row * width + column].channel(shift)
                        },
                        prefix = prefix,
                        weightedPrefix = weightedPrefix,
                        write = { column, value ->
                            val index = row * width + column
                            destination[index] =
                                destination[index] or (value shl shift)
                        }
                    )
                }
            }
        } else {
            repeat(width) { column ->
                repeatColorChannel { shift ->
                    blurLine(
                        length = height,
                        radius = safeRadius,
                        read = { row ->
                            source[row * width + column].channel(shift)
                        },
                        prefix = prefix,
                        weightedPrefix = weightedPrefix,
                        write = { row, value ->
                            val index = row * width + column
                            destination[index] =
                                destination[index] or (value shl shift)
                        }
                    )
                }
            }
        }
    }

    private inline fun blurLine(
        length: Int,
        radius: Int,
        read: (Int) -> Int,
        prefix: LongArray,
        weightedPrefix: LongArray,
        write: (Int, Int) -> Unit
    ) {
        prefix[0] = 0L
        weightedPrefix[0] = 0L
        for (index in 0 until length) {
            val value = read(index).toLong()
            prefix[index + 1] = prefix[index] + value
            weightedPrefix[index + 1] =
                weightedPrefix[index] + value * index
        }

        val normalization = radius.toLong() * radius
        for (center in 0 until length) {
            val leftStart = center - radius + 1
            val leftInBoundsStart = leftStart.coerceAtLeast(0)
            val leftSum = range(prefix, leftInBoundsStart, center)
            val leftWeighted = range(
                weightedPrefix,
                leftInBoundsStart,
                center
            )
            var weightedSum =
                leftWeighted +
                (radius - center).toLong() * leftSum

            if (leftStart < 0) {
                val clampedSamples = -leftStart
                weightedSum +=
                    read(0).toLong() *
                    triangularNumber(clampedSamples)
            }

            val rightEnd = center + radius - 1
            val rightInBoundsEnd = rightEnd.coerceAtMost(length - 1)
            if (center + 1 <= rightInBoundsEnd) {
                val rightSum = range(
                    prefix,
                    center + 1,
                    rightInBoundsEnd
                )
                val rightWeighted = range(
                    weightedPrefix,
                    center + 1,
                    rightInBoundsEnd
                )
                weightedSum +=
                    (center + radius).toLong() * rightSum -
                    rightWeighted
            }

            if (rightEnd >= length) {
                val clampedSamples = rightEnd - length + 1
                weightedSum +=
                    read(length - 1).toLong() *
                    triangularNumber(clampedSamples)
            }

            val rounded = ((weightedSum + normalization / 2L) / normalization)
                .toInt()
                .coerceIn(0, 255)
            write(center, rounded)
        }
    }

    private fun range(prefix: LongArray, start: Int, endInclusive: Int): Long {
        return if (start > endInclusive) {
            0L
        } else {
            prefix[endInclusive + 1] - prefix[start]
        }
    }

    private fun triangularNumber(value: Int): Long {
        return value.toLong() * (value + 1L) / 2L
    }

    private inline fun repeatColorChannel(block: (shift: Int) -> Unit) {
        block(RED_SHIFT)
        block(GREEN_SHIFT)
        block(BLUE_SHIFT)
    }

    private fun Int.channel(shift: Int): Int = ushr(shift) and 0xFF

    private const val OPAQUE_ALPHA = -0x1000000
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
}
