package com.siftalpha.studio.siftalphax

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The deliberately small state model used by the experimental single-active-session PoC. */
enum class EmbeddedPythonState {
    IDLE,
    STARTING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    STOPPED,
}

enum class EmbeddedPythonRuntimePhase {
    IDLE,
    SESSION_CREATED,
    PROJECT_SPEC_VALIDATE_BEGIN,
    PROJECT_SPEC_VALIDATED,
    WORKER_ENTERED,
    RUNTIME_INIT_BEGIN,
    CPYTHON_READY,
    GIL_ACQUIRE_BEGIN,
    GIL_ACQUIRED,
    THREAD_STATE_READY,
    RUNNING,
    PYTHON_EXEC_BEGIN,
    PYTHON_EXEC_END,
    GIL_RELEASE_BEGIN,
    GIL_RELEASED,
    TERMINAL,
    WORKER_EXIT,
}

enum class EmbeddedPythonStopPhase {
    IDLE,
    STOP_REQUEST_RECEIVED,
    STOP_TARGET_FOUND,
    STOP_THREAD_STATE_ATTACH_BEGIN,
    STOP_THREAD_STATE_ATTACHED,
    STOP_INTERRUPT_BEGIN,
    STOP_INTERRUPT_RESULT_0,
    STOP_INTERRUPT_RESULT_1,
    STOP_INTERRUPT_RESULT_GT1,
    STOP_REQUEST_RETURNED,
}

enum class EmbeddedPythonStopResult {
    NONE,
    REQUEST_ACCEPTED,
    INTERRUPT_DELIVERED,
    TARGET_NOT_FOUND,
    MULTIPLE_TARGETS,
    RUNTIME_FINALIZING,
    DISPATCH_FAILED,
}

data class EmbeddedPythonSnapshot(
    val engine: InternalPythonBackend = InternalPythonBackend.CPYTHON,
    val sessionId: String = "",
    val projectIdentity: String = "",
    val executionRoot: String = "",
    val entrypoint: String = "",
    val workingDirectory: String = "",
    val generation: Long = 0L,
    val state: EmbeddedPythonState = EmbeddedPythonState.IDLE,
    val runtimePhase: EmbeddedPythonRuntimePhase = EmbeddedPythonRuntimePhase.IDLE,
    val stopPhase: EmbeddedPythonStopPhase = EmbeddedPythonStopPhase.IDLE,
    val stopResult: EmbeddedPythonStopResult = EmbeddedPythonStopResult.NONE,
    val startedAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
)

object EmbeddedPythonSnapshotParser {
    fun parse(raw: String): EmbeddedPythonSnapshot {
        val json = Json.parseToJsonElement(raw).jsonObject
        val stateName = json.requiredString("state")
        val state = EmbeddedPythonState.entries.firstOrNull { it.name == stateName }
            ?: error("Unknown embedded Python state: $stateName")
        val runtimePhaseName = json.stringOrEmpty("runtimePhase")
        val runtimePhase = if (runtimePhaseName.isBlank()) {
            EmbeddedPythonRuntimePhase.IDLE
        } else {
            EmbeddedPythonRuntimePhase.entries.firstOrNull { it.name == runtimePhaseName }
                ?: error("Unknown embedded Python runtime phase: $runtimePhaseName")
        }
        val stopPhaseName = json.stringOrEmpty("stopPhase")
        val stopPhase = if (stopPhaseName.isBlank()) {
            EmbeddedPythonStopPhase.IDLE
        } else {
            EmbeddedPythonStopPhase.entries.firstOrNull { it.name == stopPhaseName }
                ?: error("Unknown embedded Python stop phase: $stopPhaseName")
        }
        val stopResultName = json.stringOrEmpty("stopResult")
        val stopResult = if (stopResultName.isBlank()) {
            EmbeddedPythonStopResult.NONE
        } else {
            EmbeddedPythonStopResult.entries.firstOrNull { it.name == stopResultName }
                ?: error("Unknown embedded Python stop result: $stopResultName")
        }
        return EmbeddedPythonSnapshot(
            sessionId = json.stringOrEmpty("sessionId"),
            projectIdentity = json.stringOrEmpty("projectIdentity"),
            executionRoot = json.stringOrEmpty("executionRoot"),
            entrypoint = json.stringOrEmpty("entrypoint"),
            workingDirectory = json.stringOrEmpty("workingDirectory"),
            generation = json.longOrDefault("generation", 0L),
            state = state,
            runtimePhase = runtimePhase,
            stopPhase = stopPhase,
            stopResult = stopResult,
            startedAtEpochMs = json.nullableLong("startedAtEpochMs"),
            finishedAtEpochMs = json.nullableLong("finishedAtEpochMs"),
            exitCode = json.nullableInt("exitCode"),
            stdout = json.stringOrEmpty("stdout"),
            stderr = json.stringOrEmpty("stderr"),
        )
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull
            ?: error("Missing JSON string field: $key")

    private fun JsonObject.stringOrEmpty(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.longOrDefault(key: String, default: Long): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: default

    private fun JsonObject.nullableLong(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.nullableInt(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
}

object EmbeddedPythonDiagnosticText {
    fun session(snapshot: EmbeddedPythonSnapshot): String = listOf(
        "SIFTALPHA_X_ENGINE=" + snapshot.engine,
        "SIFTALPHA_X_TERMUX=NOT_USED",
        "SIFTALPHA_X_PROOT=" + if (snapshot.engine == InternalPythonBackend.ALPINE) "INTERNAL" else "NOT_USED",
        "SIFTALPHA_X_PROJECT_ID=${snapshot.projectIdentity.ifBlank { "-" }}",
        "SIFTALPHA_X_EXECUTION_ROOT=${snapshot.executionRoot.ifBlank { "-" }}",
        "SIFTALPHA_X_ENTRYPOINT=${snapshot.entrypoint.ifBlank { "-" }}",
        "SIFTALPHA_X_WORKING_DIRECTORY=${snapshot.workingDirectory.ifBlank { "-" }}",
        "SIFTALPHA_X_SESSION_ID=${snapshot.sessionId.ifBlank { "-" }}",
        "SIFTALPHA_X_GENERATION=${snapshot.generation}",
        "SIFTALPHA_X_STATE=${snapshot.state}",
        "SIFTALPHA_X_RUNTIME_PHASE=${snapshot.runtimePhase}",
        "SIFTALPHA_X_STOP_PHASE=${snapshot.stopPhase}",
        "SIFTALPHA_X_STOP_RESULT=${snapshot.stopResult}",
        "exitCode=${snapshot.exitCode ?: "-"}",
    ).joinToString("\n")

    fun copyAll(snapshot: EmbeddedPythonSnapshot): String = buildString {
        append(session(snapshot))
        append("\n\nstdout:\n")
        append(snapshot.stdout)
        append("\n\nstderr:\n")
        append(snapshot.stderr)
    }
}

object EmbeddedPythonStatePolicy {
    fun canStart(state: EmbeddedPythonState): Boolean =
        state == EmbeddedPythonState.IDLE || isTerminal(state)

    fun canStop(state: EmbeddedPythonState): Boolean =
        state == EmbeddedPythonState.STARTING || state == EmbeddedPythonState.RUNNING

    fun isTerminal(state: EmbeddedPythonState): Boolean = when (state) {
        EmbeddedPythonState.SUCCEEDED,
        EmbeddedPythonState.FAILED,
        EmbeddedPythonState.STOPPED,
        -> true

        EmbeddedPythonState.IDLE,
        EmbeddedPythonState.STARTING,
        EmbeddedPythonState.RUNNING,
        -> false
    }
}
