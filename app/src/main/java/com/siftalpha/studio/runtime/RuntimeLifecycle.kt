package com.siftalpha.studio.runtime

import android.content.Context
import com.siftalpha.core.lifecycle.ProjectLifecycleOperation
import com.siftalpha.core.lifecycle.RuntimeExecutionState
import com.siftalpha.core.lifecycle.RuntimeLifecyclePolicy
import com.siftalpha.studio.R

/**
 * Android（安卓） presentation-compatible project lifecycle states.
 *
 * Lifecycle decision policy now lives in SiftAlpha Core（跨平台核心）. UI（界面） labels remain
 * Android（安卓） platform responsibility.
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

/**
 * Android（安卓） compatibility facade over the platform-independent Core（核心） lifecycle policy.
 *
 * Existing Android callers keep their stable types while all lifecycle decisions are delegated
 * to :core.
 */
object RuntimeLifecycleResolver {
    fun resolve(
        environmentReady: Boolean?,
        runtimeState: RuntimeState,
        operation: RuntimeLifecycleOperation = RuntimeLifecycleOperation.NONE,
        configurationRequired: Boolean = false,
        processActive: Boolean = false,
        recoveryInProgress: Boolean = false,
    ): RuntimeLifecycleState {
        val resolved = RuntimeLifecyclePolicy.resolve(
            environmentReady = environmentReady,
            runtimeState = RuntimeExecutionState.valueOf(runtimeState.name),
            operation = ProjectLifecycleOperation.valueOf(operation.name),
            configurationRequired = configurationRequired,
            processActive = processActive,
            recoveryInProgress = recoveryInProgress,
        )
        return RuntimeLifecycleState.valueOf(resolved.name)
    }
}
