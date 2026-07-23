package com.app.nosatmosphereeffect.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectCatalogTest {
    @Test
    fun `effect identifiers are unique`() {
        val ids = EffectCatalog.items.map(EffectItem::id)

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every reverse effect has a forward counterpart`() {
        val ids = EffectCatalog.items.map(EffectItem::id).toSet()

        EffectCatalog.items
            .filter { EffectCatalog.isReverse(it.id) }
            .forEach { reverse ->
                val forwardId = if (reverse.id == "REVERSE") {
                    "ORIGINAL"
                } else {
                    reverse.id.removeSuffix("_REVERSE")
                }
                assertTrue("$reverse has no forward counterpart", forwardId in ids)
                assertEquals(
                    EffectCatalog.family(forwardId),
                    EffectCatalog.family(reverse.id)
                )
            }
    }

    @Test
    fun `unknown and null identifiers resolve to the default effect`() {
        val default = EffectCatalog.items.first()

        assertSame(default, EffectCatalog.find(null))
        assertSame(default, EffectCatalog.find("NOT_AN_EFFECT"))
    }

    @Test
    fun `all catalog effects have positive recommended durations`() {
        EffectCatalog.items.forEach { effect ->
            assertTrue(
                "${effect.id} has an invalid duration",
                EffectCatalog.recommendedDurationMillis(effect.id) > 0L
            )
        }
        assertEquals(1000L, EffectCatalog.recommendedDurationMillis(null))
    }

    @Test
    fun `drawing and print families disable dimming`() {
        listOf(
            "HALFTONE",
            "HALFTONE_REVERSE",
            "COLORFILL",
            "COLORFILL_REVERSE",
            "NEON",
            "NEON_REVERSE"
        ).forEach { id ->
            assertEquals(0f, EffectCatalog.defaultDimness(id), 0f)
        }

        assertEquals(0.2f, EffectCatalog.defaultDimness("ORIGINAL"), 0f)
        assertEquals(0.2f, EffectCatalog.defaultDimness(null), 0f)
    }

    @Test
    fun `only declared effects start from the original wallpaper`() {
        val originalFirst = setOf(
            "ORIGINAL",
            "FROSTED",
            "HALFTONE",
            "COLORFILL_REVERSE",
            "NEON_REVERSE"
        )

        EffectCatalog.items.forEach { effect ->
            assertEquals(
                effect.id in originalFirst,
                EffectCatalog.startsFromOriginalWallpaper(effect.id)
            )
        }
        assertFalse(EffectCatalog.startsFromOriginalWallpaper(null))
    }
}
