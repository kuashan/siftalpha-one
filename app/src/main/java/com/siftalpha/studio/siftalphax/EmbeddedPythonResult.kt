package com.siftalpha.studio.siftalphax

import org.json.JSONObject

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
        val json = JSONObject(raw)
        val stateName = json.getString("state")
        val state = EmbeddedPythonState.entries.firstOrNull { it.name == stateName }
            ?: error("Unknown embedded Python state: $stateName")
        return EmbeddedPythonSnapshot(
            sessionId = json.optString("sessionId"),
            generation = json.optLong("generation", 0L),
            state = state,
            startedAtEpochMs = json.optNullableLong("startedAtEpochMs"),
            finishedAtEpochMs = json.optNullableLong("finishedAtEpochMs"),
            exitCode = json.optNullableInt("exitCode"),
            stdout = json.optString("stdout"),
            stderr = json.optString("stderr"),
        )
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
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
