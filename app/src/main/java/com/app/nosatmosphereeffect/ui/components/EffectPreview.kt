package com.app.nosatmosphereeffect.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun WallpaperTransitionPreview(
    effectId: String,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    showDeviceChrome: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "wallpaperPreview")
    val automaticProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3200
                0f at 0
                1f at 1450 using FastOutSlowInEasing
                1f at 2100
                0f at 3200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "wallpaperPreviewProgress"
    )

    val unlockProgress = (progress ?: automaticProgress).coerceIn(0f, 1f)
    val phase = if (EffectCatalog.isReverse(effectId)) 1f - unlockProgress else unlockProgress
    val family = EffectCatalog.family(effectId)
    val sketch = rememberSketchPreview(if (family == "CANVAS") wallpaper else null)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription = "${EffectCatalog.find(effectId).title} transition preview"
            }
    ) {
        when (family) {
            "ATMOSPHERE" -> AtmosphericPreview(wallpaper, phase, false)
            "FROSTED" -> AtmosphericPreview(wallpaper, phase, true)
            "HALFTONE" -> HalftonePreview(wallpaper, phase)
            "COLORFILL" -> ColorFillPreview(wallpaper, phase)
            else -> CanvasSketchPreview(wallpaper, sketch, phase)
        }

        if (showDeviceChrome) {
            PreviewChrome(unlockProgress)
        }
    }
}

@Composable
private fun AtmosphericPreview(
    wallpaper: ImageBitmap?,
    phase: Float,
    frosted: Boolean
) {
    val density = LocalDensity.current
    val blurRadius = with(density) {
        ((if (frosted) 26.dp else 17.dp) * phase.coerceAtLeast(0.01f)).toPx()
    }
    Box(Modifier.fillMaxSize()) {
        ArtworkLayer(
            wallpaper = wallpaper,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                }
        )
        if (!frosted) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .alpha(phase * 0.78f)
            ) {
                val cloud = MaterialPreviewColors.cloud
                drawOval(
                    color = cloud.copy(alpha = 0.42f),
                    topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.18f, size.height * 0.38f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.86f, size.height * 0.42f)
                )
                drawOval(
                    color = MaterialPreviewColors.warmCloud.copy(alpha = 0.34f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.82f, size.height * 0.36f)
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = if (frosted) phase * 0.12f else 0f))
        )
    }
}

@Composable
private fun HalftonePreview(wallpaper: ImageBitmap?, phase: Float) {
    Box(Modifier.fillMaxSize()) {
        ArtworkLayer(wallpaper = wallpaper, modifier = Modifier.fillMaxSize())
        ArtworkLayer(
            wallpaper = wallpaper,
            grayscale = true,
            modifier = Modifier
                .fillMaxSize()
                .alpha(phase * 0.72f)
        )
        Canvas(Modifier.fillMaxSize().alpha(phase)) {
            val spacing = 11.dp.toPx()
            val radius = 0.8.dp.toPx() + phase * 2.2.dp.toPx()
            var y = spacing * 0.5f
            var row = 0
            while (y < size.height) {
                var x = spacing * 0.5f + if (row % 2 == 0) 0f else spacing * 0.5f
                while (x < size.width) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.70f),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                    x += spacing
                }
                y += spacing
                row++
            }
        }
    }
}

@Composable
private fun ColorFillPreview(wallpaper: ImageBitmap?, phase: Float) {
    Box(Modifier.fillMaxSize()) {
        ArtworkLayer(wallpaper = wallpaper, grayscale = true, modifier = Modifier.fillMaxSize())
        ArtworkLayer(
            wallpaper = wallpaper,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val center = androidx.compose.ui.geometry.Offset(
                        size.width * 0.52f,
                        size.height * 0.78f
                    )
                    val radius = hypot(size.width, size.height) * phase
                    val path = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center = center,
                                radius = radius
                            )
                        )
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
        )
    }
}

