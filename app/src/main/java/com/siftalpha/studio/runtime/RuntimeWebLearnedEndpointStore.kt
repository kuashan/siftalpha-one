package com.siftalpha.studio.runtime

import android.content.Context
import java.net.URI

data class RuntimeWebLearnedEndpoint(
    val url: String,
    val port: Int,
    val verifiedAtEpochMs: Long,
)

/**
 * Project-scoped cross-run memory for an endpoint that previously crossed both ownership discovery
 * and Android endpoint verification. It is only a hint on the next run and never becomes current
 * execution evidence by itself.
 */
class RuntimeWebLearnedEndpointStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(projectKey: String): RuntimeWebLearnedEndpoint? {
        val url = prefs.getString(key(projectKey, "url"), null) ?: return null
        val port = prefs.getInt(key(projectKey, "port"), -1)
        val verifiedAt = prefs.getLong(key(projectKey, "verified_at"), 0L)
        if (RuntimeWebLearnedEndpointPolicy.port(url) != port || port !in 1..65535) return null
        return RuntimeWebLearnedEndpoint(url, port, verifiedAt)
    }

    fun rememberOwnedVerified(projectKey: String, url: String): RuntimeWebLearnedEndpoint? {
        val port = RuntimeWebLearnedEndpointPolicy.port(url) ?: return null
        read(projectKey)?.takeIf { it.url == url && it.port == port }?.let { return it }
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(key(projectKey, "url"), url)
            .putInt(key(projectKey, "port"), port)
            .putLong(key(projectKey, "verified_at"), now)
            .apply()
        return RuntimeWebLearnedEndpoint(url, port, now)
    }

    fun clear(projectKey: String) {
        prefs.edit()
            .remove(key(projectKey, "url"))
            .remove(key(projectKey, "port"))
            .remove(key(projectKey, "verified_at"))
            .apply()
    }

    private fun key(projectKey: String, suffix: String): String =
        "${projectKey.length}:$projectKey:$suffix"

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_web_learned_endpoint_v1"
    }
}

object RuntimeWebLearnedEndpointPolicy {
    fun port(url: String): Int? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return null
        if (host !in setOf("127.0.0.1", "localhost", "::1")) return null
        return uri.port.takeIf { it in 1..65535 }
    }

    fun canLearn(
        source: RuntimeWebCandidateSource,
        verifiedUrl: String?,
    ): Boolean = source == RuntimeWebCandidateSource.PID_SOCKET &&
        verifiedUrl?.let(::port) != null
}
