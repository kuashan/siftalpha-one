package com.siftalpha.studio.runtime

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectRuntimeSelectionStoreTest {

    @Test
    fun missingSelectionDefaultsToTermux() {
        val store = ProjectRuntimeSelectionStore(MemorySharedPreferences())

        assertEquals(
            ProjectRuntimeSelection.TERMUX,
            store.read("legacy-project-document"),
        )
    }

    @Test
    fun selectedRuntimePersistsAcrossStoreInstances() {
        val prefs = MemorySharedPreferences()
        ProjectRuntimeSelectionStore(prefs).write(
            "project-a-document",
            ProjectRuntimeSelection.EMBEDDED_R,
        )

        assertEquals(
            ProjectRuntimeSelection.EMBEDDED_R,
            ProjectRuntimeSelectionStore(prefs).read("project-a-document"),
        )
    }

    @Test
    fun projectSelectionsDoNotContaminateEachOther() {
        val prefs = MemorySharedPreferences()
        val store = ProjectRuntimeSelectionStore(prefs)
        store.write("project-a-document", ProjectRuntimeSelection.EMBEDDED_R)
        store.write("project-b-document", ProjectRuntimeSelection.TERMUX)

        assertEquals(ProjectRuntimeSelection.EMBEDDED_R, store.read("project-a-document"))
        assertEquals(ProjectRuntimeSelection.TERMUX, store.read("project-b-document"))
    }

    @Test
    fun malformedStoredValueSafelyDefaultsToTermux() {
        val prefs = MemorySharedPreferences()
        val store = ProjectRuntimeSelectionStore(prefs)
        store.write("project-a-document", ProjectRuntimeSelection.EMBEDDED_R)
        val key = prefs.all.keys.single()
        prefs.edit().putString(key, "UNKNOWN_RUNTIME").apply()

        assertEquals(ProjectRuntimeSelection.TERMUX, store.read("project-a-document"))
    }

    @Test
    fun selectionMapsToTheExpectedControlRequest() {
        assertEquals(
            RuntimeControlRequest.EXTERNAL_PROVIDER,
            ProjectRuntimeSelection.TERMUX.controlRequest,
        )
        assertEquals(
            RuntimeControlRequest.EMBEDDED_R,
            ProjectRuntimeSelection.EMBEDDED_R.controlRequest,
        )
    }

    @Test
    fun clearRestoresTheLegacyDefault() {
        val prefs = MemorySharedPreferences()
        val store = ProjectRuntimeSelectionStore(prefs)
        store.write("project-a-document", ProjectRuntimeSelection.EMBEDDED_R)

        store.clear("project-a-document")

        assertEquals(ProjectRuntimeSelection.TERMUX, store.read("project-a-document"))
    }

    private class MemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()

        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            (values[key] as? Set<String>)?.toSet() ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            values[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = linkedMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                put(key, value)

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
                put(key, values?.toSet())

            override fun putInt(key: String, value: Int): SharedPreferences.Editor =
                put(key, value)

            override fun putLong(key: String, value: Long): SharedPreferences.Editor =
                put(key, value)

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
                put(key, value)

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
                put(key, value)

            override fun remove(key: String): SharedPreferences.Editor {
                changes[key] = REMOVED
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }

            override fun commit(): Boolean {
                commitChanges()
                return true
            }

            override fun apply() {
                commitChanges()
            }

            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                changes[key] = value ?: REMOVED
                return this
            }

            private fun commitChanges() {
                if (clearRequested) values.clear()
                changes.forEach { (key, value) ->
                    if (value === REMOVED) values.remove(key) else values[key] = value
                }
            }
        }

        private companion object {
            val REMOVED = Any()
        }
    }
}
