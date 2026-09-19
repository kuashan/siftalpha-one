package com.siftalpha.studio.runtime

import android.content.Context
import com.siftalpha.studio.R

/**
 * Single source of truth for the Web availability shown by the project card and Browser button.
 *
 * A URL candidate alone is not proof that a project is Web-enabled. AVAILABLE is reserved for a
 * RUNNING project whose loopback endpoint was recently verified. AUTO_DETECT means that Studio has
 * no static Web profile, configured endpoint, or discovered URL candidate to present.
 */
enum class RuntimeWebUiStatus {
    AVAILABLE,
    DETECTING,
    UNAVAILABLE,
    WAITING,
    AUTO_DETECT;

    fun uiLabel(context: Context): String = context.getString(
        when (this) {
            AVAILABLE -> R.string.runtime_web_status_available
            DETECTING -> R.string.runtime_web_status_detecting
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
        ): RuntimeWebUiStatus = when {
            !profileEnabled && !hasCandidateRuntimeUrl && !hasConfiguredLocalUrl ->
                AUTO_DETECT
            runtimeState == RuntimeState.RUNNING && endpointReachable == true -> AVAILABLE
            runtimeState == RuntimeState.RUNNING && endpointReachable == null -> DETECTING
            runtimeState == RuntimeState.RUNNING -> UNAVAILABLE
            profileEnabled || hasCandidateRuntimeUrl || hasConfiguredLocalUrl -> WAITING
            else -> AUTO_DETECT
        }
    }
}
