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
class EmbeddedPythonWorkerProjectExecutionInstrumentedTest {
    @Test
    fun workerExecutesCallerStagedFiniteProjectWithTypedResults() {
        assumeTrue(
            "Worker project execution is packaged for arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectRoot = File(
            context.filesDir,
            "siftalphax/projects/worker-file-project-${UUID.randomUUID()}",
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

            // Force the Worker to exercise runtime reconstruction after the caller has staged
            // its project. The prepare repair must touch python/ only, never projects/.
            File(context.filesDir, "siftalphax/python").deleteRecursively()

            val binding = projectBinding()
            val workerInstanceId = activeWorker.getWorkerInstanceId()
            val workerPid = activeWorker.getWorkerPid()
            assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, activeWorker.bind(binding))

            val firstStart = activeWorker.startProjectExecutionV1(
                workerInstanceId,
                binding.processBindingId.value,
                projectRoot.absolutePath,
                "main.py",
                ".",
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
                firstStart,
            )
            awaitProjectTerminal(activeWorker)
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
                activeWorker.getProjectExecutionState(),
            )
            assertTrue(activeWorker.hasProjectExecutionExitCode())
            assertEquals(0, activeWorker.getProjectExecutionExitCode())
            assertEquals(binding.projectIdentity, activeWorker.getProjectExecutionProjectIdentity())
            assertEquals(projectRoot.absolutePath, activeWorker.getProjectExecutionRoot())
            assertEquals(File(projectRoot, "main.py").absolutePath, activeWorker.getProjectExecutionEntrypoint())
            assertEquals(File(projectRoot, ".").absolutePath, activeWorker.getProjectExecutionWorkingDirectory())
            val firstSessionId = activeWorker.getProjectExecutionSessionId()
            val firstGeneration = activeWorker.getProjectExecutionGeneration()
            assertTrue(firstSessionId.startsWith("siftalpha-worker-exec-"))
            assertTrue(firstGeneration > 0L)

            val successStdout = activeWorker.getProjectExecutionStdout()
            assertTrue(successStdout.contains("SIFTALPHA_ALPHA44_WORKER_PROJECT_SUCCESS"))
            assertTrue(successStdout.contains("WORKER_PROJECT_HELPER"))
            assertTrue(successStdout.contains("__main__"))
            assertTrue(successStdout.contains(File(projectRoot, "main.py").absolutePath))
            assertTrue(successStdout.contains("{\"mode\": \"success\"}"))
            assertTrue(activeWorker.getProjectExecutionStderr().isEmpty())
            assertEquals(workerInstanceId, activeWorker.getWorkerInstanceId())
            assertEquals(workerPid, activeWorker.getWorkerPid())
            assertEquals(
                EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                activeWorker.getWorkerLifecycleState(),
            )
            assertEquals(binding.processBindingId.value, activeWorker.getBoundProcessBindingId())

            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
                activeWorker.startProjectExecutionV1(
                    workerInstanceId,
                    binding.processBindingId.value,
                    projectRoot.absolutePath,
                    "fail.py",
                    ".",
                ),
            )
            awaitProjectTerminal(activeWorker)
            assertEquals(
                EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED,
                activeWorker.getProjectExecutionState(),
            )
            assertTrue(activeWorker.hasProjectExecutionExitCode())
            assertNotEquals(0, activeWorker.getProjectExecutionExitCode())
            assertNotEquals(firstSessionId, activeWorker.getProjectExecutionSessionId())
            assertTrue(activeWorker.getProjectExecutionGeneration() > firstGeneration)
            assertTrue(
                activeWorker.getProjectExecutionStdout().contains(
                    "SIFTALPHA_ALPHA44_WORKER_PROJECT_FAILURE_STDOUT",
                ),
            )
            val failureStderr = activeWorker.getProjectExecutionStderr()
            assertTrue(failureStderr.contains("SIFTALPHA_ALPHA44_WORKER_PROJECT_FAILURE_STDERR"))
            assertTrue(failureStderr.contains("SIFTALPHA_ALPHA44_WORKER_PROJECT_EXPECTED_FAILURE"))
            assertTrue(failureStderr.contains("fail.py"))
            assertTrue("native traceback must retain the file path", !failureStderr.contains("<string>"))
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
        File(root, "helper.py").writeText("VALUE = \"WORKER_PROJECT_HELPER\"\n")
        File(root, "main.py").writeText(
            """
            import json
            from helper import VALUE

            print("SIFTALPHA_ALPHA44_WORKER_PROJECT_SUCCESS")
            print(VALUE)
            print(__name__)
            print(__file__)
            print(json.dumps({"mode": "success"}, sort_keys=True))
            """.trimIndent() + "\n",
        )
        File(root, "fail.py").writeText(
            """
            import sys

            print("SIFTALPHA_ALPHA44_WORKER_PROJECT_FAILURE_STDOUT")
            print(
                "SIFTALPHA_ALPHA44_WORKER_PROJECT_FAILURE_STDERR",
                file=sys.stderr,
            )

            raise RuntimeError(
                "SIFTALPHA_ALPHA44_WORKER_PROJECT_EXPECTED_FAILURE"
            )
            """.trimIndent() + "\n",
        )
    }

    private fun requiresFreshWorker(worker: IEmbeddedPythonWorker): Boolean =
        worker.getBindingState() == EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND ||
            worker.getWorkerLifecycleState() == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING ||
            worker.getCpythonSmokeState() != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED ||
            worker.getProjectExecutionState() != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED

    private fun awaitProjectTerminal(worker: IEmbeddedPythonWorker) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        var state = worker.getProjectExecutionState()
        while (
            state != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED &&
            state != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED &&
            state != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED &&
            state != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(100L)
            state = worker.getProjectExecutionState()
        }
        assertTrue(
            "worker project execution timed out; last state=$state",
            state == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED ||
                state == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED ||
                state == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED ||
                state == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR,
        )
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
            projectIdentity = "alpha44-worker-file-project",
            projectSourceGeneration = ProjectSourceGeneration.of("alpha44-worker-file-project-v1"),
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
            dependencyLayerBinding = DependencyLayerBinding.STDLIB_ONLY,
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
}
