package com.siftalpha.studio.runtime

/**
 * Shared Normal Mode（普通模式） guard for changing the per-project Runtime selection.
 *
 * Developer Workspace（开发者工作区） keeps its accepted UI-side guard unchanged. Normal Mode uses
 * the same persisted lifecycle/operation facts so both surfaces reject provider changes while the
 * project is active or an operation is still in flight.
 */
object ProjectRuntimeSelectionChangePolicy {
    data class Input(
        val runtimeState: RuntimeState,
        val operation: RuntimeOperationRecord?,
    )

    fun canChange(input: Input): Boolean {
        if (input.runtimeState in ACTIVE_RUNTIME_STATES) return false
        val operation = input.operation
        if (operation != null && !operation.terminal) return false
        return true
    }

    private val ACTIVE_RUNTIME_STATES = setOf(
        RuntimeState.PREPARING,
        RuntimeState.STARTING,
        RuntimeState.RUNNING,
    )
}
