package com.app.nosatmosphereeffect.ui.theme

import android.content.Context
import androidx.core.content.edit

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object AppearancePreferences {
    private const val PREFS_NAME = "appearance_prefs"
    private const val EXPRESSIVE_KEY = "material_expressive_enabled"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val PITCH_BLACK_KEY = "pitch_black_background"

    fun isExpressiveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(EXPRESSIVE_KEY, true)

    fun setExpressiveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(EXPRESSIVE_KEY, enabled)
        }
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(THEME_MODE_KEY, AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(saved.orEmpty()) }
            .getOrDefault(AppThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(THEME_MODE_KEY, mode.name)
        }
    }

    fun isPitchBlackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PITCH_BLACK_KEY, false)

    fun setPitchBlackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(PITCH_BLACK_KEY, enabled)
        }
    }
}
