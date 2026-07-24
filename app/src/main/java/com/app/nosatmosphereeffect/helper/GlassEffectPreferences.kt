package com.app.nosatmosphereeffect.helper

import android.content.SharedPreferences

object GlassEffectPreferences {
    fun readAndMigrate(preferences: SharedPreferences): GlassEffectSettings {
        val storedCount = preferences.readInt(
            GlassEffectPolicy.LINE_COUNT_KEY,
            GlassEffectPolicy.DEFAULT_LINE_COUNT
        )
        val storedThickness = preferences.readFloat(
            GlassEffectPolicy.LINE_THICKNESS_KEY,
            GlassEffectPolicy.DEFAULT_LINE_THICKNESS
        )
        val presetVersion = preferences.readInt(
            GlassEffectPolicy.PRESET_VERSION_KEY,
            0
        )
        val storedTransition = preferences.readString(
            GlassEffectPolicy.TRANSITION_STYLE_KEY,
            GlassTransitionStyle.RIGHT_TO_LEFT.storedValue
        )
        val transitionStyle = GlassTransitionStyle.fromStoredValue(storedTransition)
        val backgroundOnly = preferences.readBoolean(
            GlassEffectPolicy.BACKGROUND_ONLY_KEY,
            false
        )
        val settings = GlassEffectPolicy.resolveStoredSettings(
            lineCount = storedCount,
            lineThickness = storedThickness,
            presetVersion = presetVersion,
            transitionStyle = transitionStyle,
            backgroundOnly = backgroundOnly
        )

        if (presetVersion != GlassEffectPolicy.CURRENT_PRESET_VERSION ||
            settings.lineCount != storedCount ||
            settings.lineThickness != storedThickness ||
            settings.transitionStyle.storedValue != storedTransition
        ) {
            preferences.edit()
                .putInt(GlassEffectPolicy.LINE_COUNT_KEY, settings.lineCount)
                .putFloat(GlassEffectPolicy.LINE_THICKNESS_KEY, settings.lineThickness)
                .putString(
                    GlassEffectPolicy.TRANSITION_STYLE_KEY,
                    settings.transitionStyle.storedValue
                )
                .putBoolean(
                    GlassEffectPolicy.BACKGROUND_ONLY_KEY,
                    settings.backgroundOnly
                )
                .putInt(
                    GlassEffectPolicy.PRESET_VERSION_KEY,
                    GlassEffectPolicy.CURRENT_PRESET_VERSION
                )
                .apply()
        }

        return settings
    }

    private fun SharedPreferences.readInt(key: String, fallback: Int): Int {
        return try {
            getInt(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }
    }

    private fun SharedPreferences.readFloat(key: String, fallback: Float): Float {
        return try {
            getFloat(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }
    }

    private fun SharedPreferences.readString(key: String, fallback: String): String {
        return try {
            getString(key, fallback) ?: fallback
        } catch (_: ClassCastException) {
            fallback
        }
    }

    private fun SharedPreferences.readBoolean(key: String, fallback: Boolean): Boolean {
        return try {
            getBoolean(key, fallback)
        } catch (_: ClassCastException) {
            fallback
        }
    }
}
