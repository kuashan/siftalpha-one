package com.siftalpha.studio.siftalphax

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
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
class EmbeddedPythonWorkerServiceInstrumentedTest {
    @Test
    fun workerTerminationProvesDeathAndFreshProcessRebinding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldConnection = WorkerConnection()
        var oldBound = false

        try {
            oldBound = context.bindService(
                Intent(context, EmbeddedPythonWorkerService::class.java),
                oldConnection.connection,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("worker service did not accept the first binding", oldBound)

            val workerA = oldConnection.awaitWorker()
            val callerPid = Process.myPid()
            val workerPidA = workerA.getWorkerPid()
            assertNotEquals("worker must run outside the caller process", callerPid, workerPidA)
            val workerInstanceIdA = workerA.getWorkerInstanceId()
            assertTrue(workerInstanceIdA.startsWith("worker-"))
            assertEquals(
                EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                workerA.getBindingState(),
            )

            val bindingA = binding(projectIdentity = "instrumented-project-a")
            val bindingB = binding(projectIdentity = "instrumented-project-b")
            assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, workerA.bind(bindingA))
            assertEquals(
                EmbeddedPythonWorkerProtocol.REJECTED_BINDING_CONFLICT,
                workerA.bind(bindingB),
            )
            assertEquals(bindingA.processBindingId.value, workerA.getBoundProcessBindingId())

            val death = CountDownLatch(1)
            val oldBinder = workerA.asBinder()
            val deathRecipient = IBinder.DeathRecipient { death.countDown() }
            oldBinder.linkToDeath(deathRecipient, 0)

            try {
                assertEquals(
                    EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED,
                    workerA.requestExitForRebind(workerInstanceIdA, bindingA.processBindingId.value),
                )
                assertEquals(
                    EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
                    workerA.getWorkerLifecycleState(),
                )

                context.unbindService(oldConnection.connection)
                oldBound = false
                assertTrue(
                    "worker Binder did not die within the bounded timeout",
                    death.await(10, TimeUnit.SECONDS),
                )
            } finally {
                oldBinder.unlinkToDeath(deathRecipient, 0)
            }

            val freshConnection = WorkerConnection()
            var freshBound = false
            try {
                freshBound = context.bindService(
                    Intent(context, EmbeddedPythonWorkerService::class.java),
                    freshConnection.connection,
                    Context.BIND_AUTO_CREATE,
                )
                assertTrue("worker service did not accept the fresh binding", freshBound)

                val workerB = freshConnection.awaitWorker()
                val workerInstanceIdB = workerB.getWorkerInstanceId()
                assertNotEquals(workerInstanceIdA, workerInstanceIdB)
                assertNotEquals("fresh process must not reuse the caller process", callerPid, workerB.getWorkerPid())
                // PID equality with worker A is intentionally not asserted: the OS may reuse a PID.
                assertEquals(
                    EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                    workerB.getBindingState(),
                )
                assertEquals(
                    EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                    workerB.getWorkerLifecycleState(),
                )
                assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, workerB.bind(bindingB))
                assertEquals(bindingB.processBindingId.value, workerB.getBoundProcessBindingId())
            } finally {
                if (freshBound) {
                    context.unbindService(freshConnection.connection)
                }
            }
        } finally {
            if (oldBound) {
                context.unbindService(oldConnection.connection)
            }
        }
    }

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

    private fun binding(projectIdentity: String): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = projectIdentity,
            projectSourceGeneration = ProjectSourceGeneration.of("instrumented-source-0001"),
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

    private fun IEmbeddedPythonWorker.requestExitForRebind(
        workerInstanceId: String,
        processBindingId: String,
    ): Int = requestWorkerExitForRebind(workerInstanceId, processBindingId)
}
