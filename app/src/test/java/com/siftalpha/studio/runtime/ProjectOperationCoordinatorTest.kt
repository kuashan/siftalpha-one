package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOperationCoordinatorTest {
    @Test
    fun normalAndDeveloperSurfacesShareOneOperationOwner() {
        val coordinator = coordinator(listenForExternalResults = false)

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
        assertEquals(
            ExternalResultDisposition.FENCED,
            coordinator.consumeExternalResultDisposition(7101),
        )
        assertNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STOP,
            ),
        )
    }

    @Test
    fun externalPrepareAndStopReceiveTheirActionDeadlines() {
        var now = 10_000L
        val coordinator = coordinator(
            nowEpochMs = { now },
            listenForExternalResults = false,
        )

        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
        )!!
        assertEquals(now + RuntimeOperationContract.PREPARE_TIMEOUT_MS, prepare.deadlineAtEpochMs)

        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
        )!!
        assertEquals(
            now + RuntimeOperationContract.EXTERNAL_STOP_TIMEOUT_MS,
            stop.deadlineAtEpochMs,
        )
    }

    @Test
    fun externalWatchdogExpiresOperationAndReleasesTheProjectLock() {
        var now = 20_000L
        val watchdog = ManualWatchdog()
        val timeouts = mutableListOf<ProjectOperationCoordinator.ExternalTimeout>()
        val coordinator = coordinator(
            nowEpochMs = { now },
            watchdog = watchdog,
            listenForExternalResults = false,
        )
        coordinator.addTimeoutListener(timeouts::add)

        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7201,
        )!!
        now += RuntimeOperationContract.STOP_TIMEOUT_MS + 1L
        watchdog.fire("project-a", stop.generation)

        assertNull(coordinator.current("project-a"))
        assertEquals(1, timeouts.size)
        assertEquals(RuntimeOperationAction.STOP, timeouts.single().action)
        assertEquals("RUNTIME_OPERATION_TIMED_OUT:STOP", timeouts.single().failureReason)

        // A timed-out STOP cannot leave the project permanently locked.
        assertNotNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STATUS,
            ),
        )
    }

    @Test
    fun stopTimeoutFencesLateStopResultAndDoesNotCompleteIt() {
        var now = 30_000L
        val watchdog = ManualWatchdog()
        val completions = mutableListOf<ProjectOperationCoordinator.ExternalCompletion>()
        val coordinator = coordinator(
            nowEpochMs = { now },
            watchdog = watchdog,
            listenForExternalResults = true,
        )
        coordinator.addCompletionListener(completions::add)
        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7301,
        )!!

        now += RuntimeOperationContract.STOP_TIMEOUT_MS + 1L
        watchdog.fire("project-a", stop.generation)
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7301,
                stdout = "SIFTALPHA_STATUS=STOPPED_BY_USER",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertTrue(completions.isEmpty())
        assertEquals(
            ExternalResultDisposition.FENCED,
            coordinator.consumeExternalResultDisposition(7301),
        )
    }

    @Test
    fun latePrepareResultAfterStopCannotMutateTheNewGeneration() {
        val coordinator = coordinator(listenForExternalResults = true)
        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7401,
        )!!
        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7402,
        )!!

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7401,
                stdout = "SIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(stop.generation, coordinator.current("project-a")?.generation)
        assertEquals(RuntimeOperationAction.STOP, coordinator.current("project-a")?.action)
        assertEquals(
            ExternalResultDisposition.FENCED,
            coordinator.consumeExternalResultDisposition(7401),
        )
        assertEquals(ExternalResultDisposition.ACCEPTED, coordinator.consumeExternalResultDisposition(7402))
        assertEquals(prepare.generation + 1L, stop.generation)
    }

    @Test
    fun successfulExternalCallbackBeforeDeadlineUsesTheOriginalCompletionPath() {
        val coordinator = coordinator(listenForExternalResults = true)
        val completions = mutableListOf<ProjectOperationCoordinator.ExternalCompletion>()
        coordinator.addCompletionListener(completions::add)
        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7501,
        )!!

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7501,
                stdout = "SIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertNull(coordinator.current("project-a"))
        assertEquals(1, completions.size)
        assertEquals(prepare.generation, completions.single().generation)
        assertEquals(ExternalResultDisposition.ACCEPTED, coordinator.consumeExternalResultDisposition(7501))
    }

    @Test
    fun timeoutOfProjectADoesNotAffectProjectB() {
        var now = 40_000L
        val watchdog = ManualWatchdog()
        val coordinator = coordinator(
            nowEpochMs = { now },
            watchdog = watchdog,
            listenForExternalResults = false,
        )
        val a = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7601,
        )!!
        val b = coordinator.begin(
            projectId = "project-b",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.START,
            executionId = 7602,
        )!!

        now += RuntimeOperationContract.STOP_TIMEOUT_MS + 1L
        watchdog.fire("project-a", a.generation)

        assertNull(coordinator.current("project-a"))
        assertEquals(b.generation, coordinator.current("project-b")?.generation)
        assertEquals(RuntimeOperationAction.START, coordinator.current("project-b")?.action)
    }

    private fun coordinator(
        nowEpochMs: () -> Long = { 1_000L },
        listenForExternalResults: Boolean,
        watchdog: RuntimeOperationWatchdog = ManualWatchdog(),
    ) = ProjectOperationCoordinator(
        operationStore = MemoryOperationStore(),
        nowEpochMs = nowEpochMs,
        listenForExternalResults = listenForExternalResults,
        watchdog = watchdog,
    )

    private class ManualWatchdog : RuntimeOperationWatchdog {
        private data class Key(val projectId: String, val generation: Long)

        private val callbacks = linkedMapOf<Key, () -> Unit>()

        override fun schedule(
            record: RuntimeOperationRecord,
            delayMs: Long,
            onTimeout: () -> Unit,
        ) {
            callbacks[Key(record.projectId, record.generation)] = onTimeout
        }

        override fun cancel(projectId: String, generation: Long) {
            callbacks.remove(Key(projectId, generation))
        }

        fun fire(projectId: String, generation: Long) {
            callbacks.remove(Key(projectId, generation))?.invoke()
        }
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
