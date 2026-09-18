package com.siftalpha.studio.siftalphax

import java.util.UUID

/** Immutable typed facts exposed by the worker smoke Binder contract. */
data class EmbeddedPythonWorkerCpythonSmokeSnapshot(
    val state: Int,
    val hasExitCode: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val pythonPid: Int,
    val sessionId: String,
    val generation: Long,
)

/**
 * Android-independent, one-shot state for the Worker-side CPython smoke.
 *
 * The state has no reset operation. Its lifetime is the lifetime of the Worker OS process, so a
 * retry is intentionally a fresh-process operation rather than a second interpreter start.
 */
class EmbeddedPythonWorkerCpythonSmokeState {
    private var state = EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED
    private var sessionId = ""
    private var generation = 0L
    private var exitCode: Int? = null
    private var stdout = ""
    private var stderr = ""
    private var pythonPid = -1

    @Synchronized
    fun start(
        expectedWorkerInstanceId: String?,
        expectedProcessBindingId: String?,
        actualWorkerInstanceId: String,
        actualProcessBindingId: String,
        bindingState: Int,
        workerLifecycleState: Int,
    ): Int {
        val requestedBindingId = try {
            validateWorkerInstanceId(requireNotNull(expectedWorkerInstanceId))
            ProcessBindingId.parse(requireNotNull(expectedProcessBindingId))
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_INVALID_REQUEST
        }

        if (state != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED) {
            return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ALREADY_STARTED
        }
        if (workerLifecycleState == EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING) {
            return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_WORKER_TERMINATING
        }
        if (bindingState != EmbeddedPythonWorkerProtocol.BINDING_STATE_BOUND) {
            return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_NOT_BOUND
        }
        if (
            expectedWorkerInstanceId != actualWorkerInstanceId ||
            requestedBindingId.value != actualProcessBindingId
        ) {
            return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_IDENTITY_MISMATCH
        }

        generation += 1L
        sessionId = "siftalpha-worker-smoke-${UUID.randomUUID()}"
        state = EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_PREPARING
        return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ACCEPTED
    }

    @Synchronized
    fun markStarting() {
        if (state == EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_PREPARING) {
            state = EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_STARTING
        }
    }

    @Synchronized
    fun markRunning() {
        if (state == EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_STARTING) {
            state = EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_RUNNING
        }
    }

    @Synchronized
    fun completeSuccess(
        exitCode: Int,
        stdout: String,
        stderr: String,
        pythonPid: Int,
    ) {
        if (isTerminal()) return
        this.exitCode = exitCode
        this.stdout = boundOutput(stdout)
        this.stderr = boundOutput(stderr)
        this.pythonPid = pythonPid
        state = EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_SUCCEEDED
    }

    @Synchronized
    fun completeFailure(
        exitCode: Int?,
        stdout: String,
        stderr: String,
    ) {
        if (isTerminal()) return
        this.exitCode = exitCode
        this.stdout = boundOutput(stdout)
        this.stderr = boundOutput(stderr)
        state = EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_FAILED
    }

    @Synchronized
    fun snapshot(): EmbeddedPythonWorkerCpythonSmokeSnapshot =
        EmbeddedPythonWorkerCpythonSmokeSnapshot(
            state = state,
            hasExitCode = exitCode != null,
            exitCode = exitCode ?: -1,
            stdout = stdout,
            stderr = stderr,
            pythonPid = pythonPid,
            sessionId = sessionId,
            generation = generation,
        )

    private fun isTerminal(): Boolean =
        state == EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_SUCCEEDED ||
            state == EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_FAILED

    private companion object {
        private const val MAX_WORKER_INSTANCE_ID_UTF8_BYTES = 256
        private const val MAX_OUTPUT_CHARS = 64 * 1024

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

        private fun boundOutput(value: String): String = value.take(MAX_OUTPUT_CHARS)
    }
}

object EmbeddedPythonWorkerCpythonSmokeEvidence {
    const val PASS_MARKER = "SIFTALPHA_ALPHA44_WORKER_CPYTHON_SMOKE=PASS"
    const val PID_MARKER = "SIFTALPHA_ALPHA44_WORKER_CPYTHON_PID="
    const val STDLIB_JSON_MARKER = "SIFTALPHA_ALPHA44_WORKER_STDLIB_JSON="
    const val EXPECTED_STDLIB_JSON = "{\"alpha44\":\"worker-smoke\",\"ok\":true}"

    data class ValidEvidence(val pythonPid: Int)

    fun parse(stdout: String): ValidEvidence? {
        val lines = stdout.lineSequence().map { it.trim() }.toList()
        if (PASS_MARKER !in lines) return null

        val pidLine = lines.firstOrNull { it.startsWith(PID_MARKER) } ?: return null
        val pid = pidLine.removePrefix(PID_MARKER).toIntOrNull() ?: return null
        if (pid <= 0) return null

        val jsonLine = lines.firstOrNull { it.startsWith(STDLIB_JSON_MARKER) } ?: return null
        if (jsonLine.removePrefix(STDLIB_JSON_MARKER) != EXPECTED_STDLIB_JSON) return null
        return ValidEvidence(pythonPid = pid)
    }
}
