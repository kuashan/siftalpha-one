package com.siftalpha.studio.siftalphax

import java.util.UUID

/** Stable integer values shared by the typed worker IPC and its JVM state-machine tests. */
object EmbeddedPythonWorkerProtocol {
    const val BINDING_STATE_UNBOUND = 0
    const val BINDING_STATE_BOUND = 1

    const val BOUND_NEW = 0
    const val BOUND_SAME = 1
    const val REJECTED_INVALID_REQUEST = 2
    const val REJECTED_BINDING_CONFLICT = 3
    const val REJECTED_WORKER_TERMINATING = 4

    const val WORKER_LIFECYCLE_ACTIVE = 0
    const val WORKER_LIFECYCLE_TERMINATING = 1

    const val EXIT_ACCEPTED = 0
    const val EXIT_ALREADY_REQUESTED = 1
    const val EXIT_REJECTED_INVALID_REQUEST = 2
    const val EXIT_REJECTED_IDENTITY_MISMATCH = 3
    const val EXIT_REJECTED_WRONG_PROCESS = 4

    const val CPYTHON_SMOKE_NOT_STARTED = 0
    const val CPYTHON_SMOKE_PREPARING = 1
    const val CPYTHON_SMOKE_STARTING = 2
    const val CPYTHON_SMOKE_RUNNING = 3
    const val CPYTHON_SMOKE_SUCCEEDED = 4
    const val CPYTHON_SMOKE_FAILED = 5

    const val CPYTHON_SMOKE_START_ACCEPTED = 0
    const val CPYTHON_SMOKE_START_ALREADY_STARTED = 1
    const val CPYTHON_SMOKE_START_REJECTED_INVALID_REQUEST = 2
    const val CPYTHON_SMOKE_START_REJECTED_IDENTITY_MISMATCH = 3
    const val CPYTHON_SMOKE_START_REJECTED_NOT_BOUND = 4
    const val CPYTHON_SMOKE_START_REJECTED_WORKER_TERMINATING = 5
    const val CPYTHON_SMOKE_START_REJECTED_WRONG_PROCESS = 6
    const val CPYTHON_SMOKE_START_REJECTED_RUNTIME_BUSY = 7

    const val PROJECT_EXECUTION_NOT_STARTED = 0
    const val PROJECT_EXECUTION_PREPARING = 1
    const val PROJECT_EXECUTION_STARTING = 2
    const val PROJECT_EXECUTION_RUNNING = 3
    const val PROJECT_EXECUTION_SUCCEEDED = 4
    const val PROJECT_EXECUTION_FAILED = 5
    const val PROJECT_EXECUTION_STOPPED = 6
    const val PROJECT_EXECUTION_INTERNAL_ERROR = 7

    const val PROJECT_EXECUTION_START_ACCEPTED = 0
    const val PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST = 1
    const val PROJECT_EXECUTION_START_REJECTED_IDENTITY_MISMATCH = 2
    const val PROJECT_EXECUTION_START_REJECTED_NOT_BOUND = 3
    const val PROJECT_EXECUTION_START_REJECTED_WORKER_TERMINATING = 4
    const val PROJECT_EXECUTION_START_REJECTED_WRONG_PROCESS = 5
    const val PROJECT_EXECUTION_START_REJECTED_BUSY = 6
    const val PROJECT_EXECUTION_START_REJECTED_RUNTIME_MODE_CONFLICT = 7
    const val PROJECT_EXECUTION_START_REJECTED_UNSUPPORTED_DEPENDENCY_LAYER = 8

    const val PROJECT_EXECUTION_STOP_ACCEPTED = 0
    const val PROJECT_EXECUTION_STOP_ALREADY_REQUESTED = 1
    const val PROJECT_EXECUTION_STOP_REJECTED_INVALID_REQUEST = 2
    const val PROJECT_EXECUTION_STOP_REJECTED_IDENTITY_MISMATCH = 3
    const val PROJECT_EXECUTION_STOP_REJECTED_NOT_RUNNING = 4
    const val PROJECT_EXECUTION_STOP_REJECTED_WRONG_PROCESS = 5
    const val PROJECT_EXECUTION_STOP_NATIVE_REJECTED = 6
}

data class EmbeddedPythonWorkerBindRequest(
    val projectIdentity: String?,
    val projectSourceGeneration: String?,
    val runtimeProvenanceDigest: String?,
    val dependencyLayerBinding: String?,
    val expectedProcessBindingId: String?,
)

/**
 * Process-scoped binding gate for the dedicated Runtime worker.
 *
 * Android Service instances may be recreated while their hosting process remains alive. This
 * state therefore belongs to the worker process owner, not to an individual Service object.
 * A new binding is only possible after the operating system creates a new worker process.
 */
