package com.siftalpha.studio.cloud

import com.siftalpha.cloud.core.CloudEnvironmentState
import com.siftalpha.cloud.agent.api.CloudLogs
import com.siftalpha.cloud.core.CloudError
import com.siftalpha.cloud.core.CloudErrorCode
import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudOperationAction
import com.siftalpha.cloud.core.CloudOperationStatus
import com.siftalpha.cloud.core.CloudProject
import com.siftalpha.cloud.core.CloudProjectStatus
import com.siftalpha.cloud.core.CloudResources
import com.siftalpha.cloud.core.CloudRuntimeState
import com.siftalpha.cloud.core.CloudRuntimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CloudTradingLabControllerTest {
    @Test
    fun test_initial_project_shows_prepare_not_running() {
        val api = FakeApi(environment = CloudEnvironmentState.NOT_READY, runtime = CloudRuntimeState.UNKNOWN)
        val state = CloudTradingLabController(api).refresh()

        assertEquals("freqtrade", state.project?.remoteProjectId)
        assertEquals(com.siftalpha.cloud.core.CloudProjectAction.PREPARE, CloudTradingLabPolicy.action(state)?.action)
    }

    @Test
    fun test_start_polls_then_refreshes_status() {
        val api = FakeApi(environment = CloudEnvironmentState.READY, runtime = CloudRuntimeState.STOPPED)
        val initial = CloudTradingLabController(api).refresh()
        val controller = CloudTradingLabController(api) { api.getOperation(it) }

        val state = controller.start(initial)

        assertEquals(CloudRuntimeState.RUNNING, state.status?.runtimeState)
        assertEquals(CloudOperationStatus.SUCCEEDED, state.operation?.state)
        assertTrue(api.actions.contains("start"))
    }

    @Test
    fun test_stop_polls_then_refreshes_status() {
        val api = FakeApi(environment = CloudEnvironmentState.READY, runtime = CloudRuntimeState.RUNNING)
        val controller = CloudTradingLabController(api) { api.getOperation(it) }
        val state = controller.stop(controller.refresh())

        assertEquals(CloudRuntimeState.STOPPED, state.status?.runtimeState)
        assertEquals(CloudOperationStatus.SUCCEEDED, state.operation?.state)
    }

    @Test
    fun test_operation_failure_does_not_fake_stopped() {
        val api = FakeApi(environment = CloudEnvironmentState.READY, runtime = CloudRuntimeState.RUNNING, operationState = CloudOperationStatus.FAILED)
        val controller = CloudTradingLabController(api) { api.getOperation(it) }
        val state = controller.stop(controller.refresh())

        assertEquals(CloudRuntimeState.RUNNING, state.status?.runtimeState)
        assertEquals(CloudOperationStatus.FAILED, state.operation?.state)
        assertEquals(CloudErrorCode.RUNTIME_ERROR, state.error?.code)
    }

    @Test
    fun test_unreachable_server_is_explicit_error() {
        val api = FakeApi(failure = CloudError(CloudErrorCode.CLOUD_SERVER_UNREACHABLE, "offline"))

        val state = CloudTradingLabController(api).refresh()

        assertEquals(CloudErrorCode.CLOUD_SERVER_UNREACHABLE, state.error?.code)
        assertTrue(state.status == null)
    }

    @Test
    fun test_logs_default_to_200_and_cap_at_1000() {
        val api = FakeApi(environment = CloudEnvironmentState.READY, runtime = CloudRuntimeState.STOPPED)
        val controller = CloudTradingLabController(api)
        val state = controller.refresh()

        controller.loadLogs(state)
        controller.loadLogs(state, tail = 5_000)

        assertEquals(listOf(200, 1_000), api.logTails)
    }

    @Test
    fun test_missing_resources_show_no_running_instance() {
        val api = FakeApi(environment = CloudEnvironmentState.NOT_READY, runtime = CloudRuntimeState.UNKNOWN)

        val state = CloudTradingLabController(api).refresh()

        assertTrue(state.runtimeUnits.isEmpty())
    }

    private class FakeApi(
        private val environment: CloudEnvironmentState = CloudEnvironmentState.READY,
        private var runtime: CloudRuntimeState = CloudRuntimeState.STOPPED,
        private val operationState: CloudOperationStatus = CloudOperationStatus.SUCCEEDED,
        private val failure: CloudError? = null,
    ) : CloudTradingLabApi {
        val actions = mutableListOf<String>()
        val logTails = mutableListOf<Int>()

        override fun listProjects() = listOf(
            CloudProject(
                displayName = "Freqtrade",
                serverId = "server-1",
                remoteProjectId = "freqtrade",
                environmentState = environment,
                runtimeState = runtime,
            ),
        ).also { failure?.let { throw com.siftalpha.cloud.agent.CloudAgentException(it) } }

        override fun getStatus(projectId: String) = CloudProjectStatus(projectId, environment, runtime)

        override fun resources(projectId: String) = CloudResources(projectId, emptyList())

        override fun logs(projectId: String, tail: Int) = CloudLogs(projectId, emptyList(), 0, false).also { logTails += tail }

        override fun prepare(projectId: String) = submit("prepare")

        override fun start(projectId: String) = submit("start")

        override fun stop(projectId: String) = submit("stop")

        override fun getOperation(operationId: String): CloudOperation {
            if (operationState == CloudOperationStatus.SUCCEEDED) {
                if (actions.lastOrNull() == "start") runtime = CloudRuntimeState.RUNNING
                if (actions.lastOrNull() == "stop") runtime = CloudRuntimeState.STOPPED
            }
            return CloudOperation(operationId, "freqtrade", CloudOperationAction.START, operationState)
        }

        private fun submit(action: String): CloudOperation {
            actions += action
            return CloudOperation("op-1", "freqtrade", CloudOperationAction.START, CloudOperationStatus.PENDING)
        }
    }
}
