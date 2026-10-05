package com.siftalpha.studio.cloud

import com.siftalpha.cloud.core.CloudEnvironmentState
import com.siftalpha.cloud.core.CloudProjectAction
import com.siftalpha.cloud.core.CloudProjectStatus
import com.siftalpha.cloud.core.CloudRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTradingLabPolicyTest {
    @Test
    fun test_prepare_is_enabled_only_for_not_ready_environment() {
        val state = CloudTradingLabUiState(
            status = CloudProjectStatus(
                remoteProjectId = "freqtrade",
                environmentState = CloudEnvironmentState.NOT_READY,
                runtimeState = CloudRuntimeState.UNKNOWN,
            ),
        )

        val decision = CloudTradingLabPolicy.action(state)

        assertEquals(CloudProjectAction.PREPARE, decision?.action)
        assertTrue(decision?.enabled == true)
    }

    @Test
    fun test_operation_in_progress_disables_actions() {
        val state = CloudTradingLabUiState(
            status = CloudProjectStatus(
                remoteProjectId = "freqtrade",
                environmentState = CloudEnvironmentState.READY,
                runtimeState = CloudRuntimeState.RUNNING,
            ),
            operation = com.siftalpha.cloud.core.CloudOperation(
                operationId = "op-1",
                projectId = "freqtrade",
                action = com.siftalpha.cloud.core.CloudOperationAction.START,
                state = com.siftalpha.cloud.core.CloudOperationStatus.RUNNING,
            ),
        )

        val decision = CloudTradingLabPolicy.action(state)

        assertEquals(CloudProjectAction.NONE, decision?.action)
        assertFalse(decision?.enabled == true)
    }

    @Test
    fun test_log_tail_is_bounded() {
        assertEquals(0, CloudTradingLabPolicy.boundedLogTail(-10))
        assertEquals(200, CloudTradingLabPolicy.boundedLogTail(200))
        assertEquals(1_000, CloudTradingLabPolicy.boundedLogTail(2_000))
    }
}
