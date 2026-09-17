package com.siftalpha.studio.runtime

/**
 * Presentation and dispatch rules for External Provider automatic observation.
 *
 * Automatic commands remain tracked in the Activity's internal pending map, but they do not become
 * user-visible operations. A manual request made during one automatic command is accepted and waits
 * for that command's result instead of being rejected as a duplicate.
 */
enum class ObservationPendingVisibility {
    INTERNAL,
    USER_VISIBLE,
}

enum class ObservationDispatchDecision {
    DISPATCH_NOW,
    DEFER_UNTIL_AUTOMATIC_COMPLETES,
    WAITING_FOR_DEFERRED_ACTION,
}

object ObservationPresentationPolicy {

    fun pendingVisibility(automaticObservation: Boolean): ObservationPendingVisibility =
        if (automaticObservation) {
            ObservationPendingVisibility.INTERNAL
        } else {
            ObservationPendingVisibility.USER_VISIBLE
        }

    fun isUserVisiblePending(automaticObservation: Boolean): Boolean =
        pendingVisibility(automaticObservation) == ObservationPendingVisibility.USER_VISIBLE

    fun shouldExpandRawLogs(automaticObservation: Boolean): Boolean = !automaticObservation

    fun dispatchDecision(
        automaticObservation: Boolean,
        automaticPending: Boolean,
        deferredManualAction: Boolean,
    ): ObservationDispatchDecision = when {
        automaticObservation -> ObservationDispatchDecision.DISPATCH_NOW
        !automaticPending -> ObservationDispatchDecision.DISPATCH_NOW
        deferredManualAction -> ObservationDispatchDecision.WAITING_FOR_DEFERRED_ACTION
        else -> ObservationDispatchDecision.DEFER_UNTIL_AUTOMATIC_COMPLETES
    }
}
