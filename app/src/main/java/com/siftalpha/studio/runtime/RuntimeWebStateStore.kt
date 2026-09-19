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
        val source: RuntimeWebCandidateSource = RuntimeWebCandidateSource.UNKNOWN,
        val verifiedUrl: String? = null,
        val verifiedAtEpochMs: Long = 0L,
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(projectKey: String): Snapshot = Snapshot(
        candidateUrl = prefs.getString(key(projectKey, "url"), null),
        framework = prefs.getString(key(projectKey, "framework"), null),
        detectedAtEpochMs = prefs.getLong(key(projectKey, "detected_at"), 0L),
        source = prefs.getString(key(projectKey, "source"), null)
            ?.let { raw -> runCatching { RuntimeWebCandidateSource.valueOf(raw) }.getOrDefault(RuntimeWebCandidateSource.UNKNOWN) }
            ?: RuntimeWebCandidateSource.UNKNOWN,
        verifiedUrl = prefs.getString(key(projectKey, "verified_url"), null),
        verifiedAtEpochMs = prefs.getLong(key(projectKey, "verified_at"), 0L),
    )

    fun rememberCandidateUrl(
        projectKey: String,
        url: String,
        framework: String?,
        source: RuntimeWebCandidateSource = RuntimeWebCandidateSource.EXPLICIT,
    ) {
        val previousUrl = prefs.getString(key(projectKey, "url"), null)
        val editor = prefs.edit()
            .putString(key(projectKey, "url"), url)
            .putString(key(projectKey, "framework"), framework)
            .putString(key(projectKey, "source"), source.name)
            .putLong(key(projectKey, "detected_at"), System.currentTimeMillis())
        if (previousUrl != url) {
            editor
                .remove(key(projectKey, "verified_url"))
                .remove(key(projectKey, "verified_at"))
        }
        editor.apply()
    }

    fun rememberVerifiedUrl(projectKey: String, url: String) {
        prefs.edit()
            .putString(key(projectKey, "verified_url"), url)
            .putLong(key(projectKey, "verified_at"), System.currentTimeMillis())
            .apply()
    }

    fun clearIfOutOfScope(projectKey: String, webCapabilityEnabled: Boolean): Boolean {
        val current = snapshot(projectKey)
        if (
            current.candidateUrl != null &&
            RuntimeWebDiscoveryScopePolicy.shouldClearPersistedCandidate(
                webCapabilityEnabled = webCapabilityEnabled,
                source = current.source,
            )
        ) {
            clear(projectKey)
            return true
        }
        return false
    }

    fun clear(projectKey: String) {
        prefs.edit()
            .remove(key(projectKey, "url"))
            .remove(key(projectKey, "framework"))
            .remove(key(projectKey, "source"))
            .remove(key(projectKey, "detected_at"))
            .remove(key(projectKey, "verified_url"))
            .remove(key(projectKey, "verified_at"))
            .apply()
    }

    private fun key(projectKey: String, suffix: String): String =
        "${projectKey.length}:$projectKey:$suffix"

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_web_state_v1"
    }
}
