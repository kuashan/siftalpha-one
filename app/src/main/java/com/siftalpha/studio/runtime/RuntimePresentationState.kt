package com.siftalpha.studio.runtime

/**
 * Resolves the user-facing Runtime lifecycle from process state only.
 *
 * Web readiness is exposed separately through [RuntimeWebUiStatus]. A missing or unreachable Web
 * endpoint must never rewrite a process-backed RUNNING state into STARTING.
 */
object RuntimePresentationState {
    fun resolve(runtimeState: RuntimeState): RuntimeState = runtimeState
}
