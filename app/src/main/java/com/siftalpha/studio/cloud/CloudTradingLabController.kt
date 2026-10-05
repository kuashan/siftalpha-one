package com.siftalpha.studio.cloud

import com.siftalpha.cloud.agent.CloudAgentException
import com.siftalpha.cloud.agent.SiftAlphaCloudAgentClient
import com.siftalpha.cloud.agent.CloudOperationPoller
import com.siftalpha.cloud.agent.CloudPollingConfig
import com.siftalpha.cloud.agent.api.CloudLogs
import com.siftalpha.cloud.core.CloudError
import com.siftalpha.cloud.core.CloudErrorCode
import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudOperationStatus
import com.siftalpha.cloud.core.CloudProject
import com.siftalpha.cloud.core.CloudProjectStatus
import com.siftalpha.cloud.core.CloudResources

interface CloudTradingLabApi {
    fun listProjects(): List<CloudProject>
    fun getStatus(projectId: String): CloudProjectStatus
    fun resources(projectId: String): CloudResources
    fun logs(projectId: String, tail: Int): CloudLogs
    fun prepare(projectId: String): CloudOperation
    fun start(projectId: String): CloudOperation
    fun stop(projectId: String): CloudOperation
    fun getOperation(operationId: String): CloudOperation
}

class CloudTradingLabClientAdapter(private val client: SiftAlphaCloudAgentClient) : CloudTradingLabApi {
    override fun listProjects() = client.listProjects()
    override fun getStatus(projectId: String) = client.getStatus(projectId)
    override fun resources(projectId: String) = client.resources(projectId)
    override fun logs(projectId: String, tail: Int) = client.logs(projectId, tail)
    override fun prepare(projectId: String) = client.prepare(projectId)
    override fun start(projectId: String) = client.start(projectId)
    override fun stop(projectId: String) = client.stop(projectId)
    override fun getOperation(operationId: String) = client.getOperation(operationId)
}

class CloudTradingLabController(
    private val api: CloudTradingLabApi,
    private val pollOperation: (String) -> CloudOperation = { operationId ->
        CloudOperationPoller(
            operationLookup = api::getOperation,
            clock = object : com.siftalpha.cloud.agent.CloudClock {
                override fun nowMillis(): Long = System.currentTimeMillis()
            },
            delay = object : com.siftalpha.cloud.agent.CloudDelay {
                override fun sleep(millis: Long) = Thread.sleep(millis)
            },
        ).poll(operationId, CloudPollingConfig(intervalMillis = 500, maxDurationMillis = 120_000))
    },
) {
    fun refresh(): CloudTradingLabUiState = try {
        val project = api.listProjects().firstOrNull { it.remoteProjectId == FREQTRADE_PROJECT_ID }
            ?: return failure(CloudError(CloudErrorCode.PROJECT_NOT_FOUND, "Freqtrade project is not registered"))
        val status = api.getStatus(project.remoteProjectId)
        val resources = api.resources(project.remoteProjectId)
        CloudTradingLabUiState(project = project, status = status, resources = resources)
    } catch (error: CloudAgentException) {
        failure(error.error)
    } catch (error: Exception) {
        failure(CloudError(CloudErrorCode.CLOUD_SERVER_UNREACHABLE, "Cloud server is unreachable"))
    }

    fun loadLogs(state: CloudTradingLabUiState, tail: Int = DEFAULT_LOG_TAIL): CloudTradingLabUiState {
        val projectId = state.project?.remoteProjectId ?: return state
        return try {
            state.copy(logs = api.logs(projectId, CloudTradingLabPolicy.boundedLogTail(tail)), error = null)
        } catch (error: CloudAgentException) {
            state.copy(error = error.error)
        }
    }

    fun loadResources(state: CloudTradingLabUiState): CloudTradingLabUiState {
        val projectId = state.project?.remoteProjectId ?: return state
        return try {
            state.copy(resources = api.resources(projectId), error = null)
        } catch (error: CloudAgentException) {
            state.copy(error = error.error)
        }
    }

    fun prepare(state: CloudTradingLabUiState) = runOperation(state) { api.prepare(it) }
    fun start(state: CloudTradingLabUiState) = runOperation(state) { api.start(it) }
    fun stop(state: CloudTradingLabUiState) = runOperation(state) { api.stop(it) }

    private fun runOperation(state: CloudTradingLabUiState, submit: (String) -> CloudOperation): CloudTradingLabUiState {
        val projectId = state.project?.remoteProjectId ?: return state
        return try {
            val submitted = submit(projectId)
            val terminal = pollOperation(submitted.operationId)
            if (terminal.state != CloudOperationStatus.SUCCEEDED) {
                state.copy(operation = terminal, error = CloudError(CloudErrorCode.RUNTIME_ERROR, terminal.failureReason ?: "Cloud operation failed"))
            } else {
                refresh().copy(operation = terminal)
            }
        } catch (error: CloudAgentException) {
            state.copy(error = error.error)
        } catch (error: Exception) {
            state.copy(error = CloudError(CloudErrorCode.UNKNOWN, "Cloud operation failed"))
        }
    }

    private fun failure(error: CloudError) = CloudTradingLabUiState(error = error)

    companion object {
        const val FREQTRADE_PROJECT_ID = "freqtrade"
        const val DEFAULT_LOG_TAIL = 200
    }
}
