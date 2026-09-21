package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOperationCoordinatorTest {
    @Test
    fun normalAndDeveloperSurfacesShareOneOperationOwner() {
        val coordinator = ProjectOperationCoordinator(
            operationStore = MemoryOperationStore(),
            listenForExternalResults = false,
        )

        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
        )!!
        assertNotNull(
            coordinator.bindExternalExecution(
                projectId = "project-a",
                generation = prepare.generation,
                executionId = 7101,
            ),
        )

        // The second surface sees the same current operation and cannot create another generation.
        assertNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.START,
            ),
        )

        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
        )
        assertNotNull(stop)
        assertTrue(stop!!.generation > prepare.generation)
        assertEquals(RuntimeOperationAction.STOP, coordinator.current("project-a")?.action)
        assertNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STOP,
            ),
        )
    }

    @Test
    fun stopIsProjectScopedAndDoesNotCancelNeighboringProject() {
        val coordinator = ProjectOperationCoordinator(
            operationStore = MemoryOperationStore(),
            listenForExternalResults = false,
        )
        val a = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.START,
        )!!
        val b = coordinator.begin(
            projectId = "project-b",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.START,
        )!!

        assertTrue(coordinator.finish("project-a", a.generation, RuntimeOperationPhase.CANCELLED))
        assertEquals(RuntimeOperationAction.START, coordinator.current("project-b")?.action)
        assertNull(coordinator.current("project-a"))
    }

    private class MemoryOperationStore : RuntimeOperationRecordStore {
        private val records = linkedMapOf<String, RuntimeOperationRecord>()
        private val generations = linkedMapOf<String, Long>()

        override fun read(projectId: String): RuntimeOperationRecord? = records[projectId]

        override fun lastGeneration(projectId: String): Long = generations[projectId] ?: 0L

        override fun write(record: RuntimeOperationRecord) {
            records[record.projectId] = record
            generations[record.projectId] = maxOf(generations[record.projectId] ?: 0L, record.generation)
        }

        override fun clear(projectId: String) {
            records.remove(projectId)
        }
    }
}
