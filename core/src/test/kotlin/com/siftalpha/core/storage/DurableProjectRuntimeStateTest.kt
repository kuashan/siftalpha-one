package com.siftalpha.core.storage

import com.siftalpha.core.lifecycle.RuntimeExecutionState
import com.siftalpha.core.operation.ProjectOperationAction
import com.siftalpha.core.operation.ProjectOperationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableProjectRuntimeStateTest {
    @Test
    fun runtimeStateRoundTripsThroughPlatformPort() {
        val storage = MemoryStorage()
        val store = DurableRuntimeStateStore(storage)

        store.write(
            projectId = "project-a",
            providerId = "host",
            environmentReady = true,
            runtimeState = RuntimeExecutionState.RUNNING,
            failureReason = null,
        )

        assertEquals(
            DurableRuntimeSnapshot(true, RuntimeExecutionState.RUNNING, null),
            store.read("project-a", "host"),
        )
    }

    @Test
    fun operationClearPreservesGenerationFence() {
        val storage = MemoryStorage()
        val store = DurableProjectOperationStore(storage)
        val record = DurableProjectOperationRecord(
            projectId = "project-a",
            providerId = "host",
            action = ProjectOperationAction.START,
            phase = ProjectOperationPhase.ACTIVE,
            generation = 7,
            startedAtEpochMs = 1000,
        )

        store.write(record)
        assertEquals(record, store.read("project-a"))
        store.clearCurrent("project-a")

        assertNull(store.read("project-a"))
        assertEquals(7L, store.lastGeneration("project-a"))
    }

    @Test
    fun pendingRunIntentRoundTripsWithoutSecrets() {
        val storage = MemoryStorage()
        val store = PendingRunIntentStore(storage)

        store.mark("project-a", PendingRunReason.CONFIGURATION, nowEpochMs = 1234)

        assertEquals(
            PendingRunIntent("project-a", PendingRunReason.CONFIGURATION, 1234),
            store.read("project-a"),
        )
        store.clear("project-a")
        assertNull(store.read("project-a"))
    }

    @Test
    fun ownershipRoundTrips() {
        val storage = MemoryStorage()
        val store = DurableRuntimeOwnershipStore(storage)
        val identity = DurableRuntimeOwnership(
            projectId = "project-a",
            generation = 3,
            platformHandle = "macos-pid:100",
            startedAtEpochMs = 2222,
        )
        store.write(identity)
        assertEquals(identity, store.read("project-a"))
        store.clear("project-a")
        assertNull(store.read("project-a"))
    }

    @Test
    fun cleanupPolicyProtectsRunningAndBusyProjects() {
        assertFalse(ProjectCleanupPolicy.evaluate(processRunning = true, operationActive = false).allowed)
        assertFalse(ProjectCleanupPolicy.evaluate(processRunning = false, operationActive = true).allowed)
        assertTrue(ProjectCleanupPolicy.evaluate(processRunning = false, operationActive = false).allowed)
    }

    @Test
    fun configurationPolicyNormalizesAndFindsMissingValues() {
        assertEquals(
            listOf("OPENAI_API_KEY"),
            ProjectConfigurationPolicy.missingEnvironmentNames(
                required = listOf(" OPENAI_API_KEY ", "OPENAI_API_KEY"),
                configured = emptySet(),
            ),
        )
        assertEquals(
            emptyList<String>(),
            ProjectConfigurationPolicy.missingEnvironmentNames(
                required = listOf("OPENAI_API_KEY"),
                configured = setOf("OPENAI_API_KEY"),
            ),
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
