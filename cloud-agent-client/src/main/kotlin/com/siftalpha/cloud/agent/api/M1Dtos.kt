package com.siftalpha.cloud.agent.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class M1HealthDto(
    val status: String,
    val agent: String? = null,
    val docker: String? = null,
    val wireguardBinding: String? = null,
)

@Serializable
data class M1ProjectDto(
    val projectId: String,
    val environmentState: String,
    val runtimeState: String,
    val image: String? = null,
)

@Serializable
data class M1ProjectsResponseDto(
    val projects: List<M1ProjectDto>,
)

@Serializable
data class M1StatusDto(
    val projectId: String,
    val environmentState: String,
    val runtimeState: String,
    val containerId: String? = null,
    val image: String? = null,
    val exitCode: Int? = null,
    val oomKilled: Boolean? = null,
    val restartCount: Int? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

@Serializable
data class M1LogsDto(
    val projectId: String,
    val lines: List<String>,
    val bytes: Long,
    val truncated: Boolean,
)

@Serializable
data class M1OperationDto(
    val operationId: String,
    val projectId: String,
    val action: String,
    val state: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val exitCode: Int? = null,
    val failureReason: String? = null,
    val result: JsonObject? = null,
)

@Serializable
data class M1ErrorDto(
    val code: String,
    val message: String,
)

@Serializable
data class M1ErrorEnvelopeDto(
    val error: M1ErrorDto,
)
