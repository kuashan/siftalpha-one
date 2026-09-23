package com.siftalpha.studio.runtime

import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.StateStorageMutation
import com.siftalpha.core.storage.StoredStateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeOperationStoreTest {
    @Test
    fun operationRoundTripsThroughPlatformStoragePort() {
        val storage = MemoryStorage()
        val store = RuntimeOperationStore(storage)
        val record = RuntimeOperationRecord(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.START,
            executionId = 44,
            generation = 3L,
            startedAtEpochMs = 1_000L,
            deadlineAtEpochMs = 31_000L,
            userVisible = true,
            phase = RuntimeOperationPhase.ACTIVE,
        )

        store.write(record)

        assertEquals(record, RuntimeOperationStore(storage).read("project-a"))
        assertEquals(3L, RuntimeOperationStore(storage).lastGeneration("project-a"))
    }

    @Test
    fun clearingCurrentOperationPreservesGenerationFence() {
        val storage = MemoryStorage()
        val store = RuntimeOperationStore(storage)
        store.write(
            RuntimeOperationRecord(
                projectId = "project-a",
                provider = RuntimeOperationProvider.INTERNAL,
                action = RuntimeOperationAction.STOP,
                executionId = null,
                generation = 7L,
                startedAtEpochMs = 2_000L,
                deadlineAtEpochMs = 47_000L,
                phase = RuntimeOperationPhase.SUCCESS,
            ),
        )

        store.clear("project-a")

        assertNull(store.read("project-a"))
        assertEquals(7L, store.lastGeneration("project-a"))
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
