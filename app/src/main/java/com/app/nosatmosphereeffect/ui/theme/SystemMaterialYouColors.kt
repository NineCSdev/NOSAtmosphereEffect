package com.app.nosatmosphereeffect.ui.theme

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Builds Material You directly from Android's wallpaper tonal palettes.
 *
 * Some Android 14+ OEM builds expose a generic blue through the newer
 * system_primary_* roles even while system_accent1_* contains the palette the
 * user selected. Reading the tonal resources keeps Compose aligned with the
 * visible system palette on those devices while retaining AOSP behavior.
 */
internal fun systemMaterialYouColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
    val primary = context.systemPalette(
        android.R.color.system_accent1_0,
        android.R.color.system_accent1_10,
        android.R.color.system_accent1_50,
        android.R.color.system_accent1_100,
        android.R.color.system_accent1_200,
        android.R.color.system_accent1_300,
        android.R.color.system_accent1_400,
        android.R.color.system_accent1_500,
        android.R.color.system_accent1_600,
        android.R.color.system_accent1_700,
        android.R.color.system_accent1_800,
        android.R.color.system_accent1_900,
        android.R.color.system_accent1_1000
    )
    val secondary = context.systemPalette(
        android.R.color.system_accent2_0,
        android.R.color.system_accent2_10,
        android.R.color.system_accent2_50,
        android.R.color.system_accent2_100,
        android.R.color.system_accent2_200,
        android.R.color.system_accent2_300,
        android.R.color.system_accent2_400,
        android.R.color.system_accent2_500,
        android.R.color.system_accent2_600,
        android.R.color.system_accent2_700,
        android.R.color.system_accent2_800,
        android.R.color.system_accent2_900,
        android.R.color.system_accent2_1000
    )
    val tertiary = context.systemPalette(
        android.R.color.system_accent3_0,
        android.R.color.system_accent3_10,
        android.R.color.system_accent3_50,
        android.R.color.system_accent3_100,
        android.R.color.system_accent3_200,
        android.R.color.system_accent3_300,
        android.R.color.system_accent3_400,
        android.R.color.system_accent3_500,
        android.R.color.system_accent3_600,
        android.R.color.system_accent3_700,
        android.R.color.system_accent3_800,
        android.R.color.system_accent3_900,
        android.R.color.system_accent3_1000
    )
    val neutral = context.systemPalette(
        android.R.color.system_neutral2_0,
        android.R.color.system_neutral2_10,
        android.R.color.system_neutral2_50,
        android.R.color.system_neutral2_100,
        android.R.color.system_neutral2_200,
        android.R.color.system_neutral2_300,
        android.R.color.system_neutral2_400,
        android.R.color.system_neutral2_500,
        android.R.color.system_neutral2_600,
        android.R.color.system_neutral2_700,
        android.R.color.system_neutral2_800,
        android.R.color.system_neutral2_900,
        android.R.color.system_neutral2_1000
    )

    return if (darkTheme) {
        darkSystemColorScheme(primary, secondary, tertiary, neutral)
    } else {
        lightSystemColorScheme(primary, secondary, tertiary, neutral)
    }
}

private fun lightSystemColorScheme(
    primary: SystemTonalPalette,
    secondary: SystemTonalPalette,
    tertiary: SystemTonalPalette,
    neutral: SystemTonalPalette
) = lightColorScheme(
    primary = primary[40],
    onPrimary = primary[100],
    primaryContainer = primary[90],
    onPrimaryContainer = primary[10],
    inversePrimary = primary[80],
    secondary = secondary[40],
    onSecondary = secondary[100],
    secondaryContainer = secondary[90],
    onSecondaryContainer = secondary[10],
    tertiary = tertiary[40],
    onTertiary = tertiary[100],
    tertiaryContainer = tertiary[90],
    onTertiaryContainer = tertiary[10],
    background = neutral[98],
    onBackground = neutral[10],
    surface = neutral[98],
    onSurface = neutral[10],
    surfaceVariant = neutral[90],
    onSurfaceVariant = neutral[30],
    inverseSurface = neutral[20],
    inverseOnSurface = neutral[95],
    outline = neutral[50],
    outlineVariant = neutral[80],
    scrim = neutral[0],
    surfaceBright = neutral[98],
    surfaceDim = neutral[87],
    surfaceContainer = neutral[94],
    surfaceContainerHigh = neutral[92],
    surfaceContainerHighest = neutral[90],
    surfaceContainerLow = neutral[96],
    surfaceContainerLowest = neutral[100],
    surfaceTint = primary[40],
    primaryFixed = primary[90],
    primaryFixedDim = primary[80],
    onPrimaryFixed = primary[10],
    onPrimaryFixedVariant = primary[30],
    secondaryFixed = secondary[90],
    secondaryFixedDim = secondary[80],
    onSecondaryFixed = secondary[10],
    onSecondaryFixedVariant = secondary[30],
    tertiaryFixed = tertiary[90],
    tertiaryFixedDim = tertiary[80],
    onTertiaryFixed = tertiary[10],
    onTertiaryFixedVariant = tertiary[30]
)

