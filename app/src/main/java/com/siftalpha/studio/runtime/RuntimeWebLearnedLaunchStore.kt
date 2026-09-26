package com.siftalpha.studio.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Project-scoped memory of a Web launch contract that has already produced a verified endpoint.
 *
 * DISCOVERED entries are written before launch but are never reused automatically. Only VERIFIED
 * entries may bypass expensive rediscovery on a later run. Endpoint reachability is still probed on
 * every run; this store remembers how to launch, not whether an old URL is still alive.
 */
class RuntimeWebLearnedLaunchStore(context: Context) {

    enum class State {
        DISCOVERED,
        VERIFIED,
    }

    data class Entry(
        val candidate: PythonNativeWebLaunchCandidate,
        val state: State,
        val updatedAtEpochMs: Long,
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(projectKey: String): Entry? {
        val raw = prefs.getString(key(projectKey), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val executable = json.getString("executable")
            if (!PythonCliLaunchResolver.isSafeCommandName(executable)) return@runCatching null
            val argsJson = json.optJSONArray("arguments") ?: JSONArray()
            val arguments = buildList {
                for (index in 0 until argsJson.length()) {
                    add(argsJson.getString(index))
                }
            }
            val evidencePath = json.optString("evidence_path").ifBlank { "learned" }
            val state = State.valueOf(json.getString("state"))
            Entry(
                candidate = PythonNativeWebLaunchCandidate(
                    executableName = executable,
                    arguments = arguments,
                    evidencePath = evidencePath,
                ),
                state = state,
                updatedAtEpochMs = json.optLong("updated_at", 0L),
            )
        }.getOrNull()
    }

    fun readVerified(projectKey: String): PythonNativeWebLaunchCandidate? =
        read(projectKey)?.takeIf { it.state == State.VERIFIED }?.candidate

    fun rememberDiscovered(projectKey: String, candidate: PythonNativeWebLaunchCandidate) {
        write(projectKey, candidate, State.DISCOVERED)
    }

    fun markVerified(projectKey: String): Entry? {
        val current = read(projectKey) ?: return null
        if (current.state == State.VERIFIED) return current
        write(projectKey, current.candidate, State.VERIFIED)
        return read(projectKey)
    }

    fun clear(projectKey: String) {
        prefs.edit().remove(key(projectKey)).apply()
    }

    private fun write(
        projectKey: String,
        candidate: PythonNativeWebLaunchCandidate,
        state: State,
    ) {
        val now = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("executable", candidate.executableName)
            put("arguments", JSONArray(candidate.arguments))
            put("evidence_path", candidate.evidencePath)
            put("state", state.name)
            put("updated_at", now)
        }
        prefs.edit().putString(key(projectKey), json.toString()).apply()
    }

    private fun key(projectKey: String): String =
        projectKey.length.toString() + ":" + projectKey

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_web_learned_launch_v2"
    }
}
