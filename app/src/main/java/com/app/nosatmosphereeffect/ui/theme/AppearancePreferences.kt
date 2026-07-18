package com.app.nosatmosphereeffect.ui.theme

import android.content.Context
import androidx.core.content.edit

object AppearancePreferences {
    private const val PREFS_NAME = "appearance_prefs"
    private const val EXPRESSIVE_KEY = "material_expressive_enabled"

    fun isExpressiveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(EXPRESSIVE_KEY, true)

    fun setExpressiveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(EXPRESSIVE_KEY, enabled)
        }
    }
}
