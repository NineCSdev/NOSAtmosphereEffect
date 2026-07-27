package com.app.nosatmosphereeffect.renderer.vulkan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU equivalent of Canvas Sketch's edge, hysteresis and short-distance passes.
 *
 * Vulkan still performs the animated final composition and presentation. This
 * processor runs only when the wallpaper, segmentation mask, or detail setting
 * changes, on the Vulkan host's worker thread.
 */
internal object CanvasContourProcessor {
    const val MAX_SKETCH_SIDE = 1_600
    const val LINE_MAX_DISTANCE = 6

    private const val EDGE_SAMPLE_RADIUS = 2
    private const val HYSTERESIS_PASSES = 2
    private const val WEAK_RATIO = 0.58f
    private const val MAGNITUDE_NORMALIZATION = 0.83715789f

    data class Size(val width: Int, val height: Int)

    data class PixelImage(
        val width: Int,
        val height: Int,
        val argb: IntArray
    ) {
        init {
            require(width > 0 && height > 0)
            require(argb.size == width * height)
        }
    }

    data class SubjectMask(
        val width: Int,
        val height: Int,
        val values: ByteArray
    ) {
        init {
            require(width > 0 && height > 0)
            require(values.size == width * height)
        }
    }

    data class Result(
        val width: Int,
        val height: Int,
        val normalizedDistance: ByteArray,
        val resolvedEdgeCount: Int
    ) {
        init {
            require(width > 0 && height > 0)
            require(normalizedDistance.size == width * height)
            require(resolvedEdgeCount in 0..normalizedDistance.size)
        }
    }

    fun workingSize(sourceWidth: Int, sourceHeight: Int): Size {
        require(sourceWidth > 0 && sourceHeight > 0)
        val longest = max(sourceWidth, sourceHeight)
        if (longest <= MAX_SKETCH_SIDE) {
            return Size(sourceWidth, sourceHeight)
        }
        val scale = MAX_SKETCH_SIDE.toFloat() / longest.toFloat()
        return Size(
            width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
            height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        )
    }

    fun thresholdFor(sensitivity: Float): Float {
        val detail = sensitivity.finiteOr(0.5f).coerceIn(0f, 1f)
        return 0.30f + (0.11f - 0.30f) * detail
    }

    fun lodFor(sensitivity: Float): Float {
        val detail = sensitivity.finiteOr(0.5f).coerceIn(0f, 1f)
        return 3.2f - 1.2f * detail
    }

    fun process(
        source: PixelImage,
        sensitivity: Float,
        subjectMask: SubjectMask? = null
    ): Result {
        val safeSensitivity = sensitivity.finiteOr(0.5f).coerceIn(0f, 1f)
        val lod = lodFor(safeSensitivity)
        val fineRadius = blurRadiusForLod(lod)
        val broadRadius = blurRadiusForLod(lod + 0.85f)
        val finePixels = boxBlur(source.argb, source.width, source.height, fineRadius)
        val broadPixels = boxBlur(source.argb, source.width, source.height, broadRadius)
        val count = source.width * source.height

        val fineMagnitude = FloatArray(count)
        fillGradientField(
            pixels = finePixels,
            width = source.width,
            height = source.height,
            magnitude = fineMagnitude
        )

        val broadMagnitude = FloatArray(count)
        val broadDirectionX = FloatArray(count)
        val broadDirectionY = FloatArray(count)
        fillGradientField(
            pixels = broadPixels,
            width = source.width,
            height = source.height,
            magnitude = broadMagnitude,
            directionX = broadDirectionX,
            directionY = broadDirectionY
        )

        val candidates = classifyCandidates(
            width = source.width,
            height = source.height,
            fineMagnitude = fineMagnitude,
            broadMagnitude = broadMagnitude,
            broadDirectionX = broadDirectionX,
            broadDirectionY = broadDirectionY,
            threshold = thresholdFor(safeSensitivity),
            subjectMask = subjectMask
        )
        val resolved = resolveHysteresis(candidates, source.width, source.height)
        var edgeCount = 0
        for (edge in resolved) {
            if (edge) edgeCount++
        }
        return Result(
            width = source.width,
            height = source.height,
            normalizedDistance = shortDistanceMap(
                resolved,
                source.width,
                source.height
            ),
            resolvedEdgeCount = edgeCount
        )
    }

