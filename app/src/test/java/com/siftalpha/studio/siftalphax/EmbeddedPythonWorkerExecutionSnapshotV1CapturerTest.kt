package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWorkerExecutionSnapshotV1CapturerTest {
    @Test
    fun unboundToBoundTransitionDiscardsFirstTornExecutionCapture() {
        val unbound = processSnapshot(
            lifecycle = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            bindingId = "",
        )
        val bound = processSnapshot(
            lifecycle = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
            bindingId = "sha256:${"b".repeat(64)}",
        )
        val execution = executionSnapshot(
            state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
            sessionId = "siftalpha-worker-exec-capture-a",
            generation = 1L,
        )
        var processReads = 0
        var executionReads = 0

        val captured = EmbeddedPythonWorkerExecutionSnapshotV1Capturer.capture(
            processSnapshot = {
                processReads += 1
                when (processReads) {
                    1 -> unbound
                    2, 3, 4 -> bound
                    else -> error("unexpected process read $processReads")
                }
            },
            executionSnapshot = {
                executionReads += 1
                execution
            },
            workerPid = 5001,
        )

        assertEquals(4, processReads)
        assertEquals(2, executionReads)
        assertEquals(EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND, captured.bindingState)
        assertEquals(bound.processBindingId, captured.processBindingId)
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING, captured.executionState)
        assertEquals(execution.sessionId, captured.executionSessionId)
        assertTrue(captured.executionGeneration > 0L)
    }

    @Test
    fun activeToTerminatingTransitionRetriesAndReturnsStableTerminatingSnapshot() {
        val active = processSnapshot(
            lifecycle = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
            bindingId = "sha256:${"c".repeat(64)}",
        )
        val terminating = active.copy(
            workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
        )
        val execution = executionSnapshot(
            state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
            sessionId = "siftalpha-worker-exec-capture-b",
            generation = 2L,
        )
        var processReads = 0
        var executionReads = 0

        val captured = EmbeddedPythonWorkerExecutionSnapshotV1Capturer.capture(
            processSnapshot = {
                processReads += 1
                when (processReads) {
                    1 -> active
                    2, 3, 4 -> terminating
                    else -> error("unexpected process read $processReads")
                }
            },
            executionSnapshot = {
                executionReads += 1
                execution
            },
            workerPid = 5002,
        )

        assertEquals(4, processReads)
        assertEquals(2, executionReads)
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
            captured.workerLifecycleState,
        )
        assertEquals(EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND, captured.bindingState)
        assertEquals(execution.sessionId, captured.executionSessionId)
        assertEquals(execution.generation, captured.executionGeneration)
    }

    private fun processSnapshot(
        lifecycle: Int,
        bindingState: Int,
        bindingId: String,
    ): EmbeddedPythonWorkerProcessSnapshot = EmbeddedPythonWorkerProcessSnapshot(
        workerInstanceId = "worker-capture-test",
        workerLifecycleState = lifecycle,
        bindingState = bindingState,
        processBindingId = bindingId,
    )

    private fun executionSnapshot(
        state: Int,
        sessionId: String,
        generation: Long,
    ): EmbeddedPythonWorkerProjectExecutionSnapshot =
        EmbeddedPythonWorkerProjectExecutionSnapshot(
            state = state,
            sessionId = sessionId,
            generation = generation,
            projectIdentity = "capture-project",
            executionRoot = "/data/user/0/com.siftalpha.studio/files/project",
            entrypoint = "/data/user/0/com.siftalpha.studio/files/project/main.py",
            workingDirectory = "/data/user/0/com.siftalpha.studio/files/project",
            stopRequested = false,
            hasExitCode = false,
            exitCode = -1,
            stdout = "",
            stderr = "",
        )
}
