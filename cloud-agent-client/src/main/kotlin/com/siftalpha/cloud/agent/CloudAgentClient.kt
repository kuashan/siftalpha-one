package com.siftalpha.cloud.agent

import com.siftalpha.cloud.agent.api.CloudHealth
import com.siftalpha.cloud.agent.api.CloudLogs
import com.siftalpha.cloud.agent.api.M1Json
import com.siftalpha.cloud.agent.transport.CloudHttpRequest
import com.siftalpha.cloud.agent.transport.CloudHttpResponse
import com.siftalpha.cloud.agent.transport.CloudHttpTransport
import com.siftalpha.cloud.agent.transport.CloudTransportException
import com.siftalpha.cloud.agent.transport.CloudTransportFailure
import com.siftalpha.cloud.core.CloudError
import com.siftalpha.cloud.core.CloudErrorCode
import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudProject
import com.siftalpha.cloud.core.CloudProjectStatus
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException

class SiftAlphaCloudAgentClient(
    private val serverId: String,
    private val credentialStore: CloudCredentialStore,
    private val transport: CloudHttpTransport,
    private val timeoutMillis: Long = 10_000,
    private val maxResponseBytes: Int = 1_048_576,
) {
    init {
        require(serverId.isNotBlank()) { "serverId must not be blank" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
    }

    fun health(): CloudHealth = executeJson(
        path = "/v1/health",
        decode = { CloudDtoMapper.toCloudHealth(M1Json.decodeHealth(it)) },
    )

    fun listProjects(): List<CloudProject> = executeJson(
        path = "/v1/projects",
        decode = { body ->
            M1Json.decodeProjects(body).projects.map { CloudDtoMapper.toCloudProject(it, serverId) }
        },
    )

    fun getProject(projectId: String): CloudProject = executeJson(
        path = "/v1/projects/${encodeSegment(projectId)}",
        decode = { CloudDtoMapper.toCloudProject(M1Json.decodeProject(it), serverId) },
    )

    fun getStatus(projectId: String): CloudProjectStatus = executeJson(
        path = "/v1/projects/${encodeSegment(projectId)}/status",
        decode = { CloudDtoMapper.toCloudStatus(M1Json.decodeStatus(it)) },
    )

    fun prepare(projectId: String): CloudOperation = postOperation(projectId, "prepare")

    fun start(projectId: String): CloudOperation = postOperation(projectId, "start")

    fun stop(projectId: String): CloudOperation = postOperation(projectId, "stop")

    fun logs(projectId: String, tail: Int): CloudLogs {
        if (tail !in 0..1_000) {
            throw CloudAgentException(
                CloudError(CloudErrorCode.INVALID_REQUEST, "tail must be between 0 and 1000"),
            )
        }
        return executeJson(
            path = "/v1/projects/${encodeSegment(projectId)}/logs?tail=$tail",
            decode = { CloudDtoMapper.toCloudLogs(M1Json.decodeLogs(it)) },
        )
    }

    fun getOperation(operationId: String): CloudOperation = executeJson(
        path = "/v1/operations/${encodeSegment(operationId)}",
        decode = { CloudDtoMapper.toCloudOperation(M1Json.decodeOperation(it)) },
    )

    private fun postOperation(projectId: String, action: String): CloudOperation = executeJson(
        method = "POST",
        path = "/v1/projects/${encodeSegment(projectId)}/$action",
        expectedStatuses = setOf(200, 202),
        decode = { CloudDtoMapper.toCloudOperation(M1Json.decodeOperation(it)) },
    )

    private fun <T> executeJson(
        path: String,
        method: String = "GET",
        expectedStatuses: Set<Int> = setOf(200),
        decode: (String) -> T,
    ): T {
        val credential = credentialStore.get(serverId)
            ?: throw CloudAgentException(CloudError(CloudErrorCode.UNAUTHORIZED, "Cloud credential is unavailable"))
        if (credential.serverId != serverId || credential.baseUrl.isBlank() || credential.bearerToken.isBlank()) {
            throw CloudAgentException(CloudError(CloudErrorCode.UNAUTHORIZED, "Cloud credential is invalid"))
        }

        val request = CloudHttpRequest(
            method = method,
            url = joinUrl(credential.baseUrl, path),
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${credential.bearerToken}",
            ),
        )
        val response = executeTransport(request)
        if (response.statusCode !in expectedStatuses) {
            throw mapHttpError(path, response)
        }
        if (response.body.toByteArray(StandardCharsets.UTF_8).size > maxResponseBytes) {
            throw CloudAgentException(CloudError(CloudErrorCode.INVALID_RESPONSE, "Cloud response exceeded the configured limit"))
        }
        return try {
            decode(response.body)
        } catch (error: CloudAgentException) {
            throw error
        } catch (error: SerializationException) {
            throw CloudAgentException(CloudError(CloudErrorCode.INVALID_RESPONSE, "Cloud response JSON is invalid"), error)
        } catch (error: IllegalArgumentException) {
            throw CloudAgentException(CloudError(CloudErrorCode.INVALID_RESPONSE, "Cloud response is invalid"), error)
        }
    }

    private fun executeTransport(request: CloudHttpRequest): CloudHttpResponse = try {
        transport.execute(request, timeoutMillis, maxResponseBytes)
    } catch (error: CloudTransportException) {
        throw mapTransportError(error)
    } catch (error: SocketTimeoutException) {
        throw CloudAgentException(CloudError(CloudErrorCode.TIMEOUT, "Cloud request timed out"), error)
    } catch (error: IOException) {
        throw CloudAgentException(CloudError(CloudErrorCode.CLOUD_SERVER_UNREACHABLE, "Cloud server is unreachable"), error)
    }

    private fun mapTransportError(error: CloudTransportException): CloudAgentException {
        val code = when (error.failure) {
            CloudTransportFailure.UNREACHABLE -> CloudErrorCode.CLOUD_SERVER_UNREACHABLE
            CloudTransportFailure.TIMEOUT -> CloudErrorCode.TIMEOUT
            CloudTransportFailure.RESPONSE_TOO_LARGE -> CloudErrorCode.INVALID_RESPONSE
        }
        return CloudAgentException(CloudError(code, error.message ?: "Cloud transport failed"), error)
    }

    private fun mapHttpError(path: String, response: CloudHttpResponse): CloudAgentException {
        val fallbackCode = when {
            response.statusCode == 401 -> CloudErrorCode.UNAUTHORIZED
            response.statusCode == 404 && path.startsWith("/v1/operations/") -> CloudErrorCode.INVALID_REQUEST
            response.statusCode == 404 -> CloudErrorCode.PROJECT_NOT_FOUND
            response.statusCode == 409 -> CloudErrorCode.OPERATION_CONFLICT
            response.statusCode == 422 -> CloudErrorCode.INVALID_REQUEST
            response.statusCode >= 500 -> CloudErrorCode.DOCKER_ERROR
            else -> CloudErrorCode.UNKNOWN
        }
        return try {
            val envelope = M1Json.decodeError(response.body)
            val mapped = CloudDtoMapper.toCloudError(envelope.error.code, envelope.error.message, response.statusCode)
            CloudAgentException(
                if (mapped.code == CloudErrorCode.UNKNOWN) mapped.copy(code = fallbackCode) else mapped,
            )
        } catch (error: Exception) {
            CloudAgentException(
                CloudError(fallbackCode, "Cloud request failed", response.statusCode),
                error,
            )
        }
    }

    private fun joinUrl(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun encodeSegment(value: String): String {
        if (value.isBlank()) {
            throw CloudAgentException(CloudError(CloudErrorCode.INVALID_REQUEST, "Path identifier must not be blank"))
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}
