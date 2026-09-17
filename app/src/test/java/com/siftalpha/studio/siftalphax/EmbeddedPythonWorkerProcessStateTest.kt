package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWorkerProcessStateTest {
    @Test
    fun unboundProcessAcceptsFirstValidBinding() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-a")
        val binding = binding()

        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            state.bindingState(),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.BOUND_NEW,
            state.bind(request(binding)),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND,
            state.bindingState(),
        )
        assertEquals(binding.processBindingId.value, state.boundProcessBindingId())
    }

    @Test
    fun sameBindingIsIdempotent() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-b")
        val binding = binding()

        assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, state.bind(request(binding)))
        assertEquals(EmbeddedPythonWorkerProtocol.BOUND_SAME, state.bind(request(binding)))
        assertEquals(binding.processBindingId.value, state.boundProcessBindingId())
    }

    @Test
    fun differentBindingIsRejectedAndOriginalBindingRemains() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-c")
        val first = binding(projectIdentity = "fixture-project-a")
        val second = binding(projectIdentity = "fixture-project-b")

        assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, state.bind(request(first)))
        assertEquals(
            EmbeddedPythonWorkerProtocol.REJECTED_BINDING_CONFLICT,
            state.bind(request(second)),
        )
        assertEquals(first.processBindingId.value, state.boundProcessBindingId())
    }

    @Test
    fun invalidExpectedProcessBindingIdIsRejectedBeforeBinding() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-d")
        val binding = binding()

        assertEquals(
            EmbeddedPythonWorkerProtocol.REJECTED_INVALID_REQUEST,
            state.bind(request(binding, expectedProcessBindingId = "sha256:${"0".repeat(64)}")),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            state.bindingState(),
        )
    }

    @Test
    fun invalidRuntimeProvenanceDigestIsRejected() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-e")
        val binding = binding()

        assertEquals(
            EmbeddedPythonWorkerProtocol.REJECTED_INVALID_REQUEST,
            state.bind(
                request(
                    binding,
                    runtimeProvenanceDigest = "SHA256:${"0".repeat(64)}",
                ),
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            state.bindingState(),
        )
    }

    @Test
    fun unsupportedDependencyLayerIsRejected() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-f")
        val binding = binding()

        assertEquals(
            EmbeddedPythonWorkerProtocol.REJECTED_INVALID_REQUEST,
            state.bind(request(binding, dependencyLayerBinding = "pylock")),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            state.bindingState(),
        )
    }

    @Test
    fun matchingIdentityAcceptsTerminationAndEntersTerminating() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-termination-a")
        val binding = binding()
        state.bind(request(binding))

        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED,
            state.requestWorkerExitForRebind(
                expectedWorkerInstanceId = state.workerInstanceId,
                expectedProcessBindingId = binding.processBindingId.value,
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
            state.workerLifecycleState(),
        )
    }

    @Test
    fun repeatedMatchingTerminationIsIdempotentlyReported() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-termination-b")
        val binding = binding()
        state.bind(request(binding))
        val workerInstanceId = state.workerInstanceId
        val processBindingId = binding.processBindingId.value

        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED,
            state.requestWorkerExitForRebind(workerInstanceId, processBindingId),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_ALREADY_REQUESTED,
            state.requestWorkerExitForRebind(workerInstanceId, processBindingId),
        )
    }

    @Test
    fun wrongWorkerIdentityIsRejectedWithoutEnteringTerminating() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-termination-c")
        val binding = binding()
        state.bind(request(binding))

        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_REJECTED_IDENTITY_MISMATCH,
            state.requestWorkerExitForRebind(
                expectedWorkerInstanceId = "worker-another-process",
                expectedProcessBindingId = binding.processBindingId.value,
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            state.workerLifecycleState(),
        )
    }

    @Test
    fun wrongProcessBindingIdentityIsRejectedWithoutEnteringTerminating() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-termination-d")
        val binding = binding()
        state.bind(request(binding))

        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_REJECTED_IDENTITY_MISMATCH,
            state.requestWorkerExitForRebind(
                expectedWorkerInstanceId = state.workerInstanceId,
                expectedProcessBindingId = "sha256:${"f".repeat(64)}",
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            state.workerLifecycleState(),
        )
    }

    @Test
    fun invalidTerminationRequestIsRejected() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-termination-e")
        val binding = binding()
        state.bind(request(binding))

        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_REJECTED_INVALID_REQUEST,
            state.requestWorkerExitForRebind(
                expectedWorkerInstanceId = null,
                expectedProcessBindingId = binding.processBindingId.value,
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_REJECTED_INVALID_REQUEST,
            state.requestWorkerExitForRebind(
                expectedWorkerInstanceId = state.workerInstanceId,
                expectedProcessBindingId = "not-a-process-binding-id",
            ),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE,
            state.workerLifecycleState(),
        )
    }

    @Test
    fun terminatingWorkerRejectsAllFurtherBindingsWithoutReset() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-termination-f")
        val first = binding(projectIdentity = "fixture-project-a")
        val second = binding(projectIdentity = "fixture-project-b")
        state.bind(request(first))
        assertEquals(
            EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED,
            state.requestWorkerExitForRebind(
                state.workerInstanceId,
                first.processBindingId.value,
            ),
        )

        assertEquals(
            EmbeddedPythonWorkerProtocol.REJECTED_WORKER_TERMINATING,
            state.bind(request(first)),
        )
        assertEquals(
            EmbeddedPythonWorkerProtocol.REJECTED_WORKER_TERMINATING,
            state.bind(request(second)),
        )
        assertEquals(first.processBindingId.value, state.boundProcessBindingId())
        assertEquals(
            EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING,
            state.workerLifecycleState(),
        )
    }

    @Test
    fun serviceRecreationFacadeCannotResetProcessScopedState() {
        val state = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-g")
        val binding = binding()
        val firstService = WorkerServiceFacade(state)

        assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, firstService.bind(request(binding)))

        val recreatedService = WorkerServiceFacade(state)
        assertEquals(firstService.workerInstanceId, recreatedService.workerInstanceId)
        assertEquals(
            EmbeddedPythonWorkerProtocol.BOUND_SAME,
            recreatedService.bind(request(binding)),
        )
        assertEquals(binding.processBindingId.value, recreatedService.boundProcessBindingId)
        assertTrue(recreatedService.bindingState == EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND)
    }

    @Test
    fun separateProcessStateHasIndependentWorkerIdentityAndGate() {
        val firstProcess = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-h1")
        val secondProcess = EmbeddedPythonWorkerProcessState(workerInstanceId = "worker-test-h2")
        val binding = binding()

        assertNotEquals(firstProcess.workerInstanceId, secondProcess.workerInstanceId)
        assertEquals(EmbeddedPythonWorkerProtocol.BOUND_NEW, firstProcess.bind(request(binding)))
        assertEquals(
            EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND,
            secondProcess.bindingState(),
        )
    }

    private class WorkerServiceFacade(private val state: EmbeddedPythonWorkerProcessState) {
        val workerInstanceId: String
            get() = state.workerInstanceId
        val bindingState: Int
            get() = state.bindingState()
        val boundProcessBindingId: String
            get() = state.boundProcessBindingId()

        fun bind(request: EmbeddedPythonWorkerBindRequest): Int = state.bind(request)
    }

    private fun binding(
        projectIdentity: String = "fixture-project-a",
        sourceGeneration: String = "source-generation-0001",
        runtimeProvenanceDigest: String =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    ): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = projectIdentity,
            projectSourceGeneration = ProjectSourceGeneration.of(sourceGeneration),
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(runtimeProvenanceDigest),
            dependencyLayerBinding = DependencyLayerBinding.STDLIB_ONLY,
        )

    private fun request(
        binding: RuntimeLoadBindingV1,
        sourceGeneration: String = binding.projectSourceGeneration.value,
        runtimeProvenanceDigest: String = binding.runtimeProvenanceDigest.value,
        dependencyLayerBinding: String = binding.dependencyLayerBinding.wireValue,
        expectedProcessBindingId: String = binding.processBindingId.value,
    ): EmbeddedPythonWorkerBindRequest = EmbeddedPythonWorkerBindRequest(
        projectIdentity = binding.projectIdentity,
        projectSourceGeneration = sourceGeneration,
        runtimeProvenanceDigest = runtimeProvenanceDigest,
        dependencyLayerBinding = dependencyLayerBinding,
        expectedProcessBindingId = expectedProcessBindingId,
    )
}
