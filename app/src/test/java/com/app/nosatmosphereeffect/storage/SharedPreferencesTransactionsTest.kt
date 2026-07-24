package com.app.nosatmosphereeffect.storage

import android.content.SharedPreferences
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesTransactionsTest {
    @Test
    fun `snapshot restores every supported value and removes later values`() {
        val preferences = FakeSharedPreferences(
            mutableMapOf(
                "boolean" to true,
                "float" to 1.5f,
                "int" to 7,
                "long" to 9L,
                "string" to "old",
                "set" to setOf("one", "two")
            )
        )
        val snapshot = SharedPreferencesTransactions.snapshot(listOf(preferences)).single()

        preferences.edit()
            .clear()
            .putString("string", "new")
            .putBoolean("added", true)
            .commit()
        snapshot.restore()

        assertEquals(true, preferences.all["boolean"])
        assertEquals(1.5f, preferences.all["float"])
        assertEquals(7, preferences.all["int"])
        assertEquals(9L, preferences.all["long"])
        assertEquals("old", preferences.all["string"])
        assertEquals(setOf("one", "two"), preferences.all["set"])
        assertEquals(6, preferences.all.size)
    }

    @Test
    fun `failed restore is attached to original apply failure`() {
        val preferences = FakeSharedPreferences(mutableMapOf("key" to "old"))
        val snapshots = SharedPreferencesTransactions.snapshot(listOf(preferences))
        val applyFailure = IOException("Apply failed")
        preferences.failCommits = true

        SharedPreferencesTransactions.restoreAll(snapshots, applyFailure)

        assertEquals(1, applyFailure.suppressed.size)
        assertTrue(applyFailure.suppressed.single() is IOException)
    }

    private class FakeSharedPreferences(
        private val values: MutableMap<String, Any?>
    ) : SharedPreferences {
        var failCommits = false

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String, defaultValue: String?): String? {
            return values[key] as? String ?: defaultValue
        }

        override fun getStringSet(
            key: String,
            defaultValues: MutableSet<String>?
        ): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (values[key] as? Set<String>)?.toMutableSet() ?: defaultValues
        }

        override fun getInt(key: String, defaultValue: Int): Int {
            return values[key] as? Int ?: defaultValue
        }

        override fun getLong(key: String, defaultValue: Long): Long {
            return values[key] as? Long ?: defaultValue
        }

        override fun getFloat(key: String, defaultValue: Float): Float {
            return values[key] as? Float ?: defaultValue
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return values[key] as? Boolean ?: defaultValue
        }

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value
                removed -= key
                return this
            }

            override fun putStringSet(
                key: String,
                values: MutableSet<String>?
            ): SharedPreferences.Editor {
                pending[key] = values?.toSet()
                removed -= key
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pending[key] = value
                removed -= key
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pending[key] = value
                removed -= key
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pending[key] = value
                removed -= key
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pending[key] = value
                removed -= key
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                pending -= key
                removed += key
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                pending.clear()
                removed.clear()
                return this
            }

            override fun commit(): Boolean {
                if (failCommits) return false
                if (clearRequested) values.clear()
                removed.forEach(values::remove)
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
