package com.siftalpha.studio.presentation

import com.siftalpha.studio.runtime.RuntimeLifecycleState
import com.siftalpha.studio.runtime.RuntimeState

/**
 * Presentation-only policy for the indeterminate Runtime activity indicator.
 *
 * The indicator is intentionally not a percentage and never extends a Runtime deadline. Bounded
 * control operations animate only while a user-visible operation is still active; once the shared
 * coordinator times out or completes that operation, the indicator stops. A confirmed RUNNING
 * project may animate indefinitely because a long-running service has no completion percentage.
 */
object RuntimeActivityIndicatorPolicy {
    data class Input(
        val lifecycleState: RuntimeLifecycleState,
        val runtimeState: RuntimeState,
        val operationActive: Boolean,
    )

    fun shouldAnimate(input: Input): Boolean {
        if (input.runtimeState == RuntimeState.RUNNING) return true
        if (!input.operationActive) return false
        return input.lifecycleState in ACTIVE_CONTROL_STATES
    }

    private val ACTIVE_CONTROL_STATES = setOf(
        RuntimeLifecycleState.DETECTING,
        RuntimeLifecycleState.PREPARING,
        RuntimeLifecycleState.STARTING,
        RuntimeLifecycleState.CHECKING,
        RuntimeLifecycleState.STOPPING,
        RuntimeLifecycleState.CLEANING,
    )
}
