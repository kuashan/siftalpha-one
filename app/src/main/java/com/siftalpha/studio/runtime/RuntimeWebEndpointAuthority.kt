package com.siftalpha.studio.runtime

import java.net.HttpURLConnection
import java.net.URL

enum class RuntimeWebEndpointClass {
    HTML_UI,
    HTTP_OTHER,
    UNREACHABLE,
}

/**
 * Browser endpoint authority for projects that expose more than one owned listener.
 *
 * Runtime ownership is established before this policy runs. Within that owned set, a real HTML
 * document outranks an API/JSON endpoint. Existing hint order is preserved inside each class.
 */
object RuntimeWebEndpointAuthorityPolicy {
    fun rank(
        ports: Collection<Int>,
        classify: (Int) -> RuntimeWebEndpointClass,
    ): List<Int> {
        val valid = ports.filter { it in 1..65535 }.distinct()
        val classified = valid.map { it to classify(it) }
        return buildList {
            classified.filter { it.second == RuntimeWebEndpointClass.HTML_UI }.forEach { add(it.first) }
            classified.filter { it.second == RuntimeWebEndpointClass.HTTP_OTHER }.forEach { add(it.first) }
            classified.filter { it.second == RuntimeWebEndpointClass.UNREACHABLE }.forEach { add(it.first) }
        }
    }
}

/**
 * Small bounded loopback classifier used only after PID/socket ownership discovery.
 *
 * It never scans ports and never follows redirects. The first response is enough to distinguish a
 * browser document from the common API/JSON root that previously stole endpoint authority.
 */
object RuntimeWebEndpointClassifier {
    fun classifyLoopback(
        port: Int,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): RuntimeWebEndpointClass {
        if (port !in 1..65535 || timeoutMs <= 0) return RuntimeWebEndpointClass.UNREACHABLE
        val connection = runCatching {
            (URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.5")
                setRequestProperty("Connection", "close")
            }
        }.getOrNull() ?: return RuntimeWebEndpointClass.UNREACHABLE

        return try {
            val code = connection.responseCode
            if (code !in 100..599) return RuntimeWebEndpointClass.UNREACHABLE
            val contentType = connection.contentType.orEmpty().lowercase()
            if ("text/html" in contentType || "application/xhtml+xml" in contentType) {
                return RuntimeWebEndpointClass.HTML_UI
            }
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = runCatching {
                stream?.use { input ->
                    val bytes = ByteArray(MAX_BODY_BYTES)
                    val read = input.read(bytes)
                    if (read > 0) String(bytes, 0, read, Charsets.UTF_8) else ""
                }.orEmpty()
            }.getOrDefault("")
            val normalized = body.trimStart().lowercase()
            if (
                normalized.startsWith("<!doctype html") ||
                normalized.startsWith("<html") ||
                "<html" in normalized.take(1024)
            ) {
                RuntimeWebEndpointClass.HTML_UI
            } else {
                RuntimeWebEndpointClass.HTTP_OTHER
            }
        } catch (_: Throwable) {
            RuntimeWebEndpointClass.UNREACHABLE
        } finally {
            connection.disconnect()
        }
    }

    private const val DEFAULT_TIMEOUT_MS = 650
    private const val MAX_BODY_BYTES = 4096
}
