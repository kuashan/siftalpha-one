package com.siftalpha.studio.runtime

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeLifecycleStoreTest {

    @Test
    fun readsStringBooleanValuesFromLegacyStorage() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(
            projectKey = PROJECT_KEY,
            environmentReady = false,
            runtimeState = RuntimeState.RUNNING,
            failureReason = null,
        )

        val presentKey = keyEnding(prefs, "env_present")
        val valueKey = keyEnding(prefs, "env_value")
        prefs.edit()
            .putString(presentKey, "true")
            .putString(valueKey, "false")
            .apply()

        val snapshot = store.read(PROJECT_KEY)

        assertEquals(false, snapshot.environmentReady)
        assertEquals(RuntimeState.RUNNING, snapshot.runtimeState)
        assertEquals(true, prefs.all[presentKey])
        assertEquals(false, prefs.all[valueKey])
    }

    @Test
    fun providerEnvironmentReadinessIsIndependent() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(
            projectKey = PROJECT_KEY,
            environmentReady = true,
            runtimeState = RuntimeState.UNKNOWN,
            failureReason = null,
            runtimeSelection = ProjectRuntimeSelection.TERMUX,
        )
        store.write(
            projectKey = PROJECT_KEY,
            environmentReady = false,
            runtimeState = RuntimeState.UNKNOWN,
            failureReason = null,
            runtimeSelection = ProjectRuntimeSelection.EMBEDDED_R,
        )

        val snapshot = store.read(PROJECT_KEY)

        assertEquals(true, snapshot.environmentReadyFor(ProjectRuntimeSelection.TERMUX))
        assertEquals(false, snapshot.environmentReadyFor(ProjectRuntimeSelection.EMBEDDED_R))
    }

    @Test
    fun legacyExternalReadinessDoesNotLeakIntoEmbeddedReadiness() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(
            projectKey = PROJECT_KEY,
            environmentReady = true,
            runtimeState = RuntimeState.RUNNING,
            failureReason = null,
        )

        val snapshot = store.read(PROJECT_KEY)

        assertEquals(true, snapshot.externalEnvironmentReady)
        assertNull(snapshot.embeddedEnvironmentReady)
    }

    @Test
    fun readsBooleanValuesWrittenByCurrentVersion() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(
            projectKey = PROJECT_KEY,
            environmentReady = true,
            runtimeState = RuntimeState.EXITED_ERROR,
            failureReason = "redacted failure",
        )

        val snapshot = store.read(PROJECT_KEY)

        assertEquals(true, snapshot.environmentReady)
        assertEquals(RuntimeState.EXITED_ERROR, snapshot.runtimeState)
        assertEquals("redacted failure", snapshot.failureReason)
    }

    @Test
    fun migratedValuesCanBeReadAgainByANewStoreInstance() {
        val prefs = MemorySharedPreferences()
        val firstStore = RuntimeLifecycleStore(prefs)
        firstStore.write(
            projectKey = PROJECT_KEY,
            environmentReady = true,
            runtimeState = RuntimeState.RUNNING,
            failureReason = null,
        )

        val presentKey = keyEnding(prefs, "env_present")
        val valueKey = keyEnding(prefs, "env_value")
        prefs.edit()
            .putString(presentKey, "true")
            .putString(valueKey, "true")
            .apply()

        assertEquals(true, firstStore.read(PROJECT_KEY).environmentReady)

        val reread = RuntimeLifecycleStore(prefs).read(PROJECT_KEY)

        assertEquals(true, reread.environmentReady)
        assertEquals(RuntimeState.RUNNING, reread.runtimeState)
        assertEquals(true, prefs.all[presentKey])
        assertEquals(true, prefs.all[valueKey])
    }

    @Test
    fun emptyStorageUsesSafeDefaults() {
        val snapshot = RuntimeLifecycleStore(MemorySharedPreferences()).read(PROJECT_KEY)

        assertNull(snapshot.environmentReady)
        assertEquals(RuntimeState.UNKNOWN, snapshot.runtimeState)
        assertNull(snapshot.failureReason)
    }

    @Test
    fun malformedHistoricalTypesUseSafeDefaultsWithoutCrashing() {
        val prefs = MemorySharedPreferences()
        val store = RuntimeLifecycleStore(prefs)
        store.write(
            projectKey = PROJECT_KEY,
            environmentReady = true,
            runtimeState = RuntimeState.RUNNING,
            failureReason = "failure",
        )

        prefs.edit()
            .putInt(keyEnding(prefs, "env_present"), 7)
            .putString(keyEnding(prefs, "env_value"), "not-a-boolean")
            .putBoolean(keyEnding(prefs, "state"), true)
            .putInt(keyEnding(prefs, "failure"), 9)
            .apply()

        val snapshot = store.read(PROJECT_KEY)

        assertNull(snapshot.environmentReady)
        assertEquals(RuntimeState.UNKNOWN, snapshot.runtimeState)
        assertNull(snapshot.failureReason)
    }

    private fun keyEnding(prefs: SharedPreferences, suffix: String): String =
        prefs.all.keys.single { it.endsWith(":$suffix") }

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

    private companion object {
        const val PROJECT_KEY = "project-for-migration"
    }
}
