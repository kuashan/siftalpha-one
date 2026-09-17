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
    fun workerHandshakeUsesDedicatedProcessAndProcessScopedBindingGate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        var remote: IEmbeddedPythonWorker? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote = IEmbeddedPythonWorker.Stub.asInterface(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                connected.countDown()
            }
        }

        val bound = context.bindService(
            Intent(context, EmbeddedPythonWorkerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("worker service did not accept binding", bound)

        try {
            assertTrue("worker service connection timed out", connected.await(10, TimeUnit.SECONDS))
            val worker = checkNotNull(remote) { "worker Binder was not delivered" }
            val callerPid = Process.myPid()
            assertNotEquals("worker must run outside the caller process", callerPid, worker.getWorkerPid())
            assertTrue(worker.getWorkerInstanceId().startsWith("worker-"))
            assertEquals(
                EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                worker.getBindingState(),
            )

            val first = binding(projectIdentity = "instrumented-project-a")
            val second = binding(projectIdentity = "instrumented-project-b")

            assertEquals(
                EmbeddedPythonWorkerProtocol.BOUND_NEW,
                worker.bind(first),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.BOUND_SAME,
                worker.bind(first),
            )
            assertEquals(
                EmbeddedPythonWorkerProtocol.REJECTED_BINDING_CONFLICT,
                worker.bind(second),
            )
            assertEquals(first.processBindingId.value, worker.getBoundProcessBindingId())
            assertEquals(
                EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
                worker.getBindingState(),
            )
        } finally {
            context.unbindService(connection)
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

}
