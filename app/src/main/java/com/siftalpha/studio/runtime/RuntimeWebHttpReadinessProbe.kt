package com.siftalpha.studio.runtime

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Lightweight HTTP readiness check performed only after M has a loopback URL candidate.
 *
 * Listener ownership remains a separate Runtime fact. This probe answers the later question:
 * "does the local service already speak HTTP well enough for the Browser entry to be truthful?"
 */
object RuntimeWebHttpReadinessProbe {

    fun isReady(
        url: String,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    ): Boolean {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) return false
        val validated = RuntimeWebUrl.extractLocalHttpUrl("SIFTALPHA_WEB_URL=$url") ?: return false
        val uri = runCatching { URI(validated) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val connection = runCatching {
            URL(validated).openConnection() as HttpURLConnection
        }.getOrNull() ?: return false

        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("User-Agent", "SiftAlpha-Web-Readiness/1")
            val code = connection.responseCode
            // Any syntactically valid HTTP response means a Browser target exists. Application
            // health is a separate concern: 5xx still proves that the local Web server is ready
            // enough to open and show its own error page.
            code in 100..599
        } catch (_: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private const val DEFAULT_CONNECT_TIMEOUT_MS = 350
    private const val DEFAULT_READ_TIMEOUT_MS = 500
}