@Composable
private fun CanvasSketchPreview(
    wallpaper: ImageBitmap?,
    sketch: ImageBitmap?,
    phase: Float
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (sketch != null) {
            Image(
                bitmap = sketch,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(1f - phase)
            )
        } else {
            FallbackSketch(Modifier.fillMaxSize().alpha(1f - phase))
        }
        ArtworkLayer(
            wallpaper = wallpaper,
            modifier = Modifier.fillMaxSize().alpha(phase)
        )
    }
}

@Composable
private fun ArtworkLayer(
    wallpaper: ImageBitmap?,
    modifier: Modifier,
    grayscale: Boolean = false
) {
    if (wallpaper != null) {
        val grayscaleFilter = remember {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (grayscale) grayscaleFilter else null,
            modifier = modifier
        )
    } else {
        Canvas(modifier) { drawFallbackArtwork(grayscale) }
    }
}

@Composable
private fun PreviewChrome(progress: Float) {
    val lockAlpha = (1f - progress * 1.7f).coerceIn(0f, 1f)
    val homeAlpha = ((progress - 0.35f) * 1.55f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().padding(12.dp)) {
        androidx.compose.material3.Text(
            text = "10:09",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(lockAlpha)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(homeAlpha),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(if (index == 2) RoundedCornerShape(7.dp) else CircleShape)
                        .background(
                            when (index) {
                                0 -> MaterialPreviewColors.cloud
                                1 -> MaterialPreviewColors.sun
                                2 -> MaterialPreviewColors.warmCloud
                                else -> MaterialPreviewColors.leaf
                            }.copy(alpha = 0.92f)
                        )
                )
            }
        }

        AnimatedContent(
            targetState = progress >= 0.5f,
            modifier = Modifier.align(Alignment.TopEnd),
            label = "previewModeIcon"
        ) { home ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (home) Icons.Rounded.Home else Icons.Rounded.Lock,
                    contentDescription = if (home) "Home screen" else "Lock screen",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun FallbackSketch(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.Black)) {
        val ink = Color(0xFFE4E4E4)
        val thin = 1.2.dp.toPx()
        val strong = 1.8.dp.toPx()
        drawLine(ink, offset(0.05f, 0.72f), offset(0.95f, 0.72f), strong)
        drawLine(ink, offset(0.10f, 0.72f), offset(0.34f, 0.44f), strong)
        drawLine(ink, offset(0.34f, 0.44f), offset(0.54f, 0.70f), strong)
        drawLine(ink, offset(0.42f, 0.72f), offset(0.68f, 0.30f), strong)
        drawLine(ink, offset(0.68f, 0.30f), offset(0.92f, 0.72f), strong)
        drawRect(
            color = ink,
            topLeft = offset(0.12f, 0.54f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.17f, size.height * 0.18f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(thin)
        )
        repeat(4) { index ->
            val x = 0.16f + index * 0.05f
            drawLine(ink, offset(x, 0.58f), offset(x, 0.68f), thin)
        }
        drawCircle(
            color = ink,
            radius = size.minDimension * 0.075f,
            center = offset(0.73f, 0.18f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strong)
        )
    }
}

