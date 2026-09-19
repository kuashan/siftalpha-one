package com.siftalpha.studio.runtime

import android.content.Context
import com.siftalpha.studio.R

/**
 * Single source of truth for Web readiness shown by the project card and Browser button.
 *
 * Running Web projects progress through SEARCHING -> LISTENER_FOUND -> STARTING_WEB -> AVAILABLE.
 * Listener ownership and HTTP readiness are deliberately separate facts.
 */
enum class RuntimeWebUiStatus {
    SEARCHING,
    LISTENER_FOUND,
    STARTING_WEB,
    AVAILABLE,
    UNAVAILABLE,
    WAITING,
    AUTO_DETECT,
    /** Kept only for source compatibility with older presentation paths; new resolve() never emits it. */
    DETECTING;

    fun uiLabel(context: Context): String = context.getString(
        when (this) {
            SEARCHING, DETECTING -> R.string.runtime_web_status_searching
            LISTENER_FOUND -> R.string.runtime_web_status_listener_found
            STARTING_WEB -> R.string.runtime_web_status_starting_web
            AVAILABLE -> R.string.runtime_web_status_available
            UNAVAILABLE -> R.string.runtime_web_not_found_title
            WAITING -> R.string.runtime_web_status_waiting
            AUTO_DETECT -> R.string.runtime_web_status_auto_detect
        },
    )

    companion object {
        fun resolve(
            profileEnabled: Boolean,
            hasCandidateRuntimeUrl: Boolean,
            hasConfiguredLocalUrl: Boolean,
            runtimeState: RuntimeState,
            endpointReachable: Boolean? = null,
            listenerFound: Boolean = false,
        ): RuntimeWebUiStatus = when {
            runtimeState == RuntimeState.RUNNING && endpointReachable == true -> AVAILABLE
            runtimeState == RuntimeState.RUNNING && listenerFound && endpointReachable == null ->
                LISTENER_FOUND
            runtimeState == RuntimeState.RUNNING && listenerFound && endpointReachable == false ->
                STARTING_WEB
            runtimeState == RuntimeState.RUNNING -> SEARCHING
            profileEnabled || hasCandidateRuntimeUrl || hasConfiguredLocalUrl -> WAITING
            else -> AUTO_DETECT
        }
    }
}
