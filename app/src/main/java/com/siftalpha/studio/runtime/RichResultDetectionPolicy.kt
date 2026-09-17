package com.siftalpha.studio.runtime

/**
 * Rich Result inspection is an output-observation concern, separate from Web log discovery.
 *
 * The Web flag is accepted at this boundary so callers cannot accidentally re-couple the two
 * decisions; it is intentionally not used to suppress inspection.
 */
object RichResultDetectionPolicy {

    @Suppress("UNUSED_PARAMETER")
    fun shouldInspectOutput(
        action: ProjectRuntimeController.Action,
        webLogDiscoveryAllowed: Boolean,
    ): Boolean = when (action) {
        ProjectRuntimeController.Action.START,
        ProjectRuntimeController.Action.STATUS,
        ProjectRuntimeController.Action.LOGS,
        -> true

        else -> false
    }
}
