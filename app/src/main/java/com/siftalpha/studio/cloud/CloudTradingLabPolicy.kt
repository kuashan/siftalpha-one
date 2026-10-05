package com.siftalpha.studio.cloud

import com.siftalpha.cloud.core.CloudActionFacts
import com.siftalpha.cloud.core.CloudActionDecision
import com.siftalpha.cloud.core.CloudProjectActionPolicy
import com.siftalpha.cloud.core.CloudWebEndpoint
import java.net.URI

object CloudTradingLabPolicy {
    fun action(state: CloudTradingLabUiState): CloudActionDecision? {
        val status = state.status ?: return null
        return CloudProjectActionPolicy.decide(
            CloudActionFacts(
                environmentState = status.environmentState,
                runtimeState = status.runtimeState,
                operation = state.operation,
            ),
        )
    }

    fun boundedLogTail(tail: Int): Int = tail.coerceIn(0, 1_000)

    fun validateRemoteWebUrl(url: String): CloudWebEndpoint? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        require(uri.userInfo == null) { "Web endpoint user info is not allowed" }
        require(uri.query == null && uri.fragment == null) { "Web endpoint query or fragment is not allowed" }
        val port = uri.port.takeIf { it >= 0 } ?: when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> return@runCatching null
        }
        CloudWebEndpoint(scheme, host, port, uri.path.orEmpty().ifBlank { "/" })
    }.getOrNull()

    fun remoteWebUrl(endpoint: CloudWebEndpoint): String = endpoint.url()
}