private fun DrawScope.drawFallbackArtwork(grayscale: Boolean) {
    fun color(value: Color): Color {
        if (!grayscale) return value
        val luminance = value.red * 0.299f + value.green * 0.587f + value.blue * 0.114f
        return Color(luminance, luminance, luminance, value.alpha)
    }

    drawRect(color(MaterialPreviewColors.sky))
    drawCircle(
        color = color(MaterialPreviewColors.sun),
        radius = size.minDimension * 0.09f,
        center = offset(0.73f, 0.18f)
    )

    val rearMountain = Path().apply {
        moveTo(0f, size.height * 0.72f)
        lineTo(size.width * 0.34f, size.height * 0.44f)
        lineTo(size.width * 0.56f, size.height * 0.72f)
        close()
    }
    drawPath(rearMountain, color(MaterialPreviewColors.rearMountain))

    val frontMountain = Path().apply {
        moveTo(size.width * 0.38f, size.height * 0.74f)
        lineTo(size.width * 0.68f, size.height * 0.30f)
        lineTo(size.width, size.height * 0.74f)
        close()
    }
    drawPath(frontMountain, color(MaterialPreviewColors.frontMountain))

    drawRect(
        color = color(MaterialPreviewColors.building),
        topLeft = offset(0.10f, 0.53f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height * 0.22f)
    )
    repeat(3) { row ->
        repeat(3) { column ->
            drawRect(
                color = color(MaterialPreviewColors.window),
                topLeft = offset(0.135f + column * 0.058f, 0.57f + row * 0.052f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.025f, size.height * 0.022f)
            )
        }
    }
    drawRect(
        color = color(MaterialPreviewColors.ground),
        topLeft = offset(0f, 0.72f),
        size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.28f)
    )
}

private fun DrawScope.offset(x: Float, y: Float) =
    androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)

private object MaterialPreviewColors {
    val sky = Color(0xFF215765)
    val sun = Color(0xFFF6C86F)
    val rearMountain = Color(0xFF7E6BB0)
    val frontMountain = Color(0xFF21443E)
    val building = Color(0xFFD66C62)
    val window = Color(0xFFF8DDA5)
    val ground = Color(0xFF132825)
    val cloud = Color(0xFF8FE0D0)
    val warmCloud = Color(0xFFE5A2B7)
    val leaf = Color(0xFF8FCB75)
}

@Composable
private fun rememberSketchPreview(source: ImageBitmap?): ImageBitmap? {
    val result by produceState<ImageBitmap?>(initialValue = null, key1 = source) {
        value = if (source == null) {
            null
        } else {
            withContext(Dispatchers.Default) { createSketchPreview(source) }
        }
    }
    return result
}

