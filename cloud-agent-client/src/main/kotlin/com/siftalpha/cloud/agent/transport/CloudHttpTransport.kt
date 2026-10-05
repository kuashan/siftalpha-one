package com.siftalpha.cloud.agent.transport

data class CloudHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
) {
    override fun toString(): String {
        val safeHeaders = headers.mapValues { (name, value) ->
            if (name.equals("Authorization", ignoreCase = true)) "<redacted>" else value
        }
        return "CloudHttpRequest(method=$method, url=$url, headers=$safeHeaders, body=${body?.let { "<redacted>" }})"
    }
}

data class CloudHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
    val elapsedMillis: Long,
)

enum class CloudTransportFailure {
    UNREACHABLE,
    TIMEOUT,
    RESPONSE_TOO_LARGE,
}

class CloudTransportException(
    val failure: CloudTransportFailure,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

interface CloudHttpTransport {
    fun execute(request: CloudHttpRequest, timeoutMillis: Long, maxResponseBytes: Int): CloudHttpResponse
}

