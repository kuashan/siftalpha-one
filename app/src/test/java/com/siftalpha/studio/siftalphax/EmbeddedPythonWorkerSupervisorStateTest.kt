package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWorkerSupervisorStateTest {
    @Test
    fun freshSupervisorIsDisconnectedWithoutSyntheticWorkerSnapshot() {
        val state = EmbeddedPythonWorkerSupervisorState()

        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_DISCONNECTED,
            state.snapshot().connectionState,
        )
        assertEquals(0L, state.snapshot().connectionEpoch)
        assertNull(state.snapshot().workerSnapshot)
        assertEquals("", state.snapshot().lastWorkerInstanceId)
    }

    @Test
    fun connectBeginsNewEpochAndDuplicateConnectIsIdempotentlyRejected() {
        val state = EmbeddedPythonWorkerSupervisorState()

        val first = state.beginConnect()
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED,
            first.result,
        )
        assertEquals(1L, first.epoch)
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BINDING,
            state.snapshot().connectionState,
        )
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ALREADY_BINDING,
            state.beginConnect().result,
        )
    }

    @Test
    fun successfulConnectRetainsSnapshotAndDuplicateConnectIsAlreadyConnected() {
        val state = EmbeddedPythonWorkerSupervisorState()
        val epoch = state.beginConnect().epoch
        val worker = workerSnapshot("worker-supervisor-a")

        assertTrue(state.completeConnected(epoch, worker))
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED,
            state.snapshot().connectionState,
        )
        assertEquals(worker, state.snapshot().workerSnapshot)
        assertEquals("worker-supervisor-a", state.snapshot().lastWorkerInstanceId)
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ALREADY_CONNECTED,
            state.beginConnect().result,
        )
    }

    @Test
    fun explicitDisconnectClearsLiveSnapshotButDoesNotTerminateWorkerOrForgetLastIdentity() {
        val state = connectedState("worker-supervisor-disconnect")

        state.disconnect()

        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_DISCONNECTED,
            state.snapshot().connectionState,
        )
        assertNull(state.snapshot().workerSnapshot)
        assertEquals("worker-supervisor-disconnect", state.snapshot().lastWorkerInstanceId)
    }

    @Test
    fun staleConnectedCompletionAfterConnectBIsIgnored() {
        val state = EmbeddedPythonWorkerSupervisorState()
        val epochA = state.beginConnect().epoch
        state.disconnect()
        val epochB = state.beginConnect().epoch

        assertEquals(3L, epochB)
        assertTrue(state.completeConnected(epochB, workerSnapshot("worker-supervisor-b")))
        assertFalse(state.completeConnected(epochA, workerSnapshot("worker-supervisor-a")))
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED,
            state.snapshot().connectionState,
        )
        assertEquals("worker-supervisor-b", state.snapshot().workerSnapshot?.workerInstanceId)
        assertEquals(3L, state.snapshot().connectionEpoch)
    }

    @Test
    fun staleDeathAfterConnectBIsIgnored() {
        val state = EmbeddedPythonWorkerSupervisorState()
        val epochA = state.beginConnect().epoch
        state.disconnect()
        val epochB = state.beginConnect().epoch
        assertTrue(state.completeConnected(epochB, workerSnapshot("worker-supervisor-b")))

        assertFalse(state.markConnectionLost(epochA))
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED,
            state.snapshot().connectionState,
        )
        assertEquals("worker-supervisor-b", state.snapshot().lastWorkerInstanceId)
    }

    @Test
    fun currentDeathMovesToLostClearsCurrentSnapshotAndRetainsLastIdentity() {
        val state = connectedState("worker-supervisor-death")
        val epoch = state.snapshot().connectionEpoch

        assertTrue(state.markConnectionLost(epoch))
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST,
            state.snapshot().connectionState,
        )
        assertNull(state.snapshot().workerSnapshot)
        assertEquals("worker-supervisor-death", state.snapshot().lastWorkerInstanceId)
    }

    @Test
    fun sameWorkerReconnectIsAcceptedAndKeepsWorkerInstanceIdentity() {
        val state = connectedState("worker-supervisor-same")
        val worker = state.snapshot().workerSnapshot!!
        val firstEpoch = state.snapshot().connectionEpoch
        state.markConnectionLost(firstEpoch)

        val reconnectEpoch = state.beginConnect().epoch
        assertTrue(state.completeConnected(reconnectEpoch, worker))
        assertEquals(worker.workerInstanceId, state.snapshot().workerSnapshot?.workerInstanceId)
        assertEquals(worker.workerInstanceId, state.snapshot().lastWorkerInstanceId)
    }

    @Test
    fun replacementWorkerIsAcceptedOnlyAsASeparateEpoch() {
        val state = connectedState("worker-supervisor-old")
        val oldEpoch = state.snapshot().connectionEpoch
        state.markConnectionLost(oldEpoch)
        val replacementEpoch = state.beginConnect().epoch

        assertTrue(state.completeConnected(replacementEpoch, workerSnapshot("worker-supervisor-new")))
        assertNotEquals(
            state.snapshot().lastWorkerInstanceId,
            "worker-supervisor-old",
        )
        assertEquals("worker-supervisor-new", state.snapshot().workerSnapshot?.workerInstanceId)
    }

    @Test
    fun refreshUpdatesSameWorkerAndRejectsWorkerIdentityChange() {
        val state = connectedState("worker-supervisor-refresh")
        val epoch = state.snapshot().connectionEpoch
        val updated = workerSnapshot(
            workerInstanceId = "worker-supervisor-refresh",
            executionState = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
        )

        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_OK,
            state.applyRefresh(epoch, updated),
        )
        assertEquals(updated, state.snapshot().workerSnapshot)
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_PROTOCOL_MISMATCH,
            state.applyRefresh(epoch, workerSnapshot("worker-supervisor-other")),
        )
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST,
            state.snapshot().connectionState,
        )
        assertNull(state.snapshot().workerSnapshot)
        assertEquals("worker-supervisor-refresh", state.snapshot().lastWorkerInstanceId)
    }

    @Test
    fun nullBindingOrBindFailureCannotBeResurrectedByLateCompletion() {
        val state = EmbeddedPythonWorkerSupervisorState()
        val epoch = state.beginConnect().epoch

        assertTrue(state.failBind(epoch))
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BIND_FAILED,
            state.snapshot().connectionState,
        )
        assertFalse(state.completeConnected(epoch, workerSnapshot("worker-supervisor-late")))
        assertNull(state.snapshot().workerSnapshot)
    }

    @Test
    fun deathDuringBindingPreventsLateConnectedCompletion() {
        val state = EmbeddedPythonWorkerSupervisorState()
        val epoch = state.beginConnect().epoch

        assertTrue(state.markConnectionLost(epoch))
        assertFalse(state.completeConnected(epoch, workerSnapshot("worker-supervisor-race")))
        assertEquals(
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST,
            state.snapshot().connectionState,
        )
    }

    private fun connectedState(workerInstanceId: String): EmbeddedPythonWorkerSupervisorState {
        val state = EmbeddedPythonWorkerSupervisorState()
        val epoch = state.beginConnect().epoch
        assertTrue(state.completeConnected(epoch, workerSnapshot(workerInstanceId)))
        return state
    }

    private fun workerSnapshot(
        workerInstanceId: String,
        executionState: Int = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED,
    ): EmbeddedPythonWorkerExecutionSnapshotV1 =
        EmbeddedPythonWorkerExecutionSnapshotV1(
            workerInstanceId = workerInstanceId,
            workerPid = 4321,
            workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            processBindingId = "",
            executionState = executionState,
            executionSessionId = "",
            executionGeneration = 0L,
            projectIdentity = "",
            executionRoot = "",
            entrypoint = "",
            workingDirectory = "",
            stopRequested = false,
            hasExitCode = false,
            exitCode = 0,
            stdout = "",
            stderr = "",
        )
}