private fun darkSystemColorScheme(
    primary: SystemTonalPalette,
    secondary: SystemTonalPalette,
    tertiary: SystemTonalPalette,
    neutral: SystemTonalPalette
) = darkColorScheme(
    primary = primary[80],
    onPrimary = primary[20],
    primaryContainer = primary[30],
    onPrimaryContainer = primary[90],
    inversePrimary = primary[40],
    secondary = secondary[80],
    onSecondary = secondary[20],
    secondaryContainer = secondary[30],
    onSecondaryContainer = secondary[90],
    tertiary = tertiary[80],
    onTertiary = tertiary[20],
    tertiaryContainer = tertiary[30],
    onTertiaryContainer = tertiary[90],
    background = neutral[6],
    onBackground = neutral[90],
    surface = neutral[6],
    onSurface = neutral[90],
    surfaceVariant = neutral[30],
    onSurfaceVariant = neutral[80],
    inverseSurface = neutral[90],
    inverseOnSurface = neutral[20],
    outline = neutral[60],
    outlineVariant = neutral[30],
    scrim = neutral[0],
    surfaceBright = neutral[24],
    surfaceDim = neutral[6],
    surfaceContainer = neutral[12],
    surfaceContainerHigh = neutral[17],
    surfaceContainerHighest = neutral[22],
    surfaceContainerLow = neutral[10],
    surfaceContainerLowest = neutral[4],
    surfaceTint = primary[80],
    primaryFixed = primary[90],
    primaryFixedDim = primary[80],
    onPrimaryFixed = primary[10],
    onPrimaryFixedVariant = primary[30],
    secondaryFixed = secondary[90],
    secondaryFixedDim = secondary[80],
    onSecondaryFixed = secondary[10],
    onSecondaryFixedVariant = secondary[30],
    tertiaryFixed = tertiary[90],
    tertiaryFixedDim = tertiary[80],
    onTertiaryFixed = tertiary[10],
    onTertiaryFixedVariant = tertiary[30]
)

private data class SystemTonalPalette(
    val tone100: Color,
    val tone99: Color,
    val tone95: Color,
    val tone90: Color,
    val tone80: Color,
    val tone70: Color,
    val tone60: Color,
    val tone50: Color,
    val tone40: Color,
    val tone30: Color,
    val tone20: Color,
    val tone10: Color,
    val tone0: Color
) {
    operator fun get(tone: Int): Color = when (tone) {
        100 -> tone100
        99 -> tone99
        95 -> tone95
        90 -> tone90
        80 -> tone80
        70 -> tone70
        60 -> tone60
        50 -> tone50
        40 -> tone40
        30 -> tone30
        20 -> tone20
        10 -> tone10
        0 -> tone0
        else -> tone40.withLuminance(tone.toDouble())
    }
}

private fun Context.systemPalette(
    @ColorRes tone100: Int,
    @ColorRes tone99: Int,
    @ColorRes tone95: Int,
    @ColorRes tone90: Int,
    @ColorRes tone80: Int,
    @ColorRes tone70: Int,
    @ColorRes tone60: Int,
    @ColorRes tone50: Int,
    @ColorRes tone40: Int,
    @ColorRes tone30: Int,
    @ColorRes tone20: Int,
    @ColorRes tone10: Int,
    @ColorRes tone0: Int
) = SystemTonalPalette(
    Color(getColor(tone100)),
    Color(getColor(tone99)),
    Color(getColor(tone95)),
    Color(getColor(tone90)),
    Color(getColor(tone80)),
    Color(getColor(tone70)),
    Color(getColor(tone60)),
    Color(getColor(tone50)),
    Color(getColor(tone40)),
    Color(getColor(tone30)),
    Color(getColor(tone20)),
    Color(getColor(tone10)),
    Color(getColor(tone0))
)

private fun Color.withLuminance(luminance: Double): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return Color(ColorUtils.LABToColor(luminance, lab[1], lab[2]))
}
