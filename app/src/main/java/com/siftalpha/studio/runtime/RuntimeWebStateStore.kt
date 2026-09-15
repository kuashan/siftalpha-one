package com.siftalpha.studio.runtime

import android.content.Context

/**
 * App-private persistence for the latest Runtime Web URL candidate.
 *
 * The stored value is a candidate only. Android-side endpoint probing decides whether it is
 * reachable, and ProjectUiSnapshot keeps that Web fact separate from the process lifecycle.
 */
class RuntimeWebStateStore(context: Context) {

    data class Snapshot(
        val candidateUrl: String?,
        val framework: String?,
        val detectedAtEpochMs: Long,
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(projectKey: String): Snapshot = Snapshot(
        candidateUrl = prefs.getString(key(projectKey, "url"), null),
        framework = prefs.getString(key(projectKey, "framework"), null),
        detectedAtEpochMs = prefs.getLong(key(projectKey, "detected_at"), 0L),
    )

    fun rememberCandidateUrl(projectKey: String, url: String, framework: String?) {
        prefs.edit()
            .putString(key(projectKey, "url"), url)
            .putString(key(projectKey, "framework"), framework)
            .putLong(key(projectKey, "detected_at"), System.currentTimeMillis())
            .apply()
    }

    fun clear(projectKey: String) {
        prefs.edit()
            .remove(key(projectKey, "url"))
            .remove(key(projectKey, "framework"))
            .remove(key(projectKey, "detected_at"))
            .apply()
    }

    private fun key(projectKey: String, suffix: String): String =
        "${projectKey.length}:$projectKey:$suffix"

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_web_state_v1"
    }
}
