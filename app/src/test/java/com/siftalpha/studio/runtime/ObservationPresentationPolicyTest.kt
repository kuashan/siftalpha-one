package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationPresentationPolicyTest {

    @Test
    fun automaticStatusPendingIsInternalOnly() {
        assertEquals(
            ObservationPendingVisibility.INTERNAL,
            ObservationPresentationPolicy.pendingVisibility(automaticObservation = true),
        )
        assertFalse(ObservationPresentationPolicy.isUserVisiblePending(automaticObservation = true))
    }

    @Test
    fun manualStatusPendingRemainsUserVisible() {
        assertEquals(
            ObservationPendingVisibility.USER_VISIBLE,
            ObservationPresentationPolicy.pendingVisibility(automaticObservation = false),
        )
        assertTrue(ObservationPresentationPolicy.isUserVisiblePending(automaticObservation = false))
    }

    @Test
    fun automaticStatusDoesNotExpandRawLogsButAutomaticLogsRemainCollapsed() {
        assertFalse(ObservationPresentationPolicy.shouldExpandRawLogs(automaticObservation = true))
        assertTrue(ObservationPresentationPolicy.shouldExpandRawLogs(automaticObservation = false))
    }

    @Test
    fun manualActionDuringAutomaticObservationIsDeferredInsteadOfDropped() {
        assertEquals(
            ObservationDispatchDecision.DEFER_UNTIL_AUTOMATIC_COMPLETES,
            ObservationPresentationPolicy.dispatchDecision(
                automaticObservation = false,
                automaticPending = true,
                deferredManualAction = false,
            ),
        )
        assertEquals(
            ObservationDispatchDecision.WAITING_FOR_DEFERRED_ACTION,
            ObservationPresentationPolicy.dispatchDecision(
                automaticObservation = false,
                automaticPending = true,
                deferredManualAction = true,
            ),
        )
    }

    @Test
    fun automaticFinalLogsStillAllowRichResultExtractionAndStayCollapsed() {
        assertTrue(
            RichResultDetectionPolicy.shouldInspectOutput(
                ProjectRuntimeController.Action.LOGS,
                webLogDiscoveryAllowed = true,
            ),
        )
        assertFalse(ObservationPresentationPolicy.shouldExpandRawLogs(automaticObservation = true))
    }

    @Test
    fun normalAndAutomaticCommandsCanDispatchWhenNoConflictExists() {
        assertEquals(
            ObservationDispatchDecision.DISPATCH_NOW,
            ObservationPresentationPolicy.dispatchDecision(
                automaticObservation = false,
                automaticPending = false,
                deferredManualAction = false,
            ),
        )
        assertEquals(
            ObservationDispatchDecision.DISPATCH_NOW,
            ObservationPresentationPolicy.dispatchDecision(
                automaticObservation = true,
                automaticPending = false,
                deferredManualAction = false,
            ),
        )
    }
}