    private fun blurRadiusForLod(lod: Float): Int {
        return 2f.pow(lod - 1f).roundToInt().coerceIn(1, 8)
    }

    private fun boxBlur(
        source: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ): IntArray {
        if (radius <= 0) return source.copyOf()
        val horizontal = IntArray(source.size)
        val destination = IntArray(source.size)
        val diameter = radius * 2 + 1

        for (y in 0 until height) {
            val row = y * width
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val pixel = source[row + offset.coerceIn(0, width - 1)]
                red += pixel.red()
                green += pixel.green()
                blue += pixel.blue()
            }
            for (x in 0 until width) {
                horizontal[row + x] = opaqueArgb(
                    red / diameter,
                    green / diameter,
                    blue / diameter
                )
                val removed = source[row + (x - radius).coerceIn(0, width - 1)]
                val added = source[row + (x + radius + 1).coerceIn(0, width - 1)]
                red += added.red() - removed.red()
                green += added.green() - removed.green()
                blue += added.blue() - removed.blue()
            }
        }

        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val pixel = horizontal[offset.coerceIn(0, height - 1) * width + x]
                red += pixel.red()
                green += pixel.green()
                blue += pixel.blue()
            }
            for (y in 0 until height) {
                destination[y * width + x] = opaqueArgb(
                    red / diameter,
                    green / diameter,
                    blue / diameter
                )
                val removed =
                    horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val added =
                    horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                red += added.red() - removed.red()
                green += added.green() - removed.green()
                blue += added.blue() - removed.blue()
            }
        }
        return destination
    }

    private fun fillGradientField(
        pixels: IntArray,
        width: Int,
        height: Int,
        magnitude: FloatArray,
        directionX: FloatArray? = null,
        directionY: FloatArray? = null
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val tl = pixels[indexAt(x - EDGE_SAMPLE_RADIUS, y - EDGE_SAMPLE_RADIUS, width, height)]
                val tm = pixels[indexAt(x, y - EDGE_SAMPLE_RADIUS, width, height)]
                val tr = pixels[indexAt(x + EDGE_SAMPLE_RADIUS, y - EDGE_SAMPLE_RADIUS, width, height)]
                val ml = pixels[indexAt(x - EDGE_SAMPLE_RADIUS, y, width, height)]
                val mr = pixels[indexAt(x + EDGE_SAMPLE_RADIUS, y, width, height)]
                val bl = pixels[indexAt(x - EDGE_SAMPLE_RADIUS, y + EDGE_SAMPLE_RADIUS, width, height)]
                val bm = pixels[indexAt(x, y + EDGE_SAMPLE_RADIUS, width, height)]
                val br = pixels[indexAt(x + EDGE_SAMPLE_RADIUS, y + EDGE_SAMPLE_RADIUS, width, height)]

                val gxRed = sobelX(tl.red(), ml.red(), bl.red(), tr.red(), mr.red(), br.red())
                val gxGreen = sobelX(tl.green(), ml.green(), bl.green(), tr.green(), mr.green(), br.green())
                val gxBlue = sobelX(tl.blue(), ml.blue(), bl.blue(), tr.blue(), mr.blue(), br.blue())
                val gyRed = sobelY(tl.red(), tm.red(), tr.red(), bl.red(), bm.red(), br.red())
                val gyGreen = sobelY(tl.green(), tm.green(), tr.green(), bl.green(), bm.green(), br.green())
                val gyBlue = sobelY(tl.blue(), tm.blue(), tr.blue(), bl.blue(), bm.blue(), br.blue())

                val jxx = gxRed * gxRed + gxGreen * gxGreen + gxBlue * gxBlue
                val jyy = gyRed * gyRed + gyGreen * gyGreen + gyBlue * gyBlue
                val jxy = gxRed * gyRed + gxGreen * gyGreen + gxBlue * gyBlue
                val delta = jxx - jyy
                val discriminant = sqrt(max(delta * delta + 4f * jxy * jxy, 0f))
                val lambda = 0.5f * (jxx + jyy + discriminant)
                val index = y * width + x
                magnitude[index] = sqrt(max(lambda, 0f)) * MAGNITUDE_NORMALIZATION

                if (directionX != null && directionY != null) {
                    val theta = 0.5f * atan2(2f * jxy, delta)
                    directionX[index] = cos(theta)
                    directionY[index] = sin(theta)
                }
            }
        }
    }

    private fun classifyCandidates(
        width: Int,
        height: Int,
        fineMagnitude: FloatArray,
        broadMagnitude: FloatArray,
        broadDirectionX: FloatArray,
        broadDirectionY: FloatArray,
        threshold: Float,
        subjectMask: SubjectMask?
    ): ByteArray {
        val candidates = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val directionX = broadDirectionX[index]
                val directionY = broadDirectionY[index]
                val before = sampleFloat(
                    broadMagnitude,
                    width,
                    height,
                    x - directionX * EDGE_SAMPLE_RADIUS,
                    y - directionY * EDGE_SAMPLE_RADIUS
                )
                val after = sampleFloat(
                    broadMagnitude,
                    width,
                    height,
                    x + directionX * EDGE_SAMPLE_RADIUS,
                    y + directionY * EDGE_SAMPLE_RADIUS
                )
                val broad = broadMagnitude[index]
                val crest = if (broad >= before && broad > after) 1f else 0f

                val maskValues = subjectMask?.let {
                    maskContribution(it, x, y, width, height)
                }
                val interior = maskValues?.interior ?: 1f
                val silhouette = maskValues?.silhouette ?: 0f
                val contour = min(fineMagnitude[index], broad * 1.4f) * crest * interior
                candidates[index] = when {
                    contour >= threshold || silhouette >= 0.35f -> STRONG
                    contour >= threshold * WEAK_RATIO -> WEAK
                    else -> NONE
                }
            }
        }
        return candidates
    }

    private data class MaskContribution(
        val interior: Float,
        val silhouette: Float
    )

    private fun maskContribution(
        mask: SubjectMask,
        x: Int,
        y: Int,
        outputWidth: Int,
        outputHeight: Int
    ): MaskContribution {
        val maskX = if (outputWidth <= 1) {
            0f
        } else {
            x.toFloat() * (mask.width - 1).toFloat() / (outputWidth - 1).toFloat()
        }
        val maskY = if (outputHeight <= 1) {
            0f
        } else {
            y.toFloat() * (mask.height - 1).toFloat() / (outputHeight - 1).toFloat()
        }
        val center = sampleMask(mask, maskX, maskY)
        var low = center
        var high = center
        for (offsetY in -1..1) {
            for (offsetX in -1..1) {
                val value = sampleMask(
                    mask,
                    maskX + offsetX,
                    maskY + offsetY
                )
                low = min(low, value)
                high = max(high, value)
            }
        }
        val crossesBoundary = if (low <= 0.5f && high >= 0.5f) 1f else 0f
        val centered = 1f - smoothstep(0.06f, 0.26f, abs(center - 0.5f))
        return MaskContribution(
            interior = smoothstep(0.60f, 0.82f, center),
            silhouette = crossesBoundary * centered
        )
    }

    private fun resolveHysteresis(
        candidates: ByteArray,
        width: Int,
        height: Int
    ): BooleanArray {
        var source = candidates
        repeat(HYSTERESIS_PASSES) {
            val destination = ByteArray(source.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val value = source[index]
                    if (value != WEAK) {
                        destination[index] = value
                        continue
                    }
                    var touchesStrong = false
                    for (offsetY in -1..1) {
                        for (offsetX in -1..1) {
                            val neighbor = source[indexAt(
                                x + offsetX,
                                y + offsetY,
                                width,
                                height
                            )]
                            if (neighbor == STRONG) {
                                touchesStrong = true
                            }
                        }
                    }
                    destination[index] = if (touchesStrong) STRONG else WEAK
                }
            }
            source = destination
        }
        return BooleanArray(source.size) { source[it] == STRONG }
    }

    private fun shortDistanceMap(
        edges: BooleanArray,
        width: Int,
        height: Int
    ): ByteArray {
        val horizontal = FloatArray(edges.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var best = LINE_MAX_DISTANCE.toFloat()
                for (offset in -LINE_MAX_DISTANCE..LINE_MAX_DISTANCE) {
                    val sampleX = (x + offset).coerceIn(0, width - 1)
                    if (edges[y * width + sampleX]) {
                        best = min(best, abs(offset).toFloat())
                    }
                }
                horizontal[y * width + x] = best
            }
        }

        val output = ByteArray(edges.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var best = LINE_MAX_DISTANCE.toFloat()
                for (offset in -LINE_MAX_DISTANCE..LINE_MAX_DISTANCE) {
                    val sampleY = (y + offset).coerceIn(0, height - 1)
                    val rowDistance = horizontal[sampleY * width + x]
                    best = min(
                        best,
                        sqrt(rowDistance * rowDistance + offset * offset.toFloat())
                    )
                }
                output[y * width + x] = (
                    best.coerceIn(0f, LINE_MAX_DISTANCE.toFloat()) /
                        LINE_MAX_DISTANCE.toFloat() *
                        255f
                    ).roundToInt().toByte()
            }
        }
        return output
    }

    private fun sampleFloat(
        values: FloatArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Float {
        val safeX = x.coerceIn(0f, (width - 1).toFloat())
        val safeY = y.coerceIn(0f, (height - 1).toFloat())
        val x0 = floor(safeX).toInt()
        val y0 = floor(safeY).toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = safeX - x0
        val fy = safeY - y0
        val top = lerp(values[y0 * width + x0], values[y0 * width + x1], fx)
        val bottom = lerp(values[y1 * width + x0], values[y1 * width + x1], fx)
        return lerp(top, bottom, fy)
    }

    private fun sampleMask(mask: SubjectMask, x: Float, y: Float): Float {
        val safeX = x.coerceIn(0f, (mask.width - 1).toFloat())
        val safeY = y.coerceIn(0f, (mask.height - 1).toFloat())
        val x0 = floor(safeX).toInt()
        val y0 = floor(safeY).toInt()
        val x1 = min(x0 + 1, mask.width - 1)
        val y1 = min(y0 + 1, mask.height - 1)
        val fx = safeX - x0
        val fy = safeY - y0
        val top = lerp(
            mask.values[y0 * mask.width + x0].unsignedUnit(),
            mask.values[y0 * mask.width + x1].unsignedUnit(),
            fx
        )
        val bottom = lerp(
            mask.values[y1 * mask.width + x0].unsignedUnit(),
            mask.values[y1 * mask.width + x1].unsignedUnit(),
            fx
        )
        return lerp(top, bottom, fy)
    }

    private fun indexAt(
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Int {
        return y.coerceIn(0, height - 1) * width +
            x.coerceIn(0, width - 1)
    }

    private fun sobelX(
        topLeft: Int,
        middleLeft: Int,
        bottomLeft: Int,
        topRight: Int,
        middleRight: Int,
        bottomRight: Int
    ): Float {
        return (
            topRight + 2 * middleRight + bottomRight -
                topLeft - 2 * middleLeft - bottomLeft
            ) * (0.25f / 255f)
    }

    private fun sobelY(
        topLeft: Int,
        topMiddle: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomMiddle: Int,
        bottomRight: Int
    ): Float {
        return (
            bottomLeft + 2 * bottomMiddle + bottomRight -
                topLeft - 2 * topMiddle - topRight
            ) * (0.25f / 255f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value < edge0) 0f else 1f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float {
        return start + (end - start) * amount
    }

    private fun Int.red(): Int = (this ushr 16) and 0xFF

    private fun Int.green(): Int = (this ushr 8) and 0xFF

    private fun Int.blue(): Int = this and 0xFF

    private fun opaqueArgb(red: Int, green: Int, blue: Int): Int {
        return 0xFF000000.toInt() or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }

    private fun Byte.unsignedUnit(): Float = (toInt() and 0xFF) / 255f

    private fun Float.finiteOr(fallback: Float): Float {
        return if (isFinite()) this else fallback
    }

    private val NONE: Byte = 0
    private val WEAK: Byte = 127
    private val STRONG: Byte = 255.toByte()
}
