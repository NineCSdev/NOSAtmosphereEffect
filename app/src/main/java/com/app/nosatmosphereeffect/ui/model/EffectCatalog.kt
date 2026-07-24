package com.app.nosatmosphereeffect.ui.model

import com.app.nosatmosphereeffect.helper.AtmosphereGlassPolicy

data class EffectItem(
    val id: String,
    val title: String,
    val transition: String,
    val description: String
)

object EffectCatalog {
    private val originalFirstEffectIds = setOf(
        "ORIGINAL",
        "GLASS",
        "FROSTED",
        "HALFTONE",
        "COLORFILL_REVERSE",
        "NEON_REVERSE"
    )

    val items = listOf(
        EffectItem(
            "ORIGINAL",
            "Original Atmosphere",
            "Sharp to blur",
            "Ambient color and drifting atmospheric clouds."
        ),
        EffectItem(
            "REVERSE",
            "Reverse Atmosphere",
            "Blur to sharp",
            "Atmospheric clouds clear to reveal the wallpaper."
        ),
        EffectItem(
            "GLASS",
            "Glass Effect",
            "Right-to-left or fade in",
            "Continuous reeded glass with a configurable transition."
        ),
        EffectItem(
            "GLASS_REVERSE",
            "Glass Effect Reverse",
            "Left-to-right or fade out",
            "Reeded glass clears with a configurable transition."
        ),
        EffectItem(
            "COLORFILL",
            "Color Fill",
            "Monochrome to color",
            "Color spreads outward from the fingerprint position."
        ),
        EffectItem(
            "COLORFILL_REVERSE",
            "Color Fill Reverse",
            "Color to monochrome",
            "Color drains toward the fingerprint position."
        ),
        EffectItem(
            "NEON",
            "Canvas Sketch",
            "Sketch to image",
            "A clean line drawing resolves into the wallpaper."
        ),
        EffectItem(
            "NEON_REVERSE",
            "Canvas Sketch Reverse",
            "Image to sketch",
            "The wallpaper settles into a clean line drawing."
        ),
        EffectItem(
            "FROSTED",
            "Simple Frosted",
            "Sharp to blur",
            "A clean, uniform frosted-glass transition."
        ),
        EffectItem(
            "FROSTED_REVERSE",
            "Simple Frosted Reverse",
            "Blur to sharp",
            "Heavy frost dissolves into a clear image."
        ),
        EffectItem(
            "HALFTONE",
            "Halftone Print",
            "Sharp to halftone",
            "The wallpaper resolves into a printed dot pattern."
        ),
        EffectItem(
            "HALFTONE_REVERSE",
            "Halftone Print Reverse",
            "Halftone to sharp",
            "Printed dots expand into continuous color."
        )
    )

    fun find(id: String?): EffectItem = items.firstOrNull { it.id == id } ?: items.first()

    fun recommendedDurationMillis(id: String?): Long = when (id) {
        "ORIGINAL" -> 2500L
        "REVERSE" -> 1500L
        "GLASS", "GLASS_REVERSE" -> 1200L
        "FROSTED", "FROSTED_REVERSE" -> 500L
        "HALFTONE", "HALFTONE_REVERSE" -> 500L
        "COLORFILL", "COLORFILL_REVERSE" -> 1500L
        "NEON", "NEON_REVERSE" -> 1000L
        else -> 1000L
    }

    fun defaultDimness(id: String?): Float = when {
        id?.contains("HALFTONE") == true -> 0f
        id?.contains("COLORFILL") == true -> 0f
        id?.contains("NEON") == true -> 0f
        id?.contains("GLASS") == true -> 0f
        else -> 0.2f
    }

    fun isReverse(id: String): Boolean = id.endsWith("_REVERSE") || id == "REVERSE"

    fun startsFromOriginalWallpaper(id: String?): Boolean = id in originalFirstEffectIds

    fun supportsAtmosphereGlass(id: String?): Boolean =
        AtmosphereGlassPolicy.supportsEffect(id)

    fun family(id: String): String = when {
        id.contains("FROSTED") -> "FROSTED"
        id.contains("HALFTONE") -> "HALFTONE"
        id.contains("COLORFILL") -> "COLORFILL"
        id.contains("NEON") -> "CANVAS"
        id.contains("GLASS") -> "GLASS"
        else -> "ATMOSPHERE"
    }
}