class EmbeddedPythonWorkerProcessState(
    val workerInstanceId: String = "worker-${UUID.randomUUID()}",
) {
    private var boundProcessBindingId: ProcessBindingId? = null
    private var boundRuntimeLoadBinding: RuntimeLoadBindingV1? = null
    private var lifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_ACTIVE

    init {
        validateWorkerInstanceId(workerInstanceId)
    }

    @Synchronized
    fun bindingState(): Int = if (boundProcessBindingId == null) {
        EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND
    } else {
        EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND
    }

    @Synchronized
    fun boundProcessBindingId(): String = boundProcessBindingId?.value.orEmpty()

    /** R-only immutable binding facts captured at BOUND_NEW and never replaced in this process. */
    @Synchronized
    fun boundRuntimeLoadBinding(): RuntimeLoadBindingV1? = boundRuntimeLoadBinding

    @Synchronized
    fun workerLifecycleState(): Int = lifecycleState

    /**
     * Rebuilds and verifies RuntimeLoadBindingV1 before the process gate is consulted.
     */
    @Synchronized
    fun bind(request: EmbeddedPythonWorkerBindRequest): Int {
        val binding = try {
            RuntimeLoadBindingV1(
                projectIdentity = requireNotNull(request.projectIdentity),
                projectSourceGeneration = ProjectSourceGeneration.of(
                    requireNotNull(request.projectSourceGeneration),
                ),
                runtimeProvenanceDigest = RuntimeProvenanceDigest.of(
                    requireNotNull(request.runtimeProvenanceDigest),
                ),
                dependencyLayerBinding = DependencyLayerBinding.fromWireValue(
                    requireNotNull(request.dependencyLayerBinding),
                ),
            )
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.REJECTED_INVALID_REQUEST
        }
        val requestedBindingId = try {
            val expectedBindingId = ProcessBindingId.parse(
                requireNotNull(request.expectedProcessBindingId),
            )
            val actualBindingId = binding.processBindingId
            require(actualBindingId == expectedBindingId) {
                "caller supplied ProcessBindingId does not match the RuntimeLoadBindingV1 input"
            }
            actualBindingId
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.REJECTED_INVALID_REQUEST
        }

        if (lifecycleState == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING) {
            return EmbeddedPythonWorkerProtocol.REJECTED_WORKER_TERMINATING
        }

        val existingBindingId = boundProcessBindingId
        return when {
            existingBindingId == null -> {
                boundProcessBindingId = requestedBindingId
                boundRuntimeLoadBinding = binding
                EmbeddedPythonWorkerProtocol.BOUND_NEW
            }

            existingBindingId == requestedBindingId -> {
                EmbeddedPythonWorkerProtocol.BOUND_SAME
            }

            else -> EmbeddedPythonWorkerProtocol.REJECTED_BINDING_CONFLICT
        }
    }

    /**
     * Fences process termination to the current worker identity and binding. There is
     * intentionally no reset operation: TERMINATING can only end when this OS process dies.
     */
    @Synchronized
    fun requestWorkerExitForRebind(
        expectedWorkerInstanceId: String?,
        expectedProcessBindingId: String?,
    ): Int {
        val requestedWorkerInstanceId = try {
            validateWorkerInstanceId(requireNotNull(expectedWorkerInstanceId))
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.EXIT_REJECTED_INVALID_REQUEST
        }
        val requestedProcessBindingId = try {
            ProcessBindingId.parse(requireNotNull(expectedProcessBindingId))
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.EXIT_REJECTED_INVALID_REQUEST
        }

        if (
            requestedWorkerInstanceId != workerInstanceId ||
            requestedProcessBindingId != boundProcessBindingId
        ) {
            return EmbeddedPythonWorkerProtocol.EXIT_REJECTED_IDENTITY_MISMATCH
        }

        if (lifecycleState == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING) {
            return EmbeddedPythonWorkerProtocol.EXIT_ALREADY_REQUESTED
        }

        lifecycleState = EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING
        return EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED
    }

    private companion object {
        private const val MAX_WORKER_INSTANCE_ID_UTF8_BYTES = 256

        private fun validateWorkerInstanceId(value: String): String {
            require(value.isNotBlank()) { "worker instance id must not be blank" }
            require(!value.contains('\u0000')) { "worker instance id must not contain NUL" }
            require(value.startsWith("worker-") && value.length > "worker-".length) {
                "worker instance id must be a non-empty worker-<id> value"
            }
            require(value.toByteArray(Charsets.UTF_8).size <= MAX_WORKER_INSTANCE_ID_UTF8_BYTES) {
                "worker instance id exceeds $MAX_WORKER_INSTANCE_ID_UTF8_BYTES UTF-8 bytes"
            }
            return value
        }
    }
}
