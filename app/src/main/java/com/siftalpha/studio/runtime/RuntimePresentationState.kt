package com.siftalpha.studio.runtime

/**
 * Resolves the user-facing Runtime lifecycle from process state only.
 *
 * Web readiness is exposed separately through [RuntimeWebUiStatus]. A missing or unreachable Web
 * endpoint must never rewrite a process-backed RUNNING state into STARTING.
 */
object RuntimePresentationState {
    fun resolve(runtimeState: RuntimeState): RuntimeState = runtimeState

    /**
     * RuntimeLifecycleResolver intentionally aggregates terminal states for action policy.
     * Keep the two user-visible terminal meanings distinct in the project card.
     */
    fun terminalStateForLabel(runtimeState: RuntimeState): RuntimeState? = when (runtimeState) {
        RuntimeState.EXITED_SUCCESS,
        RuntimeState.STOPPED_BY_USER -> runtimeState
        else -> null
    }
}
