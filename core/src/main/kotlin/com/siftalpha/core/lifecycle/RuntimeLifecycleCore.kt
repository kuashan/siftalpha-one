package com.siftalpha.core.lifecycle

/**
 * Platform-independent Runtime（运行时） execution facts.
 *
 * These are domain facts only. They intentionally contain no Android（安卓）, macOS（苹果）,
 * Windows（微软）, UI（界面）, storage or process-launching APIs.
 */
enum class RuntimeExecutionState {
    PREPARING,
    STARTING,
    RUNNING,
    EXITED_SUCCESS,
    EXITED_ERROR,
    STOPPED_BY_USER,
    ENVIRONMENT_ERROR,
    UNKNOWN,
}

/** Project-level lifecycle presented by the shared product model. */
enum class ProjectLifecycleState {
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
    RECOVERING,
}

/** One currently accepted lifecycle operation. */
enum class ProjectLifecycleOperation {
    NONE,
    PREPARE,
    START,
    STOP,
    STATUS,
    LOGS,
    CLEAN,
}

/**
 * Shared lifecycle decision policy（共享生命周期决策策略）.
 *
 * Platform adapters provide facts. Core（核心） decides the lifecycle state.
 */
object RuntimeLifecyclePolicy {
    fun resolve(
        environmentReady: Boolean?,
        runtimeState: RuntimeExecutionState,
        operation: ProjectLifecycleOperation = ProjectLifecycleOperation.NONE,
        configurationRequired: Boolean = false,
        processActive: Boolean = false,
        recoveryInProgress: Boolean = false,
    ): ProjectLifecycleState {
        if (operation == ProjectLifecycleOperation.NONE && recoveryInProgress) {
            return ProjectLifecycleState.RECOVERING
        }

        when (operation) {
            ProjectLifecycleOperation.PREPARE -> return ProjectLifecycleState.PREPARING
            ProjectLifecycleOperation.START -> return ProjectLifecycleState.STARTING
            ProjectLifecycleOperation.STATUS,
            ProjectLifecycleOperation.LOGS,
            -> return ProjectLifecycleState.CHECKING
            ProjectLifecycleOperation.STOP -> return ProjectLifecycleState.STOPPING
            ProjectLifecycleOperation.CLEAN -> return ProjectLifecycleState.CLEANING
            ProjectLifecycleOperation.NONE -> Unit
        }

        if (runtimeState == RuntimeExecutionState.PREPARING) {
            return ProjectLifecycleState.PREPARING
        }
        if (runtimeState == RuntimeExecutionState.STARTING) {
            return ProjectLifecycleState.STARTING
        }

        if (environmentReady != true) {
            return ProjectLifecycleState.ENVIRONMENT_NOT_PREPARED
        }
        if (configurationRequired) {
            return ProjectLifecycleState.NEEDS_CONFIGURATION
        }

        return when {
            runtimeState == RuntimeExecutionState.RUNNING || processActive ->
                ProjectLifecycleState.RUNNING

            runtimeState == RuntimeExecutionState.EXITED_ERROR ||
                runtimeState == RuntimeExecutionState.ENVIRONMENT_ERROR ->
                ProjectLifecycleState.RUN_FAILED

            runtimeState == RuntimeExecutionState.EXITED_SUCCESS ||
                runtimeState == RuntimeExecutionState.STOPPED_BY_USER ->
                ProjectLifecycleState.STOPPED

            else -> ProjectLifecycleState.READY_TO_RUN
        }
    }
}

/**
 * Provider-neutral parser for structured Runtime（运行时） output.
 *
 * Output transport can differ by platform/provider, but once a provider emits the shared
 * SIFTALPHA_* lifecycle contract, parsing is a Core（核心） concern.
 */
object RuntimeOutputStateParser {
    fun fromOutput(output: String): RuntimeExecutionState {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        lines.lastOrNull { it.startsWith("SIFTALPHA_RUNTIME_STATE=") }
            ?.substringAfter('=')
            ?.let(::fromStateToken)
            ?.let { return it }

        lines.firstOrNull { it.startsWith("STATE=") }
            ?.substringAfter('=')
            ?.let { state ->
                when (state) {
                    "EXITED" -> return if (extractExitCode(output) == 0) {
                        RuntimeExecutionState.EXITED_SUCCESS
                    } else {
                        RuntimeExecutionState.EXITED_ERROR
                    }
                    else -> fromStateToken(state)?.let { return it }
                }
            }

        return when {
            lines.any { it == "SIFTALPHA_STATUS=STOPPED_BY_USER" } ->
                RuntimeExecutionState.STOPPED_BY_USER

            lines.any { it == "SIFTALPHA_STATUS=EXITED_SUCCESS" } ->
                RuntimeExecutionState.EXITED_SUCCESS

            lines.any {
                it == "SIFTALPHA_STATUS=EXITED_ERROR" ||
                    it == "SIFTALPHA_STATUS=EXITED"
            } -> if (extractExitCode(output) == 0) {
                RuntimeExecutionState.EXITED_SUCCESS
            } else {
                RuntimeExecutionState.EXITED_ERROR
            }

            lines.any { it == "SIFTALPHA_STATUS=RUNNING" } ->
                RuntimeExecutionState.RUNNING

            lines.any {
                it.startsWith("SIFTALPHA_ERROR=") ||
                    it == "STATE=START_FAILED"
            } -> RuntimeExecutionState.ENVIRONMENT_ERROR

            else -> RuntimeExecutionState.UNKNOWN
        }
    }

    fun extractExitCode(output: String): Int? {
        val lines = output.lineSequence().map { it.trim() }.toList()
        val exitLine = lines.lastOrNull { it.startsWith("EXIT_CODE=") }
            ?: lines.lastOrNull { it.startsWith("SIFTALPHA_PROCESS_EXIT=") }
            ?: return null
        return exitLine.substringAfter('=').trim().toIntOrNull()
    }

    private fun fromStateToken(token: String): RuntimeExecutionState? = when (token.trim()) {
        "PREPARING" -> RuntimeExecutionState.PREPARING
        "STARTING" -> RuntimeExecutionState.STARTING
        "RUNNING" -> RuntimeExecutionState.RUNNING
        "EXITED_SUCCESS" -> RuntimeExecutionState.EXITED_SUCCESS
        "EXITED_ERROR" -> RuntimeExecutionState.EXITED_ERROR
        "STOPPED_BY_USER" -> RuntimeExecutionState.STOPPED_BY_USER
        "ENVIRONMENT_ERROR" -> RuntimeExecutionState.ENVIRONMENT_ERROR
        else -> null
    }
}
