package com.siftalpha.cloud.agent

import com.siftalpha.cloud.agent.api.CloudHealth
import com.siftalpha.cloud.agent.api.CloudLogs
import com.siftalpha.cloud.agent.api.M1ErrorDto
import com.siftalpha.cloud.agent.api.M1HealthDto
import com.siftalpha.cloud.agent.api.M1LogsDto
import com.siftalpha.cloud.agent.api.M1OperationDto
import com.siftalpha.cloud.agent.api.M1ProjectDto
import com.siftalpha.cloud.agent.api.M1StatusDto
import com.siftalpha.cloud.core.CloudArtifact
import com.siftalpha.cloud.core.CloudConnectionState
import com.siftalpha.cloud.core.CloudEnvironmentState
import com.siftalpha.cloud.core.CloudError
import com.siftalpha.cloud.core.CloudErrorCode
import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudOperationAction
import com.siftalpha.cloud.core.CloudOperationStatus
import com.siftalpha.cloud.core.CloudProject
import com.siftalpha.cloud.core.CloudProjectStatus
import com.siftalpha.cloud.core.CloudRuntimeState
import java.time.Instant

object CloudDtoMapper {
    fun toCloudHealth(dto: M1HealthDto): CloudHealth {
        requireNonBlank(dto.status, "health.status")
        return CloudHealth(dto.status, dto.agent, dto.docker, dto.wireguardBinding)
    }

    fun toCloudProject(dto: M1ProjectDto, serverId: String): CloudProject {
        requireNonBlank(serverId, "serverId")
        requireNonBlank(dto.projectId, "project.projectId")
        return CloudProject(
            displayName = dto.projectId,
            serverId = serverId,
            remoteProjectId = dto.projectId,
            environmentState = environment(dto.environmentState),
            runtimeState = runtime(dto.runtimeState),
            runtimeKind = dto.image,
        )
    }

    fun toCloudStatus(dto: M1StatusDto): CloudProjectStatus {
        requireNonBlank(dto.projectId, "status.projectId")
        return CloudProjectStatus(
            remoteProjectId = dto.projectId,
            environmentState = environment(dto.environmentState),
            runtimeState = runtime(dto.runtimeState),
            containerId = dto.containerId,
            image = dto.image,
            exitCode = dto.exitCode,
            oomKilled = dto.oomKilled,
            restartCount = dto.restartCount,
            startedAt = instant(dto.startedAt, "status.startedAt"),
            finishedAt = instant(dto.finishedAt, "status.finishedAt"),
        )
    }

    fun toCloudOperation(dto: M1OperationDto): CloudOperation {
        requireNonBlank(dto.operationId, "operation.operationId")
        requireNonBlank(dto.projectId, "operation.projectId")
        return CloudOperation(
            operationId = dto.operationId,
            projectId = dto.projectId,
            action = operationAction(dto.action),
            state = operationStatus(dto.state),
            startedAt = instant(dto.startedAt, "operation.startedAt"),
            finishedAt = instant(dto.finishedAt, "operation.finishedAt"),
            exitCode = dto.exitCode,
            failureReason = dto.failureReason,
        )
    }

    fun toCloudLogs(dto: M1LogsDto): CloudLogs {
        requireNonBlank(dto.projectId, "logs.projectId")
        require(dto.bytes >= 0) { invalid("logs.bytes must be non-negative") }
        return CloudLogs(dto.projectId, dto.lines, dto.bytes, dto.truncated)
    }

    fun toCloudError(code: String, message: String, httpStatus: Int? = null): CloudError = CloudError(
        code = runCatching { CloudErrorCode.valueOf(code) }.getOrDefault(CloudErrorCode.UNKNOWN),
        message = message.ifBlank { "Cloud request failed" },
        httpStatus = httpStatus,
    )

    private fun environment(value: String): CloudEnvironmentState =
        runCatching { CloudEnvironmentState.valueOf(value) }.getOrElse {
            throw invalid("Unknown environmentState: $value")
        }

    private fun runtime(value: String): CloudRuntimeState =
        runCatching { CloudRuntimeState.valueOf(value) }.getOrElse {
            throw invalid("Unknown runtimeState: $value")
        }

    private fun operationAction(value: String): CloudOperationAction =
        runCatching { CloudOperationAction.valueOf(value) }.getOrElse {
            throw invalid("Unknown operation action: $value")
        }

    private fun operationStatus(value: String): CloudOperationStatus =
        runCatching { CloudOperationStatus.valueOf(value) }.getOrElse {
            throw invalid("Unknown operation state: $value")
        }

    private fun instant(value: String?, field: String): Instant? = value?.let {
        runCatching { Instant.parse(it) }.getOrElse {
            throw invalid("Invalid $field timestamp")
        }
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) throw invalid("Missing $field")
    }

    private fun invalid(message: String): CloudAgentException =
        CloudAgentException(CloudError(CloudErrorCode.INVALID_RESPONSE, message))
}

