package com.siftalpha.cloud.core

import java.time.Instant

data class CloudResult(
    val remoteProjectId: String,
    val runtimeState: CloudRuntimeState,
    val environmentState: CloudEnvironmentState,
    val operationId: String? = null,
    val webEndpoint: String? = null,
    val artifacts: List<CloudArtifact> = emptyList(),
    val summary: String? = null,
    val errorCode: CloudErrorCode? = null,
    val errorMessage: String? = null,
    val updatedAt: Instant? = null,
)

data class CloudArtifact(
    val name: String,
    val type: String,
    val url: String? = null,
    val size: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
)
