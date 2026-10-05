package com.siftalpha.cloud.core

data class CloudActionFacts(
    val environmentState: CloudEnvironmentState,
    val runtimeState: CloudRuntimeState,
    val operation: CloudOperation?,
)

enum class CloudProjectAction {
    PREPARE,
    START,
    STOP,
    RETRY,
    REFRESH,
    NONE,
}

data class CloudActionDecision(
    val action: CloudProjectAction,
    val enabled: Boolean,
    val reason: String? = null,
)

object CloudProjectActionPolicy {
    fun decide(facts: CloudActionFacts): CloudActionDecision {
        when (facts.operation?.state) {
            CloudOperationStatus.PENDING,
            CloudOperationStatus.RUNNING,
            -> return CloudActionDecision(
                action = CloudProjectAction.NONE,
                enabled = false,
                reason = "An operation is already in progress",
            )

            CloudOperationStatus.FAILED,
            CloudOperationStatus.CANCELLED,
            -> return CloudActionDecision(
                action = CloudProjectAction.RETRY,
                enabled = true,
                reason = "The previous operation did not complete",
            )

            CloudOperationStatus.SUCCEEDED,
            null,
            -> Unit
        }

        return when (facts.environmentState) {
            CloudEnvironmentState.NOT_READY -> CloudActionDecision(
                action = CloudProjectAction.PREPARE,
                enabled = true,
                reason = "The cloud environment is not ready",
            )

            CloudEnvironmentState.READY -> when (facts.runtimeState) {
                CloudRuntimeState.STOPPED -> CloudActionDecision(
                    action = CloudProjectAction.START,
                    enabled = true,
                    reason = "The cloud runtime is stopped",
                )

                CloudRuntimeState.RUNNING -> CloudActionDecision(
                    action = CloudProjectAction.STOP,
                    enabled = true,
                    reason = "The cloud runtime is running",
                )

                CloudRuntimeState.READY,
                CloudRuntimeState.EXITED,
                CloudRuntimeState.UNKNOWN,
                -> CloudActionDecision(
                    action = CloudProjectAction.REFRESH,
                    enabled = true,
                    reason = "Refresh the cloud runtime facts",
                )
            }

            CloudEnvironmentState.UNKNOWN -> CloudActionDecision(
                action = CloudProjectAction.REFRESH,
                enabled = true,
                reason = "Refresh the cloud environment facts",
            )
        }
    }
}

