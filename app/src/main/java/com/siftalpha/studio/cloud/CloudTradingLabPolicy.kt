package com.siftalpha.studio.cloud

import android.net.Uri
import com.siftalpha.cloud.core.CloudActionFacts
import com.siftalpha.cloud.core.CloudActionDecision
import com.siftalpha.cloud.core.CloudProjectActionPolicy
import com.siftalpha.cloud.core.CloudWebEndpoint

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

    fun validateRemoteWebUrl(url: String): Uri? = runCatching {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        val port = uri.port.takeIf { it >= 0 } ?: when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> return@runCatching null
        }
        CloudWebEndpoint(scheme, host, port, uri.path.orEmpty().ifBlank { "/" })
        uri
    }.getOrNull()

    fun remoteWebUrl(endpoint: CloudWebEndpoint): String = endpoint.url()
}
