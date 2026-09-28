package com.siftalpha.cloud.core

data class CloudServer(
    val serverId: String,
    val displayName: String,
    val baseUrl: String,
    val connectionState: CloudConnectionState = CloudConnectionState.UNKNOWN,
)
