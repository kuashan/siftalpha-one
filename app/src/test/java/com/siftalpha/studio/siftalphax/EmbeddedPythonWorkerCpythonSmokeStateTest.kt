package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWorkerCpythonSmokeStateTest {
    @Test
    fun unboundWorkerCannotAuthorizeSmoke() {
        val smoke = EmbeddedPythonWorkerCpythonSmokeState()
        val binding = binding()

        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_NOT_BOUND,
            smoke.start(
                expectedWorkerInstanceId = "worker-test-a",
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = "worker-test-a",
                actualProcessBindingId = "",
                bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            ),
        )
    }

    @Test
    fun workerIdentityMismatchIsRejected() {
        val smoke = EmbeddedPythonWorkerCpythonSmokeState()
        val binding = binding()

        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_IDENTITY_MISMATCH,
            smoke.start(
                expectedWorkerInstanceId = "worker-expected",
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = "worker-actual",
                actualProcessBindingId = binding.processBindingId.value,
                bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            ),
        )
    }

    @Test
    fun bindingIdentityMismatchIsRejected() {
        val smoke = EmbeddedPythonWorkerCpythonSmokeState()
        val expected = binding(projectIdentity = "expected")
        val actual = binding(projectIdentity = "actual")

        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_IDENTITY_MISMATCH,
            smoke.start(
                expectedWorkerInstanceId = "worker-actual",
                expectedProcessBindingId = expected.processBindingId.value,
                actualWorkerInstanceId = "worker-actual",
                actualProcessBindingId = actual.processBindingId.value,
                bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            ),
        )
    }

    @Test
    fun terminatingWorkerRejectsSmoke() {
        val worker = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-terminating")
        val binding = binding()
        assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, worker.bind(bindRequest(binding)))
        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED,
            worker.requestWorkerExitForRebind(
                worker.workerInstanceId,
                binding.processBindingId.value,
            ),
        )

        val smoke = EmbeddedPythonWorkerCpythonSmokeState()
        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_WORKER_TERMINATING,
            smoke.start(
                expectedWorkerInstanceId = worker.workerInstanceId,
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = worker.workerInstanceId,
                actualProcessBindingId = binding.processBindingId.value,
                bindingState = worker.bindingState(),
                workerLifecycleState = worker.workerLifecycleState(),
            ),
        )
    }

    @Test
    fun firstValidStartCreatesOneSmokeSessionAndSecondStartIsRejectedAsAlreadyStarted() {
        val smoke = EmbeddedPythonWorkerCpythonSmokeState()
        val binding = binding()
        val result = smoke.start(
            expectedWorkerInstanceId = "worker-valid",
            expectedProcessBindingId = binding.processBindingId.value,
            actualWorkerInstanceId = "worker-valid",
            actualProcessBindingId = binding.processBindingId.value,
            bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
            workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
        )

        assertEquals(EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ACCEPTED, result)
        val first = smoke.snapshot()
        assertEquals(EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_PREPARING, first.state)
        assertTrue(first.generation > 0L)
        assertTrue(first.sessionId.startsWith("siftalpha-worker-smoke-"))
        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ALREADY_STARTED,
            smoke.start(
                expectedWorkerInstanceId = "worker-valid",
                expectedProcessBindingId = binding.processBindingId.value,
                actualWorkerInstanceId = "worker-valid",
                actualProcessBindingId = binding.processBindingId.value,
                bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            ),
        )
        assertEquals(first.sessionId, smoke.snapshot().sessionId)
    }

    @Test
    fun invalidRequestIsRejectedBeforeSmokeStarts() {
        val smoke = EmbeddedPythonWorkerCpythonSmokeState()
        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_INVALID_REQUEST,
            smoke.start(
                expectedWorkerInstanceId = "not-a-worker-id",
                expectedProcessBindingId = "invalid",
                actualWorkerInstanceId = "worker-valid",
                actualProcessBindingId = "",
                bindingState = EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
                workerLifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED,
            smoke.snapshot().state,
        )
    }

    @Test
    fun validStdlibSmokeEvidenceParsesPythonPid() {
        val evidence = EmbeddedPythonWorkerCpythonSmokeEvidence.parse(
            """
            ${EmbeddedPythonWorkerCpythonSmokeEvidence.PASS_MARKER}
            ${EmbeddedPythonWorkerCpythonSmokeEvidence.PID_MARKER}4242
            ${EmbeddedPythonWorkerCpythonSmokeEvidence.STDLIB_JSON_MARKER}${EmbeddedPythonWorkerCpythonSmokeEvidence.EXPECTED_STDLIB_JSON}
            """.trimIndent(),
        )

        assertNotNull(evidence)
        assertEquals(4242, evidence?.pythonPid)
    }

    @Test
    fun missingPassMarkerMakesEvidenceInvalid() {
        assertNull(
            EmbeddedPythonWorkerCpythonSmokeEvidence.parse(
                "${EmbeddedPythonWorkerCpythonSmokeEvidence.PID_MARKER}4242\n" +
                    EmbeddedPythonWorkerCpythonSmokeEvidence.STDLIB_JSON_MARKER +
                    EmbeddedPythonWorkerCpythonSmokeEvidence.EXPECTED_STDLIB_JSON,
            ),
        )
    }

    @Test
    fun invalidPidMarkerMakesEvidenceInvalid() {
        assertNull(
            EmbeddedPythonWorkerCpythonSmokeEvidence.parse(
                EmbeddedPythonWorkerCpythonSmokeEvidence.PASS_MARKER + "\n" +
                    EmbeddedPythonWorkerCpythonSmokeEvidence.PID_MARKER + "not-a-pid\n" +
                    EmbeddedPythonWorkerCpythonSmokeEvidence.STDLIB_JSON_MARKER +
                    EmbeddedPythonWorkerCpythonSmokeEvidence.EXPECTED_STDLIB_JSON,
            ),
        )
    }

    @Test
    fun missingOrWrongStdlibMarkerMakesEvidenceInvalid() {
        val missing = EmbeddedPythonWorkerCpythonSmokeEvidence.PASS_MARKER + "\n" +
            EmbeddedPythonWorkerCpythonSmokeEvidence.PID_MARKER + "4242"
        assertNull(EmbeddedPythonWorkerCpythonSmokeEvidence.parse(missing))

        val wrong = missing + "\n" +
            EmbeddedPythonWorkerCpythonSmokeEvidence.STDLIB_JSON_MARKER + "{}"
        assertNull(EmbeddedPythonWorkerCpythonSmokeEvidence.parse(wrong))
    }

    private fun binding(projectIdentity: String = "smoke-project"): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = projectIdentity,
            projectSourceGeneration = ProjectSourceGeneration.of("smoke-source-0001"),
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
            dependencyLayerBinding = DependencyLayerBinding.STDLIB_ONLY,
        )

    private fun bindRequest(binding: RuntimeLoadBindingV1): EmbeddedPythonWorkerBindRequest =
        EmbeddedPythonWorkerBindRequest(
            projectIdentity = binding.projectIdentity,
            projectSourceGeneration = binding.projectSourceGeneration.value,
            runtimeProvenanceDigest = binding.runtimeProvenanceDigest.value,
            dependencyLayerBinding = binding.dependencyLayerBinding.wireValue,
            expectedProcessBindingId = binding.processBindingId.value,
        )
}
