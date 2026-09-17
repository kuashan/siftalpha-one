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

    init {
        require(workerInstanceId.startsWith("worker-") && workerInstanceId.length > "worker-".length) {
            "worker instance id must be a non-empty worker-<id> value"
        }
    }

    @Synchronized
    fun bindingState(): Int = if (boundProcessBindingId == null) {
        EmbeddedPythonWorkerProtocol.BINDING_STATE_UNBOUND
    } else {
        EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND
    }

    @Synchronized
    fun boundProcessBindingId(): String = boundProcessBindingId?.value.orEmpty()

    /**
     * Rebuilds and verifies RuntimeLoadBindingV1 before the process gate is consulted.
     */
    @Synchronized
    fun bind(request: EmbeddedPythonWorkerBindRequest): Int {
        val requestedBindingId = try {
            val binding = RuntimeLoadBindingV1(
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

        val existingBindingId = boundProcessBindingId
        return when {
            existingBindingId == null -> {
                boundProcessBindingId = requestedBindingId
                EmbeddedPythonWorkerProtocol.BOUND_NEW
            }

            existingBindingId == requestedBindingId -> {
                EmbeddedPythonWorkerProtocol.BOUND_SAME
            }

            else -> EmbeddedPythonWorkerProtocol.REJECTED_BINDING_CONFLICT
        }
    }
}
