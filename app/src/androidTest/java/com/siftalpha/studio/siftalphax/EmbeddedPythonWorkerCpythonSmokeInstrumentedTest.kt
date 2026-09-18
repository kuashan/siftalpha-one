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
class EmbeddedPythonWorkerCpythonSmokeInstrumentedTest {
    @Test
    fun workerCpythonSmokeExecutesInsideDedicatedProcess() {
        assumeTrue(
            "Worker CPython smoke is packaged for arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var connection = WorkerConnection()
        var bound = bind(context, connection)
        var worker = connection.awaitWorker()

        try {
            if (requiresFreshWorker(worker)) {
                terminateAndAwaitDeath(context, connection, worker)
                bound = false
                connection = WorkerConnection()
                bound = bind(context, connection)
                worker = connection.awaitWorker()
            }

            assertNotEquals("worker must run outside the caller process", Process.myPid(), worker.getWorkerPid())
            assertEquals(
                EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                worker.getBindingState(),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                worker.getWorkerLifecycleState(),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED,
                worker.getCpythonSmokeState(),
            )

            val workerInstanceId = worker.getWorkerInstanceId()
            val binding = smokeBinding()
            assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, worker.bind(binding))
            assertEquals(
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ACCEPTED,
                worker.startCpythonSmokeV1(workerInstanceId, binding.processBindingId.value),
            )
            assertEquals(workerInstanceId, worker.getWorkerInstanceId())
            awaitSmoke(worker)

            assertEquals(
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_SUCCEEDED,
                worker.getCpythonSmokeState(),
            )
            assertTrue(worker.hasCpythonSmokeExitCode())
            assertEquals(0, worker.getCpythonSmokeExitCode())
            assertEquals(worker.getWorkerPid(), worker.getCpythonSmokePythonPid())
            assertEquals(workerInstanceId, worker.getWorkerInstanceId())
            assertTrue(worker.getCpythonSmokeSessionId().startsWith("siftalpha-worker-smoke-"))

            val stdout = worker.getCpythonSmokeStdout()
            assertTrue(stdout.contains(EmbeddedPythonWorkerCpythonSmokeEvidence.PASS_MARKER))
            assertTrue(
                stdout.contains(
                    EmbeddedPythonWorkerCpythonSmokeEvidence.STDLIB_JSON_MARKER +
                        EmbeddedPythonWorkerCpythonSmokeEvidence.EXPECTED_STDLIB_JSON,
                ),
            )
            assertTrue("stdlib-only fixture should not write stderr", worker.getCpythonSmokeStderr().isEmpty())
            assertEquals(
                EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                worker.getWorkerLifecycleState(),
            )
            assertEquals(binding.processBindingId.value, worker.getBoundProcessBindingId())
        } finally {
            if (bound) {
                try {
                    terminateAndAwaitDeath(context, connection, worker)
                } catch (_: Throwable) {
                    // Cleanup must not replace the first smoke assertion or execution failure.
                }
            }
        }
    }

    private fun requiresFreshWorker(worker: IEmbeddedPythonWorker): Boolean =
        worker.getBindingState() == EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND ||
            worker.getWorkerLifecycleState() == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING ||
            worker.getCpythonSmokeState() != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED

    private fun awaitSmoke(worker: IEmbeddedPythonWorker) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        var state = worker.getCpythonSmokeState()
        while (
            state != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_SUCCEEDED &&
            state != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_FAILED &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(100L)
            state = worker.getCpythonSmokeState()
        }
        assertTrue(
            "worker smoke timed out; last state=$state",
            state == EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_SUCCEEDED ||
                state == EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_FAILED,
        )
        assertEquals(
            "worker smoke failed: ${worker.getCpythonSmokeStderr()}",
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_SUCCEEDED,
            state,
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
            if (bindingId.isNotBlank()) {
                val result = worker.requestWorkerExitForRebind(
                    worker.getWorkerInstanceId(),
                    bindingId,
                )
                assertTrue(
                    "unexpected worker termination result $result",
                    result == EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED ||
                        result == EmbeddedPythonWorkerProtocol.EXIT_ALREADY_REQUESTED,
                )
            }
            context.unbindService(connection.connection)
            assertTrue(
                "worker Binder did not die within the bounded timeout",
                death.await(10, TimeUnit.SECONDS),
            )
        } finally {
            if (linked) {
                runCatching { binder.unlinkToDeath(recipient, 0) }
            }
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

    private fun smokeBinding(): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = "alpha44-worker-cpython-smoke",
            projectSourceGeneration = ProjectSourceGeneration.of("alpha44-worker-cpython-smoke-v1"),
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

    private fun IEmbeddedPythonWorker.bind(binding: RuntimeLoadBindingV1): Int =
        bindRuntimeLoadV1(
            binding.projectIdentity,
            binding.projectSourceGeneration.value,
            binding.runtimeProvenanceDigest.value,
            binding.dependencyLayerBinding.wireValue,
            binding.processBindingId.value,
        )
}
