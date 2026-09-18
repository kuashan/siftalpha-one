package com.siftalpha.studio.siftalphax

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedPythonWorkerSupervisorInstrumentedTest {
    @Test
    fun supervisorDistinguishesReconnectFromWorkerReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val supervisor = EmbeddedPythonWorkerSupervisor(context)
        var controlConnection: DirectWorkerConnection? = null
        var controlBound = false

        try {
            assertEquals(
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED,
                supervisor.connect(),
            )
            var firstSnapshot = awaitConnected(supervisor).workerSnapshot!!

            // Instrumentation can run after another test left a cached Worker behind. Establish
            // the required fresh baseline with the existing fenced process-exit primitive; no
            // production reset operation is used.
            if (!isFreshWorker(firstSnapshot)) {
                supervisor.disconnect()
                val staleConnection = DirectWorkerConnection()
                val staleBound = bindDirect(context, staleConnection)
                try {
                    val staleWorker = staleConnection.awaitWorker()
                    terminateDirectWorker(context, staleConnection, staleWorker)
                } finally {
                    if (staleBound) runCatching { context.unbindService(staleConnection.connection) }
                }
                assertEquals(
                    EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED,
                    supervisor.connect(),
                )
                firstSnapshot = awaitConnected(supervisor).workerSnapshot!!
            }

            assertFreshWorkerSnapshot(firstSnapshot)
            assertNotEquals(Process.myPid(), firstSnapshot.workerPid)
            val workerInstanceA = firstSnapshot.workerInstanceId

            // Keep the Worker process alive while this Supervisor voluntarily disconnects.
            controlConnection = DirectWorkerConnection()
            controlBound = bindDirect(context, controlConnection!!)
            val controlWorker = controlConnection!!.awaitWorker()
            assertEquals(workerInstanceA, controlWorker.getWorkerInstanceId())

            supervisor.disconnect()
            assertEquals(
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_DISCONNECTED,
                supervisor.snapshot().connectionState,
            )
            assertEquals(null, supervisor.snapshot().workerSnapshot)

            assertEquals(
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED,
                supervisor.connect(),
            )
            val sameWorkerSnapshot = awaitConnected(supervisor).workerSnapshot!!
            assertEquals(workerInstanceA, sameWorkerSnapshot.workerInstanceId)
            // PID equality is useful evidence here, but WorkerInstanceId remains the identity.
            assertEquals(firstSnapshot.workerPid, sameWorkerSnapshot.workerPid)

            val bindingA = testBinding("alpha44-supervisor-worker-a")
            assertEquals(
                EmbeddedPythonWorkerProtocol.BOUND_NEW,
                controlWorker.bind(bindingA),
            )

            terminateDirectWorker(context, controlConnection!!, controlWorker)
            controlBound = false
            awaitState(
                supervisor,
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST,
                10_000L,
                "Supervisor did not observe Worker A death",
            )
            assertEquals(null, supervisor.snapshot().workerSnapshot)
            assertEquals(workerInstanceA, supervisor.snapshot().lastWorkerInstanceId)

            // Worker loss is a terminal observation for this connection. There is no automatic
            // bind/restart/replay policy in this foundation.
            SystemClock.sleep(750L)
            assertEquals(
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST,
                supervisor.snapshot().connectionState,
            )

            assertEquals(
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED,
                supervisor.connect(),
            )
            val freshWorkerSnapshot = awaitConnected(supervisor).workerSnapshot!!
            assertNotEquals(workerInstanceA, freshWorkerSnapshot.workerInstanceId)
            assertNotEquals(Process.myPid(), freshWorkerSnapshot.workerPid)
            assertFreshWorkerSnapshot(freshWorkerSnapshot)
            assertEquals(
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_OK,
                supervisor.refreshSnapshot(),
            )
            assertEquals(
                freshWorkerSnapshot.workerInstanceId,
                supervisor.snapshot().workerSnapshot?.workerInstanceId,
            )

            // Clean the test-created fresh Worker through the existing explicit termination
            // primitive. The Supervisor never wraps this into automatic recovery.
            val freshControl = DirectWorkerConnection()
            val freshControlBound = bindDirect(context, freshControl)
            try {
                val freshWorker = freshControl.awaitWorker()
                val bindingB = testBinding("alpha44-supervisor-worker-b")
                assertEquals(
                    EmbeddedPythonWorkerProtocol.BOUND_NEW,
                    freshWorker.bind(bindingB),
                )
                terminateDirectWorker(context, freshControl, freshWorker)
                awaitState(
                    supervisor,
                    EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST,
                    10_000L,
                    "Supervisor did not observe Worker B cleanup death",
                )
            } finally {
                if (freshControlBound) {
                    runCatching { context.unbindService(freshControl.connection) }
                }
            }
            supervisor.disconnect()
        } finally {
            if (controlBound && controlConnection != null) {
                runCatching { context.unbindService(controlConnection!!.connection) }
            }
            runCatching { supervisor.disconnect() }
        }
    }

    private fun awaitConnected(
        supervisor: EmbeddedPythonWorkerSupervisor,
    ): EmbeddedPythonWorkerSupervisorSnapshot {
        return awaitState(
            supervisor,
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED,
            10_000L,
            "Supervisor did not connect to Worker",
        )
    }

    private fun awaitState(
        supervisor: EmbeddedPythonWorkerSupervisor,
        expectedState: Int,
        timeoutMs: Long,
        timeoutMessage: String,
    ): EmbeddedPythonWorkerSupervisorSnapshot {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var snapshot = supervisor.snapshot()
        while (
            snapshot.connectionState != expectedState &&
                SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(50L)
            snapshot = supervisor.snapshot()
        }
        assertEquals(timeoutMessage, expectedState, snapshot.connectionState)
        return snapshot
    }

    private fun isFreshWorker(snapshot: EmbeddedPythonWorkerExecutionSnapshotV1): Boolean =
        snapshot.workerLifecycleState == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE &&
            snapshot.bindingState == EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND &&
            snapshot.executionState == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED

    private fun assertFreshWorkerSnapshot(snapshot: EmbeddedPythonWorkerExecutionSnapshotV1) {
        assertTrue(snapshot.workerInstanceId.startsWith("worker-"))
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            snapshot.workerLifecycleState,
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            snapshot.bindingState,
        )
        assertEquals("", snapshot.processBindingId)
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED,
            snapshot.executionState,
        )
        assertEquals("", snapshot.executionSessionId)
        assertEquals(0L, snapshot.executionGeneration)
        assertEquals("", snapshot.projectIdentity)
        assertEquals("", snapshot.executionRoot)
        assertEquals("", snapshot.entrypoint)
        assertEquals("", snapshot.workingDirectory)
        assertEquals(false, snapshot.stopRequested)
        assertEquals(false, snapshot.hasExitCode)
    }

    private fun bindDirect(context: Context, connection: DirectWorkerConnection): Boolean {
        val bound = context.bindService(
            Intent(context, EmbeddedPythonWorkerService::class.java),
            connection.connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("direct Worker control binding was rejected", bound)
        return bound
    }

    private fun terminateDirectWorker(
        context: Context,
        connection: DirectWorkerConnection,
        worker: IEmbeddedPythonWorker,
    ) {
        val death = CountDownLatch(1)
        val binder = worker.asBinder()
        val recipient = IBinder.DeathRecipient { death.countDown() }
        var linked = false
        try {
            try {
                binder.linkToDeath(recipient, 0)
                linked = true
            } catch (_: RemoteException) {
                death.countDown()
            }
            val bindingId = worker.getBoundProcessBindingId()
            assertTrue("Worker must be bound before test termination", bindingId.isNotBlank())
            val result = worker.requestWorkerExitForRebind(
                worker.getWorkerInstanceId(),
                bindingId,
            )
            assertTrue(
                "unexpected Worker termination result $result",
                result == EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED ||
                    result == EmbeddedPythonWorkerProtocol.EXIT_ALREADY_REQUESTED,
            )
            context.unbindService(connection.connection)
            assertTrue(
                "Worker Binder did not die within the bounded timeout",
                death.await(10, TimeUnit.SECONDS),
            )
        } finally {
            if (linked) runCatching { binder.unlinkToDeath(recipient, 0) }
        }
    }

    private fun testBinding(projectIdentity: String): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = projectIdentity,
            projectSourceGeneration = ProjectSourceGeneration.of("alpha44-supervisor-source-v1"),
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
            dependencyLayerBinding = DependencyLayerBinding.STDLIB_ONLY,
        )

    private fun IEmbeddedPythonWorker.bind(binding: RuntimeLoadBindingV1): Int =
        bindRuntimeLoadV1(
            binding.projectIdentity,
            binding.projectSourceGeneration.value,
            binding.runtimeProvenanceDigest.value,
            binding.dependencyLayerBinding.wireValue,
            binding.processBindingId.value,
        )

    private class DirectWorkerConnection {
        private val connected = CountDownLatch(1)
        private var remote: IEmbeddedPythonWorker? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote = IEmbeddedPythonWorker.Stub.asInterface(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                connected.countDown()
            }
        }

        fun awaitWorker(): IEmbeddedPythonWorker {
            assertTrue(
                "direct Worker connection timed out",
                connected.await(10, TimeUnit.SECONDS),
            )
            return checkNotNull(remote) { "direct Worker Binder was not delivered" }
        }
    }
}
