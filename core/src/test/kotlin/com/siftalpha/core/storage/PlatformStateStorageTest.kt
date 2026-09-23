package com.siftalpha.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformStateStorageTest {
    @Test
    fun primitiveValuesRemainTyped() {
        val storage = MemoryStorage()

        storage.mutate(
            StateStorageMutation(
                writes = mapOf(
                    "text" to StoredStateValue.Text("value"),
                    "bool" to StoredStateValue.Bool(true),
                    "long" to StoredStateValue.LongNumber(42L),
                    "int" to StoredStateValue.IntNumber(7),
                ),
            ),
        )

        assertEquals("value", storage.readText("text"))
        assertEquals(true, storage.readBoolean("bool"))
        assertEquals(42L, storage.readLong("long"))
        assertEquals(7, storage.readInt("int"))
        assertNull(storage.readText("bool"))
    }

    @Test
    fun mutationCanWriteAndRemoveDifferentKeysTogether() {
        val storage = MemoryStorage()
        storage.mutate(
            StateStorageMutation(
                writes = mapOf(
                    "keep" to StoredStateValue.Text("old"),
                    "remove" to StoredStateValue.Text("gone"),
                ),
            ),
        )

        storage.mutate(
            StateStorageMutation(
                writes = mapOf("keep" to StoredStateValue.Text("new")),
                removals = setOf("remove"),
            ),
        )

        assertEquals("new", storage.readText("keep"))
        assertFalse(storage.contains("remove"))
        assertTrue(storage.contains("keep"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sameKeyCannotBeWrittenAndRemovedInOneMutation() {
        StateStorageMutation(
            writes = mapOf("same" to StoredStateValue.Text("value")),
            removals = setOf("same"),
        )
    }

    private class MemoryStorage : PlatformStateStorage {
        private val values = linkedMapOf<String, StoredStateValue>()

        override fun read(key: String): StoredStateValue? = values[key]

        override fun mutate(mutation: StateStorageMutation) {
            mutation.removals.forEach(values::remove)
            values.putAll(mutation.writes)
        }
    }
}
