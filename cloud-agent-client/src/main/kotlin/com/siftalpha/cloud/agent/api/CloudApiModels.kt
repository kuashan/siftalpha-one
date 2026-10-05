package com.siftalpha.cloud.agent.api

data class CloudHealth(
    val status: String,
    val agent: String?,
    val docker: String?,
    val wireguardBinding: String?,
)

data class CloudLogs(
    val remoteProjectId: String,
    val lines: List<String>,
    val bytes: Long,
    val truncated: Boolean,
)

