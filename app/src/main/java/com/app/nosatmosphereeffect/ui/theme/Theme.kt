package com.app.nosatmosphereeffect.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val AtmoDarkColors = darkColorScheme(
    primary = AtmoMint,
    onPrimary = OnAtmoMint,
    primaryContainer = AtmoMintContainer,
    onPrimaryContainer = AtmoWhite,

    secondary = AtmoAmber,
    onSecondary = OnAtmoAmber,
    secondaryContainer = AtmoAmberContainer,
    onSecondaryContainer = AtmoWhite,

    tertiary = AtmoPurple,
    onTertiary = OnAtmoPurple,
    tertiaryContainer = AtmoPurpleContainer,
    onTertiaryContainer = AtmoWhite,

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

private val StandardShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

val LocalAtmoExpressive = staticCompositionLocalOf { true }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AtmoEngineTheme(
    expressive: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useExpressive = expressive ?: AppearancePreferences.isExpressiveEnabled(context)
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

    val colors = if (useExpressive) {
        dynamicDarkColorScheme(context)
    } else {
        AtmoDarkColors
    }

    CompositionLocalProvider(LocalAtmoExpressive provides useExpressive) {
        if (useExpressive) {
            MaterialExpressiveTheme(
                colorScheme = colors,
                typography = AtmoTypography,
                shapes = ExpressiveShapes,
                content = content
            )
        } else {
            MaterialTheme(
                colorScheme = colors,
                typography = AtmoTypography,
                shapes = StandardShapes,
                content = content
            )
        }
    }
}
