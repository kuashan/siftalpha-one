package com.siftalpha.studio.runtime

/**
 * User-facing status guidance derived from observed runtime facts.
 *
 * It intentionally does not expose a project "mode" or ask the user to classify a project.
 */
object ProjectStatusGuidancePolicy {
    enum class Message {
        RUNNING_OBSERVING,
        RUNNING_WEB_AVAILABLE,
        RESULT_READY,
        COMPLETED_OUTPUT_AVAILABLE,
        STOPPED_INCOMPLETE,
    }

    fun resolve(
        state: RuntimeState,
        webEndpointVerified: Boolean,
        richResultAvailable: Boolean,
    ): Message? = when (state) {
        RuntimeState.RUNNING ->
            if (webEndpointVerified) Message.RUNNING_WEB_AVAILABLE
            else Message.RUNNING_OBSERVING
        RuntimeState.EXITED_SUCCESS ->
            if (richResultAvailable) Message.RESULT_READY
            else Message.COMPLETED_OUTPUT_AVAILABLE
        RuntimeState.STOPPED_BY_USER -> Message.STOPPED_INCOMPLETE
        else -> null
    }
}
