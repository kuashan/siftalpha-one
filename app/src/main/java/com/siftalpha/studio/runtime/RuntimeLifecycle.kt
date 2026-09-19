package com.siftalpha.studio.runtime

import android.content.Context
import com.siftalpha.studio.R

/**
 * Project-level lifecycle states shown by Runtime Center.
 *
 * This is intentionally separate from the low-level RuntimeState parsed from Termux output:
 * the presentation state can say "Recovering" while the app is reconciling a persisted active
 * process with a fresh status command.
 */
enum class RuntimeLifecycleState {
    ENVIRONMENT_NOT_PREPARED,
    PREPARING,
    READY_TO_RUN,
    DETECTING,
    STARTING,
    CHECKING,
    STOPPING,
    CLEANING,
    NEEDS_CONFIGURATION,
    RUNNING,
    STOPPED,
    RUN_FAILED,
    RECOVERING;

    fun uiLabel(context: Context): String = context.getString(
        when (this) {
            ENVIRONMENT_NOT_PREPARED -> R.string.runtime_lifecycle_environment_not_prepared
            PREPARING -> R.string.runtime_lifecycle_preparing
            READY_TO_RUN -> R.string.runtime_lifecycle_ready_to_run
            DETECTING -> R.string.runtime_lifecycle_detecting
            STARTING -> R.string.runtime_lifecycle_starting
            CHECKING -> R.string.runtime_lifecycle_checking
            STOPPING -> R.string.runtime_lifecycle_stopping
            CLEANING -> R.string.runtime_lifecycle_cleaning
            NEEDS_CONFIGURATION -> R.string.runtime_lifecycle_needs_configuration
            RUNNING -> R.string.runtime_lifecycle_running
            STOPPED -> R.string.runtime_lifecycle_stopped
            RUN_FAILED -> R.string.runtime_lifecycle_run_failed
            RECOVERING -> R.string.runtime_lifecycle_recovering
        },
    )
}

enum class RuntimeLifecycleOperation {
    NONE,
    PREPARE,
    START,
    STOP,
    STATUS,
    LOGS,
    CLEAN,
}

object RuntimeLifecycleResolver {
    fun resolve(
        environmentReady: Boolean?,
        runtimeState: RuntimeState,
        operation: RuntimeLifecycleOperation = RuntimeLifecycleOperation.NONE,
        configurationRequired: Boolean = false,
        processActive: Boolean = false,
        recoveryInProgress: Boolean = false,
    ): RuntimeLifecycleState {
        // A user-issued operation has priority over a stale recovery marker. Recovery is only
        // shown while no current operation is accepted for the project.
        if (operation == RuntimeLifecycleOperation.NONE && recoveryInProgress) {
            return RuntimeLifecycleState.RECOVERING
        }

        when (operation) {
            RuntimeLifecycleOperation.PREPARE -> return RuntimeLifecycleState.PREPARING
            RuntimeLifecycleOperation.START -> return RuntimeLifecycleState.STARTING
            RuntimeLifecycleOperation.STATUS,
            RuntimeLifecycleOperation.LOGS,
            -> return RuntimeLifecycleState.CHECKING
            RuntimeLifecycleOperation.STOP -> return RuntimeLifecycleState.STOPPING
            RuntimeLifecycleOperation.CLEAN -> return RuntimeLifecycleState.CLEANING
            RuntimeLifecycleOperation.NONE -> Unit
        }

        // Internal preparation is app-owned and does not have an External Pending executionId.
        // Its RuntimeState is therefore authoritative while the environment is still not READY.
        if (runtimeState == RuntimeState.PREPARING) return RuntimeLifecycleState.PREPARING
        if (runtimeState == RuntimeState.STARTING) return RuntimeLifecycleState.STARTING

        if (environmentReady != true) return RuntimeLifecycleState.ENVIRONMENT_NOT_PREPARED
        if (configurationRequired) return RuntimeLifecycleState.NEEDS_CONFIGURATION

        return when {
            runtimeState == RuntimeState.RUNNING || processActive ->
                RuntimeLifecycleState.RUNNING
            runtimeState == RuntimeState.PREPARING ||
            runtimeState == RuntimeState.STARTING ->
                if (runtimeState == RuntimeState.STARTING) {
                    RuntimeLifecycleState.STARTING
                } else {
                    RuntimeLifecycleState.PREPARING
                }
            runtimeState == RuntimeState.EXITED_ERROR ||
                runtimeState == RuntimeState.ENVIRONMENT_ERROR ->
                RuntimeLifecycleState.RUN_FAILED
            runtimeState == RuntimeState.EXITED_SUCCESS ||
                runtimeState == RuntimeState.STOPPED_BY_USER ->
                RuntimeLifecycleState.STOPPED
            else -> RuntimeLifecycleState.READY_TO_RUN
        }
    }
}
