package com.app.nosatmosphereeffect.helper

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Extracts an explicit, representative color trio for the wallpaper framework. */
internal object WallpaperColorExtractor {
    private const val MAX_INPUT_SIDE = 512
    private const val MIN_OEM_SATURATION = 0.40f
    private const val MIN_CHROMATIC_SATURATION = 0.08f
    private const val MIN_OEM_LIGHTNESS = 0.30f
    private const val MAX_OEM_LIGHTNESS = 0.70f

    fun extract(file: File): WallpaperColors? {
        val bitmap = decodeSampledBitmap(file) ?: return null
        return try {
            extract(bitmap, shouldSanitizeForOem())
        } finally {
            bitmap.recycle()
        }
    }

    internal fun extract(bitmap: Bitmap, sanitizeForOem: Boolean): WallpaperColors {
        val frameworkColors = WallpaperColors.fromBitmap(bitmap)
        val palette = Palette.from(bitmap)
            .maximumColorCount(24)
            .generate()

        val frameworkPrimary = frameworkColors.primaryColor.toArgb()
        val frameworkSecondary = frameworkColors.secondaryColor?.toArgb()
        val frameworkTertiary = frameworkColors.tertiaryColor?.toArgb()

        val rawPrimary = firstColor(
            palette.vibrantSwatch?.rgb,
            palette.dominantSwatch?.rgb,
            frameworkPrimary
        )
        val rawSecondary = firstDistinctColor(
            rawPrimary,
            palette.lightVibrantSwatch?.rgb,
            palette.mutedSwatch?.rgb,
            palette.darkMutedSwatch?.rgb,
            frameworkSecondary
        ) ?: colorVariant(rawPrimary, lighten = true)
        val rawTertiary = firstDistinctColor(
            rawPrimary,
            rawSecondary,
            palette.darkVibrantSwatch?.rgb,
            palette.lightMutedSwatch?.rgb,
            palette.darkMutedSwatch?.rgb,
            frameworkTertiary
        ) ?: colorVariant(rawPrimary, lighten = false)

        val referenceHue = mostChromaticHue(rawPrimary, rawSecondary, rawTertiary)
        val primary = sanitizeIfNeeded(rawPrimary, referenceHue, sanitizeForOem)
        val secondary = sanitizeIfNeeded(rawSecondary, referenceHue, sanitizeForOem)
        val tertiary = sanitizeIfNeeded(rawTertiary, referenceHue, sanitizeForOem)

        // Preserve the framework's readability hints while supplying Palette's
        // explicit colors. Launchers still receive the correct light/dark text advice.
        return WallpaperColors(
            Color.valueOf(primary),
            Color.valueOf(secondary),
            Color.valueOf(tertiary),
            frameworkColors.colorHints
        )
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        if (!file.isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_INPUT_SIDE) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }

    @ColorInt
    private fun sanitizeIfNeeded(
        @ColorInt color: Int,
        referenceHue: Float?,
        sanitizeForOem: Boolean
    ): Int {
        if (!sanitizeForOem) return color

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        when {
            hsl[1] >= MIN_OEM_SATURATION -> Unit
            hsl[1] >= MIN_CHROMATIC_SATURATION -> hsl[1] = MIN_OEM_SATURATION
            referenceHue != null -> {
                hsl[0] = referenceHue
                hsl[1] = MIN_OEM_SATURATION
            }
            // Do not invent a hue for genuinely monochrome wallpapers.
            else -> Unit
        }
        hsl[2] = hsl[2].coerceIn(MIN_OEM_LIGHTNESS, MAX_OEM_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun shouldSanitizeForOem(): Boolean {
        val deviceIdentity = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.ROOT)
        return deviceIdentity.contains("samsung") || deviceIdentity.contains("infinix")
    }

    @ColorInt
    private fun firstColor(vararg colors: Int?): Int = colors.firstNotNullOf { it }

    @ColorInt
    private fun firstDistinctColor(@ColorInt reference: Int, vararg colors: Int?): Int? {
        return colors.firstOrNull { candidate ->
            candidate != null && colorsAreDistinct(reference, candidate)
        }
    }

    @ColorInt
    private fun firstDistinctColor(
        @ColorInt firstReference: Int,
        @ColorInt secondReference: Int,
        vararg colors: Int?
    ): Int? {
        return colors.firstOrNull { candidate ->
            candidate != null &&
                colorsAreDistinct(firstReference, candidate) &&
                colorsAreDistinct(secondReference, candidate)
        }
    }

    private fun colorsAreDistinct(@ColorInt first: Int, @ColorInt second: Int): Boolean {
        val firstHsl = FloatArray(3)
        val secondHsl = FloatArray(3)
        ColorUtils.colorToHSL(first, firstHsl)
        ColorUtils.colorToHSL(second, secondHsl)

        val directHueDistance = abs(firstHsl[0] - secondHsl[0])
        val hueDistance = min(directHueDistance, 360f - directHueDistance)
        return hueDistance >= 18f ||
            abs(firstHsl[1] - secondHsl[1]) >= 0.12f ||
            abs(firstHsl[2] - secondHsl[2]) >= 0.12f
    }

    private fun mostChromaticHue(vararg colors: Int): Float? {
        var bestHue: Float? = null
        var bestSaturation = MIN_CHROMATIC_SATURATION
        colors.forEach { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            if (hsl[1] >= bestSaturation) {
                bestHue = hsl[0]
                bestSaturation = hsl[1]
            }
        }
        return bestHue
    }

    @ColorInt
    private fun colorVariant(@ColorInt color: Int, lighten: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = if (lighten) {
            (hsl[2] + 0.18f).coerceAtMost(0.78f)
        } else {
            (hsl[2] - 0.18f).coerceAtLeast(0.22f)
        }
        return ColorUtils.HSLToColor(hsl)
    }
}
