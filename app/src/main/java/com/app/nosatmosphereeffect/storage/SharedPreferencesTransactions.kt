package com.app.nosatmosphereeffect.storage

import android.content.SharedPreferences
import java.io.IOException

internal object SharedPreferencesTransactions {
    fun snapshot(preferences: List<SharedPreferences>): List<Snapshot> {
        return preferences.map(::Snapshot)
    }

    fun restoreAll(snapshots: List<Snapshot>, failure: Throwable) {
        snapshots.asReversed().forEach { snapshot ->
            try {
                snapshot.restore()
            } catch (restoreFailure: Exception) {
                failure.addSuppressed(restoreFailure)
            }
        }
    }

    internal class Snapshot internal constructor(
        private val preferences: SharedPreferences
    ) {
        private val values = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }

        @Throws(IOException::class)
        fun restore() {
            val editor = preferences.edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> {
                        val strings = value.filterIsInstance<String>().toSet()
                        if (strings.size != value.size) {
                            throw IOException(
                                "Preference $key contains an unsupported string-set value"
                            )
                        }
                        editor.putStringSet(key, strings)
                    }
                    null -> editor.remove(key)
                    else -> throw IOException(
                        "Preference $key has unsupported type ${value.javaClass.name}"
                    )
                }
            }
            if (!editor.commit()) {
                throw IOException("Could not restore preferences after apply failure")
            }
        }
    }
}
