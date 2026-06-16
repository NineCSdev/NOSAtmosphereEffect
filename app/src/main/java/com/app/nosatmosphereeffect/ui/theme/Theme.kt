package com.app.nosatmosphereeffect.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Always-dark scheme that matches Atmo Engine's identity. We deliberately do not
 * follow the system light/dark setting: the tool is a black canvas with a single
 * purple accent regardless of the OS theme.
 */
private val AtmoDarkColors = darkColorScheme(
    primary = AtmoPurple,
    onPrimary = OnAtmoPurple,
    primaryContainer = AtmoPurpleContainer,
    onPrimaryContainer = AtmoWhite,

    secondary = AtmoPurpleDim,
    onSecondary = OnAtmoPurple,
    secondaryContainer = AtmoSurfaceContainerHigh,
    onSecondaryContainer = AtmoWhite,

    tertiary = AtmoPurple,
    onTertiary = OnAtmoPurple,

    background = AtmoBlack,
    onBackground = AtmoOnSurface,

    surface = AtmoSurface,
    onSurface = AtmoOnSurface,
    surfaceVariant = AtmoSurfaceContainerHigh,
    onSurfaceVariant = AtmoOnSurfaceVariant,

    surfaceContainerLowest = AtmoBlack,
    surfaceContainerLow = AtmoSurfaceContainerLow,
    surfaceContainer = AtmoSurfaceContainer,
    surfaceContainerHigh = AtmoSurfaceContainerHigh,
    surfaceContainerHighest = AtmoSurfaceContainerHighest,

    outline = AtmoOutlineStrong,
    outlineVariant = AtmoOutline,

    error = AtmoError,
    onError = AtmoWhite
)

private val AtmoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun AtmoEngineTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // Dark UI -> light (white) status/nav icons. The bars themselves are
            // transparent (set in the app theme) and Compose draws edge-to-edge
            // behind them, so no window bar-color is needed (and those setters are
            // deprecated from Android 15 onward).
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = AtmoDarkColors,
        typography = AtmoTypography,
        shapes = AtmoShapes,
        content = content
    )
}