package com.app.nosatmosphereeffect.ui.screens

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.nosatmosphereeffect.helper.TouchImageView
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import com.app.nosatmosphereeffect.ui.components.AtmoDropdownField
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton

/**
 * Bridges Compose and the proven [TouchImageView] gesture/matrix engine. The
 * activity holds one of these, hands it to [CropScreen], and calls through to
 * the underlying view once its image has been decoded.
 */
class CropController {
    var view: TouchImageView? = null

    fun setInitialImage(bitmap: Bitmap, savedMatrix: FloatArray? = null) {
        view?.setInitialImage(bitmap, savedMatrix)
    }

    fun setFitMode(fit: String, fill: String) {
        view?.setFitMode(fit, fill)
    }

    fun getCroppedBitmap(): Bitmap? = view?.getCroppedBitmap()

    fun getCurrentMatrixValues(): FloatArray? = view?.getCurrentMatrixValues()
}

private val fitOptions = listOf(
    "Screen Fill (Crop)",
    "Fit Image (Show All)",
    "Stretch",
    "Rotate to Fit (Landscape)"
)
private val fitValues = listOf(
    WallpaperFitHelper.MODE_FILL,
    WallpaperFitHelper.MODE_FIT,
    WallpaperFitHelper.MODE_STRETCH,
    WallpaperFitHelper.MODE_ROTATE_FIT
)
private val fillOptions = listOf("Black Bars", "Repeat Pattern", "Mirror Pattern")
private val fillValues = listOf(
    WallpaperFitHelper.FILL_BLACK,
    WallpaperFitHelper.FILL_REPEAT,
    WallpaperFitHelper.FILL_MIRROR
)

@Composable
fun CropScreen(
    controller: CropController,
    buttonLabel: String,
    initialFit: String,
    initialFill: String,
    onViewCreated: (TouchImageView) -> Unit,
    onFitChanged: (fit: String, fill: String) -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- The interop crop surface ------------------------------------
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TouchImageView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Seed the mode on the view up-front so the first decode
                    // frames the image with the correct fit (important when
                    // re-editing a playlist image saved as Fit/Rotate).
                    setFitMode(initialFit, initialFill)
                }.also { v ->
                    controller.view = v
                    onViewCreated(v)
                }
            }
        )

        // --- Center framing guides ---------------------------------------
        Box(
            Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.22f))
        )

        // --- Top scrim ---------------------------------------------------
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        // --- Bottom controls (with scrim for legibility over photos) -----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FitChooserSection(
                initialFit = initialFit,
                initialFill = initialFill,
                onFitChanged = onFitChanged
            )
            AtmoPrimaryButton(
                text = buttonLabel,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FitChooserSection(
    initialFit: String,
    initialFill: String,
    onFitChanged: (fit: String, fill: String) -> Unit
) {
    var fitIndex by remember {
        mutableIntStateOf(fitValues.indexOf(initialFit).takeIf { it >= 0 } ?: 0)
    }
    var fillIndex by remember {
        mutableIntStateOf(fillValues.indexOf(initialFill).takeIf { it >= 0 } ?: 0)
    }

    val currentFit = fitValues[fitIndex]
    val letterboxed = currentFit == WallpaperFitHelper.MODE_FIT ||
        currentFit == WallpaperFitHelper.MODE_ROTATE_FIT

    val hint = when (currentFit) {
        WallpaperFitHelper.MODE_FIT ->
            "The whole image is shown. Zoom or drag to adjust; empty space uses your fill choice."
        WallpaperFitHelper.MODE_STRETCH ->
            "The image is stretched to fill the screen."
        WallpaperFitHelper.MODE_ROTATE_FIT ->
            "Landscape photos are rotated to fill the screen. Zoom or drag to adjust."
        else ->
            "Pinch to zoom and drag to frame your wallpaper."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AtmoDropdownField(
            label = "Image Fit",
            options = fitOptions,
            selectedIndex = fitIndex,
            onSelected = { idx ->
                fitIndex = idx
                onFitChanged(fitValues[idx], fillValues[fillIndex])
            }
        )
        if (letterboxed) {
            AtmoDropdownField(
                label = "Empty Space Fill",
                options = fillOptions,
                selectedIndex = fillIndex,
                onSelected = { idx ->
                    fillIndex = idx
                    onFitChanged(fitValues[fitIndex], fillValues[idx])
                }
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
