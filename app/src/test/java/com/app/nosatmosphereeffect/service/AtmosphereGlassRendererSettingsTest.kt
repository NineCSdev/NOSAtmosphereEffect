package com.app.nosatmosphereeffect.service

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.app.nosatmosphereeffect.helper.AtmosphereGlassPolicy
import com.app.nosatmosphereeffect.helper.GlassEffectPolicy
import com.app.nosatmosphereeffect.renderer.AtmosphereRenderer
import com.app.nosatmosphereeffect.renderer.BlurToSharpRenderer
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereGlassRendererSettingsTest {
    private val context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun `saved glass option reaches both atmosphere renderer directions`() {
        val preferences = ReadOnlyPreferences(
            currentGlassPreferences() + mapOf(
                AtmosphereGlassPolicy.ENABLED_KEY to true,
                GlassEffectPolicy.LINE_COUNT_KEY to 16,
                GlassEffectPolicy.LINE_THICKNESS_KEY to 0.55f,
                GlassEffectPolicy.BACKGROUND_ONLY_KEY to true
            )
        )
        val forward = AtmosphereRenderer(context)
        val reverse = BlurToSharpRenderer(context)

        configure(AtmosphereService(), forward, preferences)
        configure(BlurToSharpService(), reverse, preferences)

        assertTrue(forward.atmosphereGlassEnabled)
        assertTrue(reverse.atmosphereGlassEnabled)
        assertEquals(16, forward.glassLineCount)
        assertEquals(16, reverse.glassLineCount)
        assertEquals(0.55f, forward.glassLineThickness, 0f)
        assertEquals(0.55f, reverse.glassLineThickness, 0f)
        assertTrue(forward.glassBackgroundOnlyEnabled)
        assertTrue(reverse.glassBackgroundOnlyEnabled)
        forward.release()
        reverse.release()
    }

    @Test
    fun `missing or malformed glass preference safely disables both renderers`() {
        listOf(
            ReadOnlyPreferences(currentGlassPreferences()),
            ReadOnlyPreferences(
                currentGlassPreferences() +
                    mapOf(AtmosphereGlassPolicy.ENABLED_KEY to "invalid")
            )
        ).forEach { preferences ->
            val forward = AtmosphereRenderer(context).apply {
                atmosphereGlassEnabled = true
            }
            val reverse = BlurToSharpRenderer(context).apply {
                atmosphereGlassEnabled = true
            }

            configure(AtmosphereService(), forward, preferences)
            configure(BlurToSharpService(), reverse, preferences)

            assertFalse(forward.atmosphereGlassEnabled)
            assertFalse(reverse.atmosphereGlassEnabled)
            assertFalse(forward.glassBackgroundOnlyEnabled)
            assertFalse(reverse.glassBackgroundOnlyEnabled)
            forward.release()
            reverse.release()
        }
    }

    @Test
    fun `both atmosphere renderers use the canonical static glass profile`() {
        val forward = AtmosphereRenderer(context)
        val reverse = BlurToSharpRenderer(context)

        assertEquals(GlassEffectPolicy.DEFAULT_LINE_COUNT, forward.glassLineCount)
        assertEquals(GlassEffectPolicy.DEFAULT_LINE_COUNT, reverse.glassLineCount)
        assertEquals(
            GlassEffectPolicy.DEFAULT_LINE_THICKNESS,
            forward.glassLineThickness,
            0f
        )
        assertEquals(
            GlassEffectPolicy.DEFAULT_LINE_THICKNESS,
            reverse.glassLineThickness,
            0f
        )

        forward.glassLineCount = Int.MAX_VALUE
        reverse.glassLineThickness = Float.NaN
        assertEquals(GlassEffectPolicy.MAX_LINE_COUNT, forward.glassLineCount)
        assertEquals(
            GlassEffectPolicy.DEFAULT_LINE_THICKNESS,
            reverse.glassLineThickness,
            0f
        )
        forward.release()
        reverse.release()
    }

    private fun configure(
        service: Any,
        renderer: Any,
        preferences: SharedPreferences
    ) {
        val method: Method = service.javaClass.declaredMethods.single { candidate ->
            candidate.name == "configureRenderer" &&
                candidate.parameterTypes.firstOrNull() == renderer.javaClass
        }
        method.isAccessible = true
        method.invoke(service, renderer, preferences)
    }

    private fun currentGlassPreferences(): Map<String, Any> = mapOf(
        GlassEffectPolicy.LINE_COUNT_KEY to GlassEffectPolicy.DEFAULT_LINE_COUNT,
        GlassEffectPolicy.LINE_THICKNESS_KEY to GlassEffectPolicy.DEFAULT_LINE_THICKNESS,
        GlassEffectPolicy.TRANSITION_STYLE_KEY to "right_to_left",
        GlassEffectPolicy.BACKGROUND_ONLY_KEY to false,
        GlassEffectPolicy.PRESET_VERSION_KEY to GlassEffectPolicy.CURRENT_PRESET_VERSION
    )

    private class ReadOnlyPreferences(
        private val values: Map<String, Any>
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String, defaultValue: String?): String? {
            return typedValue(key, defaultValue)
        }

        override fun getStringSet(
            key: String,
            defaultValues: MutableSet<String>?
        ): MutableSet<String>? {
            val value = values[key] ?: return defaultValues
            if (value !is Set<*> || value.any { it !is String }) {
                throw ClassCastException("$key is not a string set")
            }
            return value.filterIsInstance<String>().toMutableSet()
        }

        override fun getInt(key: String, defaultValue: Int): Int {
            return typedValue(key, defaultValue)
        }

        override fun getLong(key: String, defaultValue: Long): Long {
            return typedValue(key, defaultValue)
        }

        override fun getFloat(key: String, defaultValue: Float): Float {
            return typedValue(key, defaultValue)
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return typedValue(key, defaultValue)
        }

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor {
            throw UnsupportedOperationException("Read-only test preferences")
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        private inline fun <reified T> typedValue(key: String, fallback: T): T {
            val value = values[key] ?: return fallback
            if (value !is T) throw ClassCastException("$key is not ${T::class.java.simpleName}")
            return value
        }
    }
}
