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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedPythonWorkerLongRunningStopInstrumentedTest {
    @Test
    fun workerStopsLongRunningProjectAndAllowsReentry() {
        assumeTrue(
            "Worker project execution is packaged for arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectRoot = File(
            context.filesDir,
            "siftalphax/projects/worker-long-stop-${UUID.randomUUID()}",
        )
        stageProject(projectRoot)
        var connection = WorkerConnection()
        var bound = false
        var worker: IEmbeddedPythonWorker? = null

        try {
            bound = bind(context, connection)
            worker = connection.awaitWorker()
            if (requiresFreshWorker(worker)) {
                terminateAndAwaitDeath(context, connection, worker)
                bound = false
                connection = WorkerConnection()
                bound = bind(context, connection)
                worker = connection.awaitWorker()
            }

            val activeWorker = checkNotNull(worker)
            assertTrue("worker must run outside the caller process", activeWorker.getWorkerPid() != Process.myPid())
            assertEquals(
                EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                activeWorker.getBindingState(),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                activeWorker.getWorkerLifecycleState(),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED,
                activeWorker.getCpythonSmokeState(),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED,
                activeWorker.getProjectExecutionState(),
            )

            val binding = projectBinding()
            val workerInstanceId = activeWorker.getWorkerInstanceId()
            val workerPid = activeWorker.getWorkerPid()
            assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, activeWorker.bind(binding))

            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
                activeWorker.startProjectExecutionV1(
                    workerInstanceId,
                    binding.processBindingId.value,
                    projectRoot.absolutePath,
                    "long.py",
                    ".",
                ),
            )
            awaitProjectState(
                activeWorker,
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
                10_000L,
                "long project did not reach RUNNING",
            )
            awaitFile(File(projectRoot, "long-started.flag"), 10_000L)

            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_BUSY,
                activeWorker.startProjectExecutionV1(
                    workerInstanceId,
                    binding.processBindingId.value,
                    projectRoot.absolutePath,
                    "after.py",
                    ".",
                ),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_RUNTIME_BUSY,
                activeWorker.startCpythonSmokeV1(workerInstanceId, binding.processBindingId.value),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED,
                activeWorker.getCpythonSmokeState(),
            )

            val sessionA = activeWorker.getProjectExecutionSessionId()
            val generationA = activeWorker.getProjectExecutionGeneration()
            assertTrue(sessionA.isNotBlank())
            assertTrue(generationA > 0L)

            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_ACCEPTED,
                activeWorker.requestProjectExecutionStopV1(
                    workerInstanceId,
                    binding.processBindingId.value,
                    sessionA,
                    generationA,
                ),
            )
            awaitProjectState(
                activeWorker,
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
                10_000L,
                "long project did not reach STOPPED",
            )
            assertTrue(activeWorker.hasProjectExecutionExitCode())
            assertEquals(130, activeWorker.getProjectExecutionExitCode())
            assertEquals(sessionA, activeWorker.getProjectExecutionSessionId())
            assertEquals(generationA, activeWorker.getProjectExecutionGeneration())
            val stoppedStdout = activeWorker.getProjectExecutionStdout()
            assertTrue(stoppedStdout.contains("SIFTALPHA_ALPHA44_WORKER_LONG_STARTED"))
            assertTrue(stoppedStdout.contains("SIFTALPHA_ALPHA44_WORKER_LONG_COOPERATIVE_STOP"))
            assertTrue(
                activeWorker.getProjectExecutionStderr().contains("SIFTALPHA_X_STOP=COOPERATIVE"),
            )
            assertEquals(workerInstanceId, activeWorker.getWorkerInstanceId())
            assertEquals(workerPid, activeWorker.getWorkerPid())
            assertEquals(binding.processBindingId.value, activeWorker.getBoundProcessBindingId())
            assertEquals(
                EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                activeWorker.getWorkerLifecycleState(),
            )

            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
                activeWorker.startProjectExecutionV1(
                    workerInstanceId,
                    binding.processBindingId.value,
                    projectRoot.absolutePath,
                    "after.py",
                    ".",
                ),
            )
            val sessionB = activeWorker.getProjectExecutionSessionId()
            val generationB = activeWorker.getProjectExecutionGeneration()
            assertNotEquals(sessionA, sessionB)
            assertTrue(generationB > generationA)

            // The identity mismatch is checked before state/stopRequested, so this remains a
            // deterministic stale-stop fence even if after.py reaches terminal immediately.
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH,
                activeWorker.requestProjectExecutionStopV1(
                    workerInstanceId,
                    binding.processBindingId.value,
                    sessionA,
                    generationA,
                ),
            )

            awaitProjectState(
                activeWorker,
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
                10_000L,
                "re-entry project did not succeed",
            )
            assertTrue(activeWorker.hasProjectExecutionExitCode())
            assertEquals(0, activeWorker.getProjectExecutionExitCode())
            assertEquals(sessionB, activeWorker.getProjectExecutionSessionId())
            assertEquals(generationB, activeWorker.getProjectExecutionGeneration())
            assertTrue(
                activeWorker.getProjectExecutionStdout().contains(
                    "SIFTALPHA_ALPHA44_WORKER_REENTRY_SUCCESS",
                ),
            )
            assertEquals(workerInstanceId, activeWorker.getWorkerInstanceId())
            assertEquals(workerPid, activeWorker.getWorkerPid())
            assertEquals(binding.processBindingId.value, activeWorker.getBoundProcessBindingId())
        } finally {
            val currentWorker = worker
            if (bound && currentWorker != null) {
                runCatching { terminateAndAwaitDeath(context, connection, currentWorker) }
            }
            runCatching { projectRoot.deleteRecursively() }
        }
    }

    private fun stageProject(root: File) {
        assertTrue("unable to create project root", root.mkdirs())
        File(root, "long.py").writeText(
            """
            import pathlib
            import sys
            import time

            started = pathlib.Path(__file__).with_name("long-started.flag")
            started.write_text("started", encoding="utf-8")

            print("SIFTALPHA_ALPHA44_WORKER_LONG_STARTED")
            sys.stdout.flush()

            try:
                while True:
                    time.sleep(0.1)
            except KeyboardInterrupt:
                print("SIFTALPHA_ALPHA44_WORKER_LONG_COOPERATIVE_STOP")
                sys.stdout.flush()
            """.trimIndent() + "\n",
        )
        File(root, "after.py").writeText(
            "print(\"SIFTALPHA_ALPHA44_WORKER_REENTRY_SUCCESS\")\n",
        )
    }

    private fun requiresFreshWorker(worker: IEmbeddedPythonWorker): Boolean =
        worker.getBindingState() == EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND ||
            worker.getWorkerLifecycleState() == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING ||
            worker.getCpythonSmokeState() != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED ||
            worker.getProjectExecutionState() != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED

    private fun awaitProjectState(
        worker: IEmbeddedPythonWorker,
        expectedState: Int,
        timeoutMs: Long,
        timeoutMessage: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var state = worker.getProjectExecutionState()
        while (
            state != expectedState &&
            state !in TERMINAL_PROJECT_STATES &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(100L)
            state = worker.getProjectExecutionState()
        }
        assertEquals(timeoutMessage, expectedState, state)
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
            projectIdentity = "alpha44-worker-long-stop",
            projectSourceGeneration = ProjectSourceGeneration.of("alpha44-worker-long-stop-v1"),
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
