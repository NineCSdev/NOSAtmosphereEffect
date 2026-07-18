package com.app.nosatmosphereeffect.ui.model

data class EffectItem(
    val id: String,
    val title: String,
    val transition: String,
    val description: String
)

object EffectCatalog {
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
        )
    )

    fun find(id: String?): EffectItem = items.firstOrNull { it.id == id } ?: items.first()

    fun isReverse(id: String): Boolean = id.endsWith("_REVERSE") || id == "REVERSE"

    fun family(id: String): String = when {
        id.contains("FROSTED") -> "FROSTED"
        id.contains("HALFTONE") -> "HALFTONE"
        id.contains("COLORFILL") -> "COLORFILL"
        id.contains("NEON") -> "CANVAS"
        else -> "ATMOSPHERE"
    }
}
