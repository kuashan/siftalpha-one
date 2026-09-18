package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedPythonWorkerExecutionSnapshotV1Test {
    @Test
    fun mapperCopiesEveryProcessAndExecutionField() {
        val process = EmbeddedPythonWorkerProcessSnapshot(
            workerInstanceId = "worker-snapshot-test",
            workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
            bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
            processBindingId = "sha256:${"a".repeat(64)}",
        )
        val execution = EmbeddedPythonWorkerProjectExecutionSnapshot(
            state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
            sessionId = "siftalpha-worker-exec-snapshot-test",
            generation = 7L,
            projectIdentity = "snapshot-project",
            executionRoot = "/data/user/0/com.siftalpha.studio/files/project",
            entrypoint = "/data/user/0/com.siftalpha.studio/files/project/main.py",
            workingDirectory = "/data/user/0/com.siftalpha.studio/files/project",
            stopRequested = true,
            hasExitCode = true,
            exitCode = 130,
            stdout = "stdout",
            stderr = "stderr",
        )

        val snapshot = EmbeddedPythonWorkerExecutionSnapshotV1Mapper.map(
            process = process,
            execution = execution,
            workerPid = 4321,
        )

        assertEquals("worker-snapshot-test", snapshot.workerInstanceId)
        assertEquals(4321, snapshot.workerPid)
        assertEquals(process.workerLifecycleState, snapshot.workerLifecycleState)
        assertEquals(process.bindingState, snapshot.bindingState)
        assertEquals(process.processBindingId, snapshot.processBindingId)
        assertEquals(execution.state, snapshot.executionState)
        assertEquals(execution.sessionId, snapshot.executionSessionId)
        assertEquals(execution.generation, snapshot.executionGeneration)
        assertEquals(execution.projectIdentity, snapshot.projectIdentity)
        assertEquals(execution.executionRoot, snapshot.executionRoot)
        assertEquals(execution.entrypoint, snapshot.entrypoint)
        assertEquals(execution.workingDirectory, snapshot.workingDirectory)
        assertEquals(execution.stopRequested, snapshot.stopRequested)
        assertEquals(execution.hasExitCode, snapshot.hasExitCode)
        assertEquals(execution.exitCode, snapshot.exitCode)
        assertEquals(execution.stdout, snapshot.stdout)
        assertEquals(execution.stderr, snapshot.stderr)
    }
}
