package com.siftalpha.studio.siftalphax

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The deliberately small state model used by the experimental single-session CPython PoC. */
enum class EmbeddedPythonState {
    IDLE,
    STARTING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    STOPPED,
}

data class EmbeddedPythonSnapshot(
    val sessionId: String = "",
    val generation: Long = 0L,
    val state: EmbeddedPythonState = EmbeddedPythonState.IDLE,
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
        return EmbeddedPythonSnapshot(
            sessionId = json.stringOrEmpty("sessionId"),
            generation = json.longOrDefault("generation", 0L),
            state = state,
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

object EmbeddedPythonStatePolicy {
    fun canStart(state: EmbeddedPythonState): Boolean = state == EmbeddedPythonState.IDLE

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