private fun createSketchPreview(source: ImageBitmap): ImageBitmap {
    val input = source.asAndroidBitmap()
    val scale = min(1f, 1600f / max(input.width, input.height).toFloat())
    val width = max(2, (input.width * scale).roundToInt())
    val height = max(2, (input.height * scale).roundToInt())
    val scaled = input.scale(width, height, true)
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)

    val gray = FloatArray(pixels.size)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        gray[index] = AndroidColor.red(pixel) * 0.299f +
            AndroidColor.green(pixel) * 0.587f +
            AndroidColor.blue(pixel) * 0.114f
    }

    val fine = boxBlur(gray, width, height, 3)
    val broad = boxBlur(gray, width, height, 7)
    val fineMagnitude = sobelMagnitude(fine, width, height)
    val broadMagnitude = FloatArray(gray.size)
    val broadGx = FloatArray(gray.size)
    val broadGy = FloatArray(gray.size)

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            val top = (y - 1) * width
            val center = y * width
            val bottom = (y + 1) * width
            val gx = -broad[top + x - 1] + broad[top + x + 1] -
                2f * broad[center + x - 1] + 2f * broad[center + x + 1] -
                broad[bottom + x - 1] + broad[bottom + x + 1]
            val gy = -broad[top + x - 1] - 2f * broad[top + x] - broad[top + x + 1] +
                broad[bottom + x - 1] + 2f * broad[bottom + x] + broad[bottom + x + 1]
            broadGx[index] = gx
            broadGy[index] = gy
            broadMagnitude[index] = sqrt(gx * gx + gy * gy)
        }
    }

    val edgeState = ByteArray(pixels.size)
    for (y in 2 until height - 2) {
        for (x in 2 until width - 2) {
            val index = y * width + x
            val coarse = broadMagnitude[index]
            val gx = broadGx[index]
            val gy = broadGy[index]
            val horizontal = kotlin.math.abs(gx)
            val vertical = kotlin.math.abs(gy)
            val firstNeighbor: Int
            val secondNeighbor: Int
            when {
                horizontal > vertical * 1.8f -> {
                    firstNeighbor = index - 1
                    secondNeighbor = index + 1
                }
                vertical > horizontal * 1.8f -> {
                    firstNeighbor = index - width
                    secondNeighbor = index + width
                }
                gx * gy >= 0f -> {
                    firstNeighbor = index - width - 1
                    secondNeighbor = index + width + 1
                }
                else -> {
                    firstNeighbor = index - width + 1
                    secondNeighbor = index + width - 1
                }
            }
            if (
                coarse < broadMagnitude[firstNeighbor] * 0.97f ||
                coarse < broadMagnitude[secondNeighbor] * 0.97f
            ) {
                continue
            }

            val magnitude = min(fineMagnitude[index], coarse * 1.9f)
            edgeState[index] = when {
                magnitude >= 52f -> 2
                magnitude >= 30f -> 1
                else -> 0
            }
        }
    }

    // Match the production Canvas shader's short hysteresis pass: retain soft
    // contour sections only when they connect to a confident line. This fills
    // small contrast gaps without bringing back isolated hair, brick, or noise.
    repeat(2) {
        val next = edgeState.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (edgeState[index].toInt() != 1) continue
                var connected = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (edgeState[index + dy * width + dx].toInt() == 2) {
                            connected = true
                        }
                    }
                }
                if (connected) next[index] = 2
            }
        }
        next.copyInto(edgeState)
    }

    val output = IntArray(pixels.size) { AndroidColor.BLACK }
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            var coverage = if (edgeState[index].toInt() == 2) 1f else 0f
            if (coverage == 0f) {
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (edgeState[index + dy * width + dx].toInt() == 2) {
                            coverage = max(coverage, if (dx == 0 || dy == 0) 0.24f else 0.14f)
                        }
                    }
                }
            }
            if (coverage > 0f) {
                val ink = 194f + gray[index] / 255f * 51f
                val shade = (ink * coverage).roundToInt().coerceIn(0, 255)
                output[index] = AndroidColor.rgb(shade, shade, shade)
            }
        }
    }

    val bitmap = Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    if (scaled !== input) scaled.recycle()
    return bitmap.asImageBitmap()
}

private fun boxBlur(
    input: FloatArray,
    width: Int,
    height: Int,
    radius: Int
): FloatArray {
    val horizontal = FloatArray(input.size)
    val output = FloatArray(input.size)

    for (y in 0 until height) {
        var sum = 0f
        for (x in -radius..radius) {
            sum += input[y * width + x.coerceIn(0, width - 1)]
        }
        for (x in 0 until width) {
            horizontal[y * width + x] = sum / (radius * 2 + 1)
            sum -= input[y * width + (x - radius).coerceIn(0, width - 1)]
            sum += input[y * width + (x + radius + 1).coerceIn(0, width - 1)]
        }
    }

    for (x in 0 until width) {
        var sum = 0f
        for (y in -radius..radius) {
            sum += horizontal[y.coerceIn(0, height - 1) * width + x]
        }
        for (y in 0 until height) {
            output[y * width + x] = sum / (radius * 2 + 1)
            sum -= horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
            sum += horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
        }
    }
    return output
}

private fun sobelMagnitude(input: FloatArray, width: Int, height: Int): FloatArray {
    val magnitude = FloatArray(input.size)
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            val top = (y - 1) * width
            val center = y * width
            val bottom = (y + 1) * width
            val gx = -input[top + x - 1] + input[top + x + 1] -
                2f * input[center + x - 1] + 2f * input[center + x + 1] -
                input[bottom + x - 1] + input[bottom + x + 1]
            val gy = -input[top + x - 1] - 2f * input[top + x] - input[top + x + 1] +
                input[bottom + x - 1] + 2f * input[bottom + x] + input[bottom + x + 1]
            magnitude[index] = sqrt(gx * gx + gy * gy)
        }
    }
    return magnitude
}
