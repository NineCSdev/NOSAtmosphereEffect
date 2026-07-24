package com.app.nosatmosphereeffect.activity

import com.app.nosatmosphereeffect.service.GlassReverseService
import com.app.nosatmosphereeffect.service.GlassService
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperEffectServicesTest {

    @Test
    fun `service registry matches the catalog and its ordering`() {
        assertEquals(
            EffectCatalog.items.map { it.id },
            WallpaperEffectServices.supportedEffectIds
        )
    }

    @Test
    fun `glass effect maps to its wallpaper service in both directions`() {
        assertEquals("GLASS", WallpaperEffectServices.normalize("GLASS"))
        assertEquals(GlassService::class.java, WallpaperEffectServices.serviceFor("GLASS"))
        assertEquals(
            "GLASS",
            WallpaperEffectServices.effectIdForService(GlassService::class.java.name)
        )
        assertEquals("GLASS_REVERSE", WallpaperEffectServices.normalize("GLASS_REVERSE"))
        assertEquals(
            GlassReverseService::class.java,
            WallpaperEffectServices.serviceFor("GLASS_REVERSE")
        )
        assertEquals(
            "GLASS_REVERSE",
            WallpaperEffectServices.effectIdForService(GlassReverseService::class.java.name)
        )
    }
}
