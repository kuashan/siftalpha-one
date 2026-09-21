package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeOperationLifecycleTest {
    @Test
    fun operationLabelsAreProviderNeutral() {
        assertEquals(RuntimeLifecycleState.PREPARING, RuntimeOperationContract.lifecycleState(RuntimeOperationAction.PREPARE))
        assertEquals(RuntimeLifecycleState.STARTING, RuntimeOperationContract.lifecycleState(RuntimeOperationAction.START))
        assertEquals(RuntimeLifecycleState.CHECKING, RuntimeOperationContract.lifecycleState(RuntimeOperationAction.STATUS))
        assertEquals(RuntimeLifecycleState.CHECKING, RuntimeOperationContract.lifecycleState(RuntimeOperationAction.LOGS))
        assertEquals(RuntimeLifecycleState.STOPPING, RuntimeOperationContract.lifecycleState(RuntimeOperationAction.STOP))
        assertEquals(RuntimeLifecycleState.CLEANING, RuntimeOperationContract.lifecycleState(RuntimeOperationAction.CLEAN))
    }

    @Test
    fun trackerAllowsOneOperationAndGivesStopPriority() {
        var now = 1_000L
        val tracker = RuntimeOperationTracker { now }
        val prepare = tracker.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.INTERNAL,
            action = RuntimeOperationAction.PREPARE,
        )
        assertNotNull(prepare)
        assertNull(
            tracker.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STATUS,
            ),
        )
        val stop = tracker.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.INTERNAL,
            action = RuntimeOperationAction.STOP,
        )
        assertNotNull(stop)
        assertEquals(RuntimeOperationAction.STOP, tracker.current("project-a")?.action)
        assertNull(
            tracker.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.INTERNAL,
                action = RuntimeOperationAction.STOP,
            ),
        )
        now += RuntimeOperationContract.STOP_TIMEOUT_MS + 1L
        assertEquals(1, tracker.markExpired(now).size)
        assertEquals(RuntimeOperationPhase.TIMED_OUT, tracker.current("project-a")?.phase)
        assertTrue(tracker.clearTerminal("project-a", stop!!.generation))
        assertFalse(tracker.clearTerminal("project-a", stop.generation))
    }

    @Test
    fun terminalOperationMayBeReplacedOnlyAfterExplicitClear() {
        val tracker = RuntimeOperationTracker { 2_000L }
        val first = tracker.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.CLEAN,
        )!!
        assertTrue(tracker.finish("project-a", first.generation, RuntimeOperationPhase.SUCCESS))
        assertNull(
            tracker.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.START,
            ),
        )
        assertTrue(tracker.clearTerminal("project-a", first.generation))
        val second = tracker.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.START,
        )!!
        assertTrue(second.generation > first.generation)
    }

    @Test
    fun internalAndExternalUseTheSameLifecycleAndConflictRules() {
        RuntimeOperationAction.entries.forEach { action ->
            val internal = RuntimeOperationContract.lifecycleState(action)
            val external = RuntimeOperationContract.lifecycleState(action)
            assertEquals(internal, external)
        }

        val tracker = RuntimeOperationTracker { 3_000L }
        val externalPrepare = tracker.begin(
            projectId = "external-project",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
        )!!
        assertNull(
            tracker.begin(
                projectId = "external-project",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.CLEAN,
            ),
        )
        assertTrue(
            tracker.finish(
                "external-project",
                externalPrepare.generation,
                RuntimeOperationPhase.CANCELLED,
            ),
        )
        assertTrue(tracker.clearTerminal("external-project", externalPrepare.generation))

        val internalClean = tracker.begin(
            projectId = "internal-project",
            provider = RuntimeOperationProvider.INTERNAL,
            action = RuntimeOperationAction.CLEAN,
        )!!
        assertNull(
            tracker.begin(
                projectId = "internal-project",
                provider = RuntimeOperationProvider.INTERNAL,
                action = RuntimeOperationAction.PREPARE,
            ),
        )
        assertTrue(internalClean.generation > 0L)
    }

    @Test
    fun bothProvidersReceiveActionDeadlines() {
        val startedAt = 4_000L

        RuntimeOperationAction.entries.forEach { action ->
            assertEquals(
                startedAt + RuntimeOperationContract.timeoutMs(action),
                RuntimeOperationContract.deadlineAtEpochMs(
                    provider = RuntimeOperationProvider.INTERNAL,
                    action = action,
                    startedAtEpochMs = startedAt,
                ),
            )
            assertEquals(
                startedAt + RuntimeOperationContract.timeoutMs(action),
                RuntimeOperationContract.deadlineAtEpochMs(
                    provider = RuntimeOperationProvider.EXTERNAL,
                    action = action,
                    startedAtEpochMs = startedAt,
                ),
            )
        }
    }

    @Test
    fun externalOperationExpiresAfterItsControlDeadline() {
        var now = 5_000L
        val tracker = RuntimeOperationTracker { now }
        val externalStatus = tracker.begin(
            projectId = "external-project",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STATUS,
        )!!

        assertEquals(
            now + RuntimeOperationContract.STATUS_TIMEOUT_MS,
            externalStatus.deadlineAtEpochMs,
        )
        now += RuntimeOperationContract.STATUS_TIMEOUT_MS + 1L

        assertEquals(1, tracker.markExpired(now).size)
        assertEquals(RuntimeOperationPhase.TIMED_OUT, tracker.current("external-project")?.phase)
    }

    @Test
    fun externalStopUsesTheShortStopDeadline() {
        assertEquals(
            45_000L,
            RuntimeOperationContract.timeoutMs(RuntimeOperationAction.STOP),
        )
        assertEquals(
            45_000L,
            RuntimeOperationContract.deadlineAtEpochMs(
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STOP,
                startedAtEpochMs = 1_000L,
            )!! - 1_000L,
        )
    }

    @Test
    fun timeoutOutcomeDoesNotClaimThatStopSucceeded() {
        val outcome = RuntimeOperationContract.timeoutOutcome(
            action = RuntimeOperationAction.STOP,
            currentEnvironmentReady = true,
        )

        assertEquals(RuntimeState.UNKNOWN, outcome.runtimeState)
        assertEquals(true, outcome.environmentReady)
        assertEquals("RUNTIME_OPERATION_TIMED_OUT:STOP", outcome.failureReason)
    }

    @Test
    fun prepareTimeoutPreservesAnAlreadyReadyEnvironment() {
        val outcome = RuntimeOperationContract.timeoutOutcome(
            action = RuntimeOperationAction.PREPARE,
            currentEnvironmentReady = true,
            previousEnvironmentReady = true,
        )

        assertEquals(RuntimeState.UNKNOWN, outcome.runtimeState)
        assertEquals(true, outcome.environmentReady)
        assertEquals("RUNTIME_OPERATION_TIMED_OUT:PREPARE", outcome.failureReason)
    }

    @Test
    fun internalOperationStillExpiresAfterItsProviderDeadline() {
        var now = 6_000L
        val tracker = RuntimeOperationTracker { now }
        val internalStatus = tracker.begin(
            projectId = "internal-project",
            provider = RuntimeOperationProvider.INTERNAL,
            action = RuntimeOperationAction.STATUS,
        )!!

        assertEquals(
            now + RuntimeOperationContract.STATUS_TIMEOUT_MS,
            internalStatus.deadlineAtEpochMs,
        )
        now += RuntimeOperationContract.STATUS_TIMEOUT_MS + 1L

        assertEquals(1, tracker.markExpired(now).size)
        assertEquals(RuntimeOperationPhase.TIMED_OUT, tracker.current("internal-project")?.phase)
    }
}
