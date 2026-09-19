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
}
