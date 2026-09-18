package com.siftalpha.studio.siftalphax

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWorkerProjectExecutionStateTest {
    @Test
    fun unboundWorkerRejectsExecution() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_NOT_BOUND,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = null,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                request = request(),
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED,
            state.snapshot().state,
        )
    }

    @Test
    fun freshProjectSnapshotHasNoExecutionOrResult() {
        val snapshot = EmbeddedPythonWorkerProjectExecutionState().snapshot()

        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED, snapshot.state)
        assertEquals("", snapshot.sessionId)
        assertEquals(0L, snapshot.generation)
        assertEquals("", snapshot.projectIdentity)
        assertEquals("", snapshot.executionRoot)
        assertEquals("", snapshot.entrypoint)
        assertEquals("", snapshot.workingDirectory)
        assertFalse(snapshot.stopRequested)
        assertFalse(snapshot.hasExitCode)
        assertEquals("", snapshot.stdout)
        assertEquals("", snapshot.stderr)
    }

    @Test
    fun workerIdentityMismatchIsRejected() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_IDENTITY_MISMATCH,
            state.start(
                expectedWorkerInstanceId = "worker-other",
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                request = request(),
            ),
        )
    }

    @Test
    fun processBindingMismatchIsRejected() {
        val bound = binding(projectIdentity = "bound-project")
        val requested = binding(projectIdentity = "requested-project")
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_IDENTITY_MISMATCH,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = requested.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = bound,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                request = request(),
            ),
        )
    }

    @Test
    fun terminatingWorkerRejectsExecution() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_WORKER_TERMINATING,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
                request = request(),
            ),
        )
    }

    @Test
    fun invalidAbsoluteAndEscapingPathsAreRejected() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                executionRoot = "relative/root",
                entrypoint = "main.py",
                workingDirectory = ".",
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                executionRoot = "$ROOT\u0000",
                entrypoint = "main.py",
                workingDirectory = ".",
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                executionRoot = ROOT,
                entrypoint = "/absolute.py",
                workingDirectory = ".",
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                executionRoot = ROOT,
                entrypoint = "../main.py",
                workingDirectory = ".",
            ),
        )
    }

    @Test
    fun firstValidExecutionUsesBoundProjectIdentityAndEntersPreparing() {
        val binding = binding(projectIdentity = "bound-project")
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            state.start(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = WORKER_ID,
                boundRuntimeLoadBinding = binding,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
                request = request(),
            ),
        )
        val snapshot = state.snapshot()
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_PREPARING, snapshot.state)
        assertEquals(binding.projectIdentity, snapshot.projectIdentity)
        assertTrue(snapshot.sessionId.startsWith("siftalpha-worker-exec-"))
        assertTrue(snapshot.generation > 0L)
        assertEquals(ROOT, snapshot.executionRoot)
    }

    @Test
    fun activeExecutionRejectsConcurrentStartAsBusy() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding),
        )

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_BUSY,
            start(state, binding, entrypoint = "second.py"),
        )
    }

    @Test
    fun terminalExecutionAllowsSequentialReentryWithNewSessionAndGeneration() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding),
        )
        state.markStarting()
        state.markRunning()
        val first = state.snapshot()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
            state.applyNativeSnapshot(nativeSnapshot(first, EmbeddedPythonState.SUCCEEDED, 0)),
        )

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding, entrypoint = "second.py"),
        )
        val second = state.snapshot()
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_PREPARING, second.state)
        assertNotEquals(first.sessionId, second.sessionId)
        assertTrue(second.generation > first.generation)
        assertEquals(binding.projectIdentity, second.projectIdentity)
    }

    @Test
    fun stopBeforeExecutionIsRejectedAsNotRunning() {
        val state = EmbeddedPythonWorkerProjectExecutionState()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_NOT_RUNNING,
            state.requestStop(
                expectedWorkerInstanceId = WORKER_ID,
                expectedProcessBindingId = binding().processBindingId.value,
                expectedExecutionSessionId = "siftalpha-worker-exec-not-started",
                expectedExecutionGeneration = 1L,
                actualWorkerInstanceId = WORKER_ID,
                actualProcessBindingId = binding().processBindingId.value,
            ),
        )
    }

    @Test
    fun invalidStopRequestIsRejectedBeforeStateChanges() {
        val state = startedState(binding())

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_INVALID_REQUEST,
            state.requestStop(
                expectedWorkerInstanceId = "not-a-worker-id",
                expectedProcessBindingId = "invalid",
                expectedExecutionSessionId = "",
                expectedExecutionGeneration = 0L,
                actualWorkerInstanceId = WORKER_ID,
                actualProcessBindingId = binding().processBindingId.value,
            ),
        )
        assertFalse(state.isStopRequested())
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
            state.snapshot().state,
        )
    }

    @Test
    fun stopDuringPreparingAndStartingIsRejectedAsNotRunning() {
        val binding = binding()
        val state = EmbeddedPythonWorkerProjectExecutionState()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_NOT_RUNNING,
            stop(state, binding),
        )

        state.markStarting()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_NOT_RUNNING,
            stop(state, binding),
        )
    }

    @Test
    fun matchingRunningExecutionAcceptsStopAndDuplicateIsIdempotent() {
        val binding = binding()
        val state = startedState(binding)

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_ACCEPTED,
            stop(state, binding),
        )
        assertTrue(state.isStopRequested())
        assertTrue(state.snapshot().stopRequested)
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_ALREADY_REQUESTED,
            stop(state, binding),
        )
    }

    @Test
    fun stopRejectsWrongWorkerBindingSessionAndGenerationIdentity() {
        val binding = binding()
        val state = startedState(binding)
        val current = state.snapshot()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH,
            stop(state, binding, workerInstanceId = "worker-stale"),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH,
            stop(
                state,
                binding,
                processBindingId = binding(projectIdentity = "other").processBindingId.value,
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH,
            stop(state, binding, sessionId = "siftalpha-worker-exec-stale"),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH,
            stop(state, binding, generation = current.generation + 1L),
        )
        assertFalse(state.isStopRequested())
    }

    @Test
    fun staleStopFromExecutionACannotAuthorizeOrChangeExecutionB() {
        val binding = binding()
        val state = startedState(binding)
        val first = state.snapshot()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
            state.applyNativeSnapshot(nativeSnapshot(first, EmbeddedPythonState.STOPPED, 130)),
        )

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding, entrypoint = "after.py"),
        )
        state.markStarting()
        state.markRunning()
        val second = state.snapshot()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH,
            stop(
                state,
                binding,
                sessionId = first.sessionId,
                generation = first.generation,
            ),
        )
        assertFalse(state.isStopRequested())
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_ACCEPTED,
            stop(state, binding, sessionId = second.sessionId, generation = second.generation),
        )
    }

    @Test
    fun stoppedTerminalUsesCooperativeExitCodeAndAllowsReentry() {
        val binding = binding()
        val state = startedState(binding)
        val first = state.snapshot()

        state.applyNativeSnapshot(
            nativeSnapshot(
                first,
                EmbeddedPythonState.STOPPED,
                exitCode = 0,
                stdout = "started\ncooperative stop",
                stderr = "SIFTALPHA_X_STOP=COOPERATIVE",
            ),
        )
        val stopped = state.snapshot()
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED, stopped.state)
        assertTrue(stopped.stopRequested)
        assertTrue(stopped.hasExitCode)
        assertEquals(130, stopped.exitCode)

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding, entrypoint = "after.py"),
        )
        assertFalse(state.isStopRequested())
        assertFalse(state.snapshot().stopRequested)
        assertTrue(state.snapshot().generation > first.generation)
    }

    @Test
    fun stoppedTerminalResultIsSealedAgainstLateNativeSnapshots() {
        val binding = binding()
        val state = startedState(binding)
        val current = state.snapshot()
        val stoppedOutput = nativeSnapshot(
            current,
            EmbeddedPythonState.STOPPED,
            exitCode = 0,
            stdout = "stopped stdout",
            stderr = "stopped stderr",
        )

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
            state.applyNativeSnapshot(stoppedOutput),
        )
        val sealed = state.snapshot()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
            state.applyNativeSnapshot(
                nativeSnapshot(
                    current,
                    EmbeddedPythonState.SUCCEEDED,
                    exitCode = 0,
                    stdout = "late success",
                    stderr = "late stderr",
                ),
            ),
        )
        assertEquals(sealed, state.snapshot())
    }

    @Test
    fun normalTerminalResultIsSealedAgainstLateNativeSnapshots() {
        val binding = binding()
        val state = startedState(binding)
        val current = state.snapshot()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
            state.applyNativeSnapshot(
                nativeSnapshot(
                    current,
                    EmbeddedPythonState.SUCCEEDED,
                    exitCode = 0,
                    stdout = "first success",
                    stderr = "",
                ),
            ),
        )
        val sealed = state.snapshot()
        state.applyNativeSnapshot(
            nativeSnapshot(
                current,
                EmbeddedPythonState.FAILED,
                exitCode = 9,
                stdout = "late failure",
                stderr = "late failure stderr",
            ),
        )
        assertEquals(sealed, state.snapshot())
    }

    @Test
    fun projectLeaseRemainsBusyUntilItsExactLeaseIsReleased() {
        val binding = binding()
        val state = startedState(binding)
        val gate = EmbeddedPythonWorkerRuntimeUseGate()
        val acquire = gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION)
        assertTrue(acquire is EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired)
        val projectLease = (acquire as EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired).lease

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_ACCEPTED,
            stop(state, binding),
        )
        assertTrue(state.isStopRequested())
        assertTrue(
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE) is
                EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OtherOwnerBusy,
        )

        gate.release(projectLease)
        assertTrue(
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE) is
                EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired,
        )
    }

    @Test
    fun nativeFailedTerminalMapsToProjectFailed() {
        val binding = binding()
        val state = startedState(binding)
        val current = state.snapshot()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED,
            state.applyNativeSnapshot(
                nativeSnapshot(
                    current,
                    EmbeddedPythonState.FAILED,
                    exitCode = 2,
                    stdout = "project stdout",
                    stderr = "project stderr",
                ),
            ),
        )
        val result = state.snapshot()
        assertTrue(result.hasExitCode)
        assertEquals(2, result.exitCode)
        assertEquals("project stdout", result.stdout)
        assertEquals("project stderr", result.stderr)
    }

    @Test
    fun nativeIdentityMismatchMapsToInternalError() {
        val binding = binding()
        val state = startedState(binding)
        val current = state.snapshot()

        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR,
            state.applyNativeSnapshot(
                nativeSnapshot(
                    current.copy(sessionId = "siftalpha-worker-exec-other"),
                    EmbeddedPythonState.SUCCEEDED,
                    exitCode = 0,
                ),
            ),
        )
        assertTrue(state.snapshot().stderr.contains("identity_mismatch"))
    }

    @Test
    fun adapterFailureMapsToInternalError() {
        val state = startedState(binding())

        state.completeInternalError("SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=snapshot_parse")

        val result = state.snapshot()
        assertEquals(EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR, result.state)
        assertFalse(result.hasExitCode)
        assertTrue(result.stderr.contains("snapshot_parse"))
    }

    @Test
    fun outputIsBoundedBeforeItReachesTypedResult() {
        val state = startedState(binding())
        val current = state.snapshot()
        val large = "x".repeat(70 * 1024)

        state.applyNativeSnapshot(
            nativeSnapshot(
                current,
                EmbeddedPythonState.SUCCEEDED,
                exitCode = 0,
                stdout = large,
                stderr = large,
            ),
        )

        assertEquals(64 * 1024, state.snapshot().stdout.length)
        assertEquals(64 * 1024, state.snapshot().stderr.length)
    }

    private fun startedState(binding: RuntimeLoadBindingV1): EmbeddedPythonWorkerProjectExecutionState {
        val state = EmbeddedPythonWorkerProjectExecutionState()
        assertEquals(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED,
            start(state, binding),
        )
        state.markStarting()
        state.markRunning()
        return state
    }

    private fun start(
        state: EmbeddedPythonWorkerProjectExecutionState,
        binding: RuntimeLoadBindingV1,
        entrypoint: String = "main.py",
    ): Int = state.start(
        expectedWorkerInstanceId = WORKER_ID,
        expectedProcessBindingId = binding.processBindingId.value,
        actualWorkerInstanceId = WORKER_ID,
        boundRuntimeLoadBinding = binding,
        workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
        request = request(entrypoint = entrypoint),
    )

    private fun stop(
        state: EmbeddedPythonWorkerProjectExecutionState,
        binding: RuntimeLoadBindingV1,
        workerInstanceId: String = WORKER_ID,
        processBindingId: String = binding.processBindingId.value,
        sessionId: String = state.snapshot().sessionId,
        generation: Long = state.snapshot().generation,
    ): Int = state.requestStop(
        expectedWorkerInstanceId = workerInstanceId,
        expectedProcessBindingId = processBindingId,
        expectedExecutionSessionId = sessionId,
        expectedExecutionGeneration = generation,
        actualWorkerInstanceId = WORKER_ID,
        actualProcessBindingId = binding.processBindingId.value,
    )

    private fun request(
        executionRoot: String = ROOT,
        entrypoint: String = "main.py",
        workingDirectory: String = ".",
    ): EmbeddedPythonWorkerProjectExecutionRequest =
        EmbeddedPythonWorkerProjectExecutionRequest(
            executionRoot = executionRoot,
            entrypoint = entrypoint,
            workingDirectory = workingDirectory,
        )

    private fun nativeSnapshot(
        current: EmbeddedPythonWorkerProjectExecutionSnapshot,
        state: EmbeddedPythonState,
        exitCode: Int,
        stdout: String = "",
        stderr: String = "",
    ): EmbeddedPythonSnapshot = EmbeddedPythonSnapshot(
        sessionId = current.sessionId,
        projectIdentity = current.projectIdentity,
        executionRoot = current.executionRoot,
        entrypoint = current.entrypoint,
        workingDirectory = current.workingDirectory,
        generation = current.generation,
        state = state,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
    )

    private fun binding(projectIdentity: String = "worker-project"): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = projectIdentity,
            projectSourceGeneration = ProjectSourceGeneration.of("worker-project-source-v1"),
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
            dependencyLayerBinding = DependencyLayerBinding.STDLIB_ONLY,
        )

    private companion object {
        private const val WORKER_ID = "worker-project-test"
        private val ROOT = File(
            System.getProperty("java.io.tmpdir"),
            "siftalpha-worker-project-test",
        ).absolutePath
    }
}
