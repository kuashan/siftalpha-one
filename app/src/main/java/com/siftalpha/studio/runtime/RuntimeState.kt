package com.siftalpha.studio.runtime

import android.content.Context
import com.siftalpha.core.lifecycle.RuntimeOutputStateParser
import com.siftalpha.studio.R

/**
 * Android（安卓） compatibility view of the shared Runtime（运行时） execution state.
 *
 * Lifecycle parsing now lives in SiftAlpha Core（跨平台核心）. Android（安卓） keeps this
 * enum so existing UI（界面）, persistence and Runtime integration call sites remain stable
 * during the staged cross-platform extraction.
 */
enum class RuntimeState {
    PREPARING,
    STARTING,
    RUNNING,
    EXITED_SUCCESS,
    EXITED_ERROR,
    STOPPED_BY_USER,
    ENVIRONMENT_ERROR,
    UNKNOWN;

    fun uiLabel(context: Context, environmentReady: Boolean? = null): String {
        val env = when (environmentReady) {
            true -> context.getString(R.string.runtime_state_env_ready_suffix)
            false -> context.getString(R.string.runtime_state_env_not_ready_suffix)
            null -> ""
        }
        return when (this) {
            PREPARING -> context.getString(R.string.runtime_state_preparing)
            STARTING -> context.getString(R.string.runtime_state_starting)
            RUNNING -> context.getString(R.string.runtime_state_running, env)
            EXITED_SUCCESS -> context.getString(R.string.runtime_state_exited_success, env)
            EXITED_ERROR -> context.getString(R.string.runtime_state_exited_error, env)
            STOPPED_BY_USER -> context.getString(R.string.runtime_state_stopped_by_user, env)
            ENVIRONMENT_ERROR -> context.getString(R.string.runtime_state_environment_error, env)
            UNKNOWN -> when (environmentReady) {
                true -> context.getString(R.string.runtime_state_env_ready)
                false -> context.getString(R.string.runtime_state_env_not_ready)
                null -> context.getString(R.string.runtime_state_not_checked)
            }
        }
    }

    internal fun labelResource(): Int = when (this) {
        PREPARING -> R.string.runtime_state_preparing
        STARTING -> R.string.runtime_state_starting
        RUNNING -> R.string.runtime_state_running
        EXITED_SUCCESS -> R.string.runtime_state_exited_success
        EXITED_ERROR -> R.string.runtime_state_exited_error
        STOPPED_BY_USER -> R.string.runtime_state_stopped_by_user
        ENVIRONMENT_ERROR -> R.string.runtime_state_environment_error
        UNKNOWN -> R.string.runtime_state_not_checked
    }

    companion object {
        fun fromOutput(output: String): RuntimeState =
            valueOf(RuntimeOutputStateParser.fromOutput(output).name)

        fun extractExitCode(output: String): Int? =
            RuntimeOutputStateParser.extractExitCode(output)
    }
}
