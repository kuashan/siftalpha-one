package com.siftalpha.studio.siftalphax

import java.io.File
import java.util.UUID

/** Caller-owned file-backed request; no project identity is accepted from this object. */
data class EmbeddedPythonWorkerProjectExecutionRequest(
    val executionRoot: String,
    val entrypoint: String,
    val workingDirectory: String,
) {
    init {
        require(executionRoot.isNotBlank()) { "execution root must not be blank" }
        require(!executionRoot.contains('\u0000')) { "execution root must not contain NUL" }
        require(File(executionRoot).isAbsolute) { "execution root must be absolute" }
        require(executionRoot.toByteArray(Charsets.UTF_8).size <= MAX_EXECUTION_ROOT_UTF8_BYTES) {
            "execution root is too long"
        }
        require(EmbeddedPythonExecutionSpec.isSafeRelativePath(entrypoint, allowCurrent = false)) {
            "entrypoint must be a safe relative path"
        }
        require(
            EmbeddedPythonExecutionSpec.isSafeRelativePath(
                workingDirectory,
                allowCurrent = true,
            ),
        ) {
            "working directory must be a safe relative path"
        }
    }

    private companion object {
        private const val MAX_EXECUTION_ROOT_UTF8_BYTES = 4096
    }
}

data class EmbeddedPythonWorkerProjectExecutionSnapshot(
    val state: Int,
    val sessionId: String,
    val generation: Long,
    val projectIdentity: String,
    val executionRoot: String,
    val entrypoint: String,
    val workingDirectory: String,
    val hasExitCode: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

data class EmbeddedPythonWorkerProjectExecutionInputs(
    val projectIdentity: String,
    val executionRoot: String,
    val entrypoint: String,
    val workingDirectory: String,
    val sessionId: String,
    val generation: Long,
)

/** Android-independent finite execution state and native snapshot adapter. */
class EmbeddedPythonWorkerProjectExecutionState {
    private var state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_NOT_STARTED
    private var sessionId = ""
    private var generation = 0L
    private var projectIdentity = ""
    private var request: EmbeddedPythonWorkerProjectExecutionRequest? = null
    private var exitCode: Int? = null
    private var stdout = ""
    private var stderr = ""

    @Synchronized
    fun start(
        expectedWorkerInstanceId: String?,
        expectedProcessBindingId: String?,
        actualWorkerInstanceId: String,
        boundRuntimeLoadBinding: RuntimeLoadBindingV1?,
        workerLifecycleState: Int,
        executionRoot: String?,
        entrypoint: String?,
        workingDirectory: String?,
    ): Int {
        val projectRequest = try {
            EmbeddedPythonWorkerProjectExecutionRequest(
                executionRoot = requireNotNull(executionRoot),
                entrypoint = requireNotNull(entrypoint),
                workingDirectory = requireNotNull(workingDirectory),
            )
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST
        }
        return start(
            expectedWorkerInstanceId = expectedWorkerInstanceId,
            expectedProcessBindingId = expectedProcessBindingId,
            actualWorkerInstanceId = actualWorkerInstanceId,
            boundRuntimeLoadBinding = boundRuntimeLoadBinding,
            workerLifecycleState = workerLifecycleState,
            request = projectRequest,
        )
    }

    @Synchronized
    fun start(
        expectedWorkerInstanceId: String?,
        expectedProcessBindingId: String?,
        actualWorkerInstanceId: String,
        boundRuntimeLoadBinding: RuntimeLoadBindingV1?,
        workerLifecycleState: Int,
        request: EmbeddedPythonWorkerProjectExecutionRequest,
    ): Int {
        val expectedBindingId = try {
            validateWorkerInstanceId(requireNotNull(expectedWorkerInstanceId))
            ProcessBindingId.parse(requireNotNull(expectedProcessBindingId))
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST
        }

        if (isActive()) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_BUSY
        }
        if (workerLifecycleState == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_WORKER_TERMINATING
        }
        val binding = boundRuntimeLoadBinding
            ?: return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_NOT_BOUND
        if (
            expectedWorkerInstanceId != actualWorkerInstanceId ||
            expectedBindingId != binding.processBindingId
        ) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_IDENTITY_MISMATCH
        }
        if (binding.dependencyLayerBinding != DependencyLayerBinding.STDLIB_ONLY) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_UNSUPPORTED_DEPENDENCY_LAYER
        }

        generation += 1L
        sessionId = "siftalpha-worker-exec-${UUID.randomUUID()}"
        projectIdentity = binding.projectIdentity
        this.request = request
        exitCode = null
        stdout = ""
        stderr = ""
        state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_PREPARING
        return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED
    }

    @Synchronized
    fun inputs(): EmbeddedPythonWorkerProjectExecutionInputs? {
        val currentRequest = request ?: return null
        return EmbeddedPythonWorkerProjectExecutionInputs(
            projectIdentity = projectIdentity,
            executionRoot = currentRequest.executionRoot,
            entrypoint = currentRequest.entrypoint,
            workingDirectory = currentRequest.workingDirectory,
            sessionId = sessionId,
            generation = generation,
        )
    }

    @Synchronized
    fun markStarting() {
        if (state == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_PREPARING) {
            state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STARTING
        }
    }

    @Synchronized
    fun markRunning() {
        if (state == EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STARTING) {
            state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING
        }
    }

    /** Maps a parsed native snapshot and fences it to the current Worker-owned execution. */
    @Synchronized
    fun applyNativeSnapshot(snapshot: EmbeddedPythonSnapshot): Int {
        if (!isActive()) return state
        if (snapshot.state == EmbeddedPythonState.IDLE && snapshot.sessionId.isBlank()) {
            return state
        }
        if (!matchesCurrentExecution(snapshot)) {
            completeInternalErrorLocked(
                detail = "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=identity_mismatch",
                snapshot = snapshot,
            )
            return state
        }

        when (snapshot.state) {
            EmbeddedPythonState.IDLE -> Unit
            EmbeddedPythonState.STARTING -> {
                state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STARTING
            }

            EmbeddedPythonState.RUNNING -> {
                state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING
            }

            EmbeddedPythonState.SUCCEEDED -> {
                completeTerminalLocked(
                    terminalState = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
                    snapshot = snapshot,
                )
            }

            EmbeddedPythonState.FAILED -> {
                completeTerminalLocked(
                    terminalState = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED,
                    snapshot = snapshot,
                )
            }

            EmbeddedPythonState.STOPPED -> {
                completeTerminalLocked(
                    terminalState = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
                    snapshot = snapshot,
                )
            }
        }
        return state
    }

    @Synchronized
    fun completeInternalError(detail: String, snapshot: EmbeddedPythonSnapshot? = null) {
        if (!isActive()) return
        if (snapshot == null) {
            completeInternalErrorLocked(detail, null)
        } else {
            completeInternalErrorLocked(detail, snapshot)
        }
    }

    @Synchronized
    fun snapshot(): EmbeddedPythonWorkerProjectExecutionSnapshot {
        val currentRequest = request
        val root = currentRequest?.executionRoot.orEmpty()
        val entrypoint = currentRequest?.let { File(root, it.entrypoint).absolutePath }.orEmpty()
        val workingDirectory = currentRequest?.let {
            File(root, it.workingDirectory).absolutePath
        }.orEmpty()
        return EmbeddedPythonWorkerProjectExecutionSnapshot(
            state = state,
            sessionId = sessionId,
            generation = generation,
            projectIdentity = projectIdentity,
            executionRoot = root,
            entrypoint = entrypoint,
            workingDirectory = workingDirectory,
            hasExitCode = exitCode != null,
            exitCode = exitCode ?: -1,
            stdout = stdout,
            stderr = stderr,
        )
    }

    private fun isActive(): Boolean = when (state) {
        EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_PREPARING,
        EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STARTING,
        EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
        -> true

        else -> false
    }

    private fun matchesCurrentExecution(snapshot: EmbeddedPythonSnapshot): Boolean {
        val currentRequest = request ?: return false
        val expectedRoot = currentRequest.executionRoot
        val expectedEntrypoint = File(expectedRoot, currentRequest.entrypoint).absolutePath
        val expectedWorkingDirectory = File(expectedRoot, currentRequest.workingDirectory).absolutePath
        return snapshot.sessionId == sessionId &&
            snapshot.projectIdentity == projectIdentity &&
            snapshot.generation == generation &&
            equivalentPath(snapshot.executionRoot, expectedRoot) &&
            equivalentPath(snapshot.entrypoint, expectedEntrypoint) &&
            equivalentPath(snapshot.workingDirectory, expectedWorkingDirectory)
    }

    private fun completeTerminalLocked(
        terminalState: Int,
        snapshot: EmbeddedPythonSnapshot,
    ) {
        state = terminalState
        exitCode = snapshot.exitCode
        stdout = boundOutput(snapshot.stdout)
        stderr = boundOutput(snapshot.stderr)
    }

    private fun completeInternalErrorLocked(
        detail: String,
        snapshot: EmbeddedPythonSnapshot?,
    ) {
        state = EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR
        exitCode = snapshot?.exitCode
        stdout = boundOutput(snapshot?.stdout.orEmpty())
        val snapshotStderr = snapshot?.stderr.orEmpty()
        stderr = boundOutput(
            listOf(snapshotStderr, detail).filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

    private companion object {
        private const val MAX_OUTPUT_CHARS = 64 * 1024
        private const val MAX_WORKER_INSTANCE_ID_UTF8_BYTES = 256

        private fun boundOutput(value: String): String = value.take(MAX_OUTPUT_CHARS)

        private fun equivalentPath(expected: String, actual: String): Boolean {
            if (expected.isBlank() || actual.isBlank()) return false
            return runCatching {
                File(expected).canonicalPath == File(actual).canonicalPath
            }.getOrDefault(expected == actual)
        }

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
