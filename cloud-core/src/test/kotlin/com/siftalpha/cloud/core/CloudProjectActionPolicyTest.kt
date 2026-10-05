package com.siftalpha.cloud.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProjectActionPolicyTest {
    @Test
    fun `not ready environment offers prepare`() {
        val decision = decide(environment = CloudEnvironmentState.NOT_READY, runtime = CloudRuntimeState.UNKNOWN)

        assertEquals(CloudProjectAction.PREPARE, decision.action)
        assertTrue(decision.enabled)
    }

    @Test
    fun `ready stopped runtime offers start`() {
        val decision = decide(environment = CloudEnvironmentState.READY, runtime = CloudRuntimeState.STOPPED)

        assertEquals(CloudProjectAction.START, decision.action)
        assertTrue(decision.enabled)
    }

    @Test
    fun `running runtime offers stop`() {
        val decision = decide(environment = CloudEnvironmentState.READY, runtime = CloudRuntimeState.RUNNING)

        assertEquals(CloudProjectAction.STOP, decision.action)
        assertTrue(decision.enabled)
    }

    @Test
    fun `pending or running operation disables destructive actions`() {
        listOf(CloudOperationStatus.PENDING, CloudOperationStatus.RUNNING).forEach { operationStatus ->
            val decision = decide(
                environment = CloudEnvironmentState.READY,
                runtime = CloudRuntimeState.RUNNING,
                operation = CloudOperation(
                    operationId = "op-1",
                    projectId = "project-1",
                    action = CloudOperationAction.STOP,
                    state = operationStatus,
                ),
            )

            assertEquals(CloudProjectAction.NONE, decision.action)
            assertFalse(decision.enabled)
        }
    }

    @Test
    fun `failed operation offers retry`() {
        val decision = decide(
            environment = CloudEnvironmentState.READY,
            runtime = CloudRuntimeState.STOPPED,
            operation = CloudOperation(
                operationId = "op-1",
                projectId = "project-1",
                action = CloudOperationAction.START,
                state = CloudOperationStatus.FAILED,
                failureReason = "docker failed",
            ),
        )

        assertEquals(CloudProjectAction.RETRY, decision.action)
        assertTrue(decision.enabled)
    }

    @Test
    fun `unknown facts offer refresh without enabling a destructive action`() {
        val decision = decide(environment = CloudEnvironmentState.UNKNOWN, runtime = CloudRuntimeState.UNKNOWN)

        assertEquals(CloudProjectAction.REFRESH, decision.action)
        assertTrue(decision.enabled)
    }

    private fun decide(
        environment: CloudEnvironmentState,
        runtime: CloudRuntimeState,
        operation: CloudOperation? = null,
    ): CloudActionDecision = CloudProjectActionPolicy.decide(
        CloudActionFacts(
            environmentState = environment,
            runtimeState = runtime,
            operation = operation,
        ),
    )
}

