package com.app.nosatmosphereeffect.renderer.backend

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

object GraphicsBackendPreferences {
    const val PREFS_NAME = "graphics_backend_prefs"
    const val PREFERENCE_KEY = "renderer_backend_preference"
    private const val TAG = "GraphicsBackendPrefs"

    fun read(context: Context): GraphicsBackendPreference {
        val preferences = try {
            context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Unable to open renderer preferences", failure)
            return GraphicsBackendPreference.AUTOMATIC
        }
        return GraphicsBackendPreference.fromStoredValue(
            preferences.readStringSafely(
                PREFERENCE_KEY,
                GraphicsBackendPreference.AUTOMATIC.storedValue
            )
        )
    }

    fun write(context: Context, preference: GraphicsBackendPreference) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(PREFERENCE_KEY, preference.storedValue)
                }
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Unable to save the renderer preference", failure)
        }
    }

    fun reset(context: Context) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    remove(PREFERENCE_KEY)
                }
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Unable to reset the renderer preference", failure)
        }
    }

    private fun SharedPreferences.readStringSafely(
        key: String,
        fallback: String
    ): String {
        return try {
            getString(key, fallback) ?: fallback
        } catch (failure: ClassCastException) {
            Log.w(TAG, "Renderer preference has the wrong type", failure)
            fallback
        }
    }
}
