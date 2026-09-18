package com.siftalpha.studio.siftalphax

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedPythonWorkerExecutionSnapshotInstrumentedTest {
    @Test
    fun workerSnapshotSurvivesBinderReconnectAndFencesProcessLifetime() {
        assumeTrue(
            "Worker project execution is packaged for arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectRoot = File(
            context.filesDir,
            "siftalphax/projects/worker-snapshot-${UUID.randomUUID()}",
        )
        stageProject(projectRoot)

        var primaryConnection = WorkerConnection()
        var primaryBound = false
        var primaryWorker: IEmbeddedPythonWorker? = null
        var secondConnection: WorkerConnection? = null
        var secondBound = false
        var secondWorker: IEmbeddedPythonWorker? = null
        var thirdConnection: WorkerConnection? = null
        var thirdBound = false
        var thirdWorker: IEmbeddedPythonWorker? = null
        var freshConnection: WorkerConnection? = null
        var freshBound = false
        var freshWorker: IEmbeddedPythonWorker? = null

        try {
            primaryBound = bind(context, primaryConnection)
            primaryWorker = primaryConnection.awaitWorker()
            if (requiresFreshWorker(primaryWorker!!)) {
                terminateAndAwaitDeath(context, primaryConnection, primaryWorker!!)
                primaryBound = false
                primaryConnection = WorkerConnection()
                primaryBound = bind(context, primaryConnection)
                primaryWorker = primaryConnection.awaitWorker()
            }

            val worker = checkNotNull(primaryWorker)
            val initial = worker.getWorkerExecutionSnapshotV1()
            assertFreshUnboundSnapshot(initial)
            assertTrue("worker must run outside the caller process", initial.workerPid != Process.myPid())

            val binding = projectBinding()
            assertEquals(
                EmbeddedPythonWorkerProtocol.BOUND_NEW,
                worker.bind(binding),
            )
            val workerInstanceId = initial.workerInstanceId
            val workerPid = initial.workerPid
            val processBindingId = binding.processBindingId.value

            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
                worker.startProjectExecutionV1(
                    workerInstanceId,
                    processBindingId,
                    projectRoot.absolutePath,
                    "long.py",
                    ".",
                ),
            )
            awaitExecutionState(
                worker,
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
                10_000L,
                "snapshot project did not reach RUNNING",
            )
            awaitFile(File(projectRoot, "snapshot-started.flag"), 10_000L)

            val runningSnapshotA = worker.getWorkerExecutionSnapshotV1()
            assertRunningSnapshot(
                runningSnapshotA,
                workerInstanceId = workerInstanceId,
                workerPid = workerPid,
                processBindingId = processBindingId,
                projectRoot = projectRoot,
            )

            secondConnection = WorkerConnection()
            secondBound = bind(context, secondConnection!!)
            secondWorker = secondConnection!!.awaitWorker()
            val runningSnapshotB = secondWorker!!.getWorkerExecutionSnapshotV1()
            assertSnapshotEquals(runningSnapshotA, runningSnapshotB)
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
                runningSnapshotB.executionState,
            )
            assertFalse(runningSnapshotB.stopRequested)

            // The second Binder client has recovered the complete execution identity. The
            // primary connection is no longer needed for the STOP request.
            context.unbindService(primaryConnection.connection)
            primaryBound = false
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_ACCEPTED,
                secondWorker!!.requestProjectExecutionStopV1(
                    runningSnapshotB.workerInstanceId,
                    runningSnapshotB.processBindingId,
                    runningSnapshotB.executionSessionId,
                    runningSnapshotB.executionGeneration,
                ),
            )

            val terminalSnapshotA = awaitExecutionSnapshot(
                secondWorker!!,
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
                10_000L,
                "snapshot project did not reach STOPPED",
            )
            assertEquals(workerInstanceId, terminalSnapshotA.workerInstanceId)
            assertEquals(workerPid, terminalSnapshotA.workerPid)
            assertEquals(
                EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                terminalSnapshotA.workerLifecycleState,
            )
            assertEquals(EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND, terminalSnapshotA.bindingState)
            assertEquals(processBindingId, terminalSnapshotA.processBindingId)
            assertEquals(runningSnapshotB.executionSessionId, terminalSnapshotA.executionSessionId)
            assertEquals(runningSnapshotB.executionGeneration, terminalSnapshotA.executionGeneration)
            assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED, terminalSnapshotA.executionState)
            assertEquals(binding.projectIdentity, terminalSnapshotA.projectIdentity)
            assertEquals(projectRoot.absolutePath, terminalSnapshotA.executionRoot)
            assertTrue(terminalSnapshotA.entrypoint.endsWith("/long.py"))
            assertEquals(
                projectRoot.canonicalPath,
                File(terminalSnapshotA.workingDirectory).canonicalPath,
            )
            assertTrue(terminalSnapshotA.stopRequested)
            assertTrue(terminalSnapshotA.hasExitCode)
            assertEquals(130, terminalSnapshotA.exitCode)
            assertTrue(terminalSnapshotA.stdout.contains("SIFTALPHA_ALPHA44_WORKER_SNAPSHOT_STARTED"))
            assertTrue(terminalSnapshotA.stdout.contains("SIFTALPHA_ALPHA44_WORKER_SNAPSHOT_STOPPED"))
            assertTrue(terminalSnapshotA.stderr.contains("SIFTALPHA_X_STOP=COOPERATIVE"))

            thirdConnection = WorkerConnection()
            thirdBound = bind(context, thirdConnection!!)
            thirdWorker = thirdConnection!!.awaitWorker()
            val terminalSnapshotB = thirdWorker!!.getWorkerExecutionSnapshotV1()
            assertSnapshotEquals(terminalSnapshotA, terminalSnapshotB)
            context.unbindService(secondConnection!!.connection)
            secondBound = false
            val terminalSnapshotC = thirdWorker!!.getWorkerExecutionSnapshotV1()
            assertSnapshotEquals(terminalSnapshotA, terminalSnapshotC)

            // A returned Parcelable is a value copy, not a mutable view of process state.
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
                runningSnapshotB.executionState,
            )
            assertFalse(runningSnapshotB.stopRequested)

            terminateAndAwaitDeath(context, thirdConnection!!, thirdWorker!!)
            thirdBound = false

            freshConnection = WorkerConnection()
            freshBound = bind(context, freshConnection!!)
            assertTrue(freshBound)
            freshWorker = freshConnection!!.awaitWorker()
            val freshSnapshot = freshWorker!!.getWorkerExecutionSnapshotV1()
            assertNotEquals(terminalSnapshotA.workerInstanceId, freshSnapshot.workerInstanceId)
            assertFreshUnboundSnapshot(freshSnapshot)

            // There is no reset API. Bind only so the existing process-exit primitive can cleanly
            // terminate this test-created fresh worker as well.
            assertEquals(
                EmbeddedPythonWorkerProtocol.BOUND_NEW,
                freshWorker!!.bind(binding),
            )
            terminateAndAwaitDeath(context, freshConnection!!, freshWorker!!)
            freshBound = false
        } finally {
            if (freshBound && freshConnection != null && freshWorker != null) {
                runCatching { terminateAndAwaitDeath(context, freshConnection!!, freshWorker!!) }
            }
            if (thirdBound && thirdConnection != null && thirdWorker != null) {
                runCatching { terminateAndAwaitDeath(context, thirdConnection!!, thirdWorker!!) }
            }
            if (secondBound && secondConnection != null) {
                runCatching { context.unbindService(secondConnection!!.connection) }
            }
            if (primaryBound && primaryWorker != null) {
                runCatching { terminateAndAwaitDeath(context, primaryConnection, primaryWorker!!) }
            }
            runCatching { projectRoot.deleteRecursively() }
        }
    }

    private fun assertFreshUnboundSnapshot(snapshot: EmbeddedPythonWorkerExecutionSnapshotV1) {
        assertTrue(snapshot.workerInstanceId.isNotBlank())
        assertEquals(EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE, snapshot.workerLifecycleState)
        assertEquals(EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND, snapshot.bindingState)
        assertEquals("", snapshot.processBindingId)
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED, snapshot.executionState)
        assertEquals("", snapshot.executionSessionId)
        assertEquals(0L, snapshot.executionGeneration)
        assertEquals("", snapshot.projectIdentity)
        assertEquals("", snapshot.executionRoot)
        assertEquals("", snapshot.entrypoint)
        assertEquals("", snapshot.workingDirectory)
        assertFalse(snapshot.stopRequested)
        assertFalse(snapshot.hasExitCode)
        assertEquals("", snapshot.stdout)
        assertEquals("", snapshot.stderr)
    }

    private fun assertRunningSnapshot(
        snapshot: EmbeddedPythonWorkerExecutionSnapshotV1,
        workerInstanceId: String,
        workerPid: Int,
        processBindingId: String,
        projectRoot: File,
    ) {
        assertEquals(workerInstanceId, snapshot.workerInstanceId)
        assertEquals(workerPid, snapshot.workerPid)
        assertEquals(EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE, snapshot.workerLifecycleState)
        assertEquals(EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND, snapshot.bindingState)
        assertEquals(processBindingId, snapshot.processBindingId)
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING, snapshot.executionState)
        assertTrue(snapshot.executionSessionId.isNotBlank())
        assertTrue(snapshot.executionGeneration > 0L)
        assertEquals("alpha44-worker-snapshot", snapshot.projectIdentity)
        assertEquals(projectRoot.absolutePath, snapshot.executionRoot)
        assertTrue(snapshot.entrypoint.endsWith("/long.py"))
        assertEquals(
            projectRoot.canonicalPath,
            File(snapshot.workingDirectory).canonicalPath,
        )
        assertFalse(snapshot.stopRequested)
        assertFalse(snapshot.hasExitCode)
    }

    private fun assertSnapshotEquals(
        expected: EmbeddedPythonWorkerExecutionSnapshotV1,
        actual: EmbeddedPythonWorkerExecutionSnapshotV1,
    ) {
        assertEquals(expected.workerInstanceId, actual.workerInstanceId)
        assertEquals(expected.workerPid, actual.workerPid)
        assertEquals(expected.workerLifecycleState, actual.workerLifecycleState)
        assertEquals(expected.bindingState, actual.bindingState)
        assertEquals(expected.processBindingId, actual.processBindingId)
        assertEquals(expected.executionState, actual.executionState)
        assertEquals(expected.executionSessionId, actual.executionSessionId)
        assertEquals(expected.executionGeneration, actual.executionGeneration)
        assertEquals(expected.projectIdentity, actual.projectIdentity)
        assertEquals(expected.executionRoot, actual.executionRoot)
        assertEquals(expected.entrypoint, actual.entrypoint)
        assertEquals(expected.workingDirectory, actual.workingDirectory)
        assertEquals(expected.stopRequested, actual.stopRequested)
        assertEquals(expected.hasExitCode, actual.hasExitCode)
        assertEquals(expected.exitCode, actual.exitCode)
        assertEquals(expected.stdout, actual.stdout)
        assertEquals(expected.stderr, actual.stderr)
    }

    private fun stageProject(root: File) {
        assertTrue("unable to create project root", root.mkdirs())
        File(root, "long.py").writeText(
            """
            import pathlib
            import sys
            import time

            flag = pathlib.Path(__file__).with_name("snapshot-started.flag")
            flag.write_text("started", encoding="utf-8")

            print("SIFTALPHA_ALPHA44_WORKER_SNAPSHOT_STARTED")
            sys.stdout.flush()

            try:
                while True:
                    time.sleep(0.1)
            except KeyboardInterrupt:
                print("SIFTALPHA_ALPHA44_WORKER_SNAPSHOT_STOPPED")
                sys.stdout.flush()
            """.trimIndent() + "\n",
        )
    }

    private fun requiresFreshWorker(worker: IEmbeddedPythonWorker): Boolean {
        val snapshot = worker.getWorkerExecutionSnapshotV1()
        return snapshot.bindingState == EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND ||
            snapshot.workerLifecycleState == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING ||
            worker.getCpythonSmokeState() != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED ||
            snapshot.executionState != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED
    }

    private fun awaitExecutionState(
        worker: IEmbeddedPythonWorker,
        expectedState: Int,
        timeoutMs: Long,
        timeoutMessage: String,
    ) {
        awaitExecutionSnapshot(worker, expectedState, timeoutMs, timeoutMessage)
    }

    private fun awaitExecutionSnapshot(
        worker: IEmbeddedPythonWorker,
        expectedState: Int,
        timeoutMs: Long,
        timeoutMessage: String,
    ): EmbeddedPythonWorkerExecutionSnapshotV1 {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var snapshot = worker.getWorkerExecutionSnapshotV1()
        while (
            snapshot.executionState != expectedState &&
            snapshot.executionState !in TERMINAL_PROJECT_STATES &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(100L)
            snapshot = worker.getWorkerExecutionSnapshotV1()
        }
        assertEquals(timeoutMessage, expectedState, snapshot.executionState)
        return snapshot
    }

    private fun awaitFile(file: File, timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!file.isFile && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
        }
        assertTrue("test synchronization file did not appear: ${file.absolutePath}", file.isFile)
    }

    private fun terminateAndAwaitDeath(
        context: Context,
        connection: WorkerConnection,
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
            assertTrue("worker must be bound before fenced termination", bindingId.isNotBlank())
            val result = worker.requestWorkerExitForRebind(
                worker.getWorkerInstanceId(),
                bindingId,
            )
            assertTrue(
                "unexpected worker termination result $result",
                result == EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED ||
                    result == EmbeddedPythonWorkerProtocol.EXIT_ALREADY_REQUESTED,
            )
            context.unbindService(connection.connection)
            assertTrue(
                "worker Binder did not die within the bounded timeout",
                death.await(10, TimeUnit.SECONDS),
            )
        } finally {
            if (linked) runCatching { binder.unlinkToDeath(recipient, 0) }
        }
    }

    private fun bind(context: Context, connection: WorkerConnection): Boolean {
        val accepted = context.bindService(
            Intent(context, EmbeddedPythonWorkerService::class.java),
            connection.connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("worker service did not accept binding", accepted)
        return accepted
    }

    private fun projectBinding(): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = "alpha44-worker-snapshot",
            projectSourceGeneration = ProjectSourceGeneration.of("alpha44-worker-snapshot-v1"),
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

    private class WorkerConnection {
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
                "worker service connection timed out",
                connected.await(10, TimeUnit.SECONDS),
            )
            return checkNotNull(remote) { "worker Binder was not delivered" }
        }
    }

    private companion object {
        private val TERMINAL_PROJECT_STATES = setOf(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR,
        )
    }
}
