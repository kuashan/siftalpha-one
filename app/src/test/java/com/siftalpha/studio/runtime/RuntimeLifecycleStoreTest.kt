package com.siftalpha.studio.runtime

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeLifecycleStoreTest {
    @Test
    fun providerEnvironmentReadinessIsIndependent() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(PROJECT_KEY, true, RuntimeState.UNKNOWN, null, ProjectRuntimeSelection.TERMUX)
        store.write(PROJECT_KEY, false, RuntimeState.UNKNOWN, null, ProjectRuntimeSelection.EMBEDDED_R)
        val snapshot = store.read(PROJECT_KEY)
        assertEquals(true, snapshot.environmentReadyFor(ProjectRuntimeSelection.TERMUX))
        assertEquals(false, snapshot.environmentReadyFor(ProjectRuntimeSelection.EMBEDDED_R))
    }

    @Test
    fun legacyExternalEnvironmentDoesNotBecomeEmbeddedReady() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(PROJECT_KEY, true, RuntimeState.RUNNING, null)
        val snapshot = store.read(PROJECT_KEY)
        assertEquals(true, snapshot.environmentReady)
        assertNull(snapshot.embeddedEnvironmentReady)
        assertEquals(RuntimeState.RUNNING, snapshot.runtimeState)
    }

    @Test
    fun emptyStorageUsesSafeDefaults() {
        val snapshot = RuntimeLifecycleStore(MemorySharedPreferences()).read(PROJECT_KEY)
        assertNull(snapshot.externalEnvironmentReady)
        assertNull(snapshot.embeddedEnvironmentReady)
        assertEquals(RuntimeState.UNKNOWN, snapshot.runtimeState)
        assertNull(snapshot.failureReason)
    }

    private class MemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()
        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            (values[key] as? Set<String>)?.toSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        private inner class Editor : SharedPreferences.Editor {
            private val changes = linkedMapOf<String, Any?>()
            private var clearRequested = false
            override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = put(key, values?.toSet())
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)
            override fun remove(key: String): SharedPreferences.Editor { changes[key] = REMOVED; return this }
            override fun clear(): SharedPreferences.Editor { clearRequested = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearRequested) values.clear()
                changes.forEach { (key, value) ->
                    if (value === REMOVED) values.remove(key) else values[key] = value
                }
            }
            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                changes[key] = value ?: REMOVED
                return this
            }
        }
        private companion object { val REMOVED = Any() }
    }

    private companion object { const val PROJECT_KEY = "project-for-migration" }
}
