package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectStatusGuidancePolicyTest {

    @Test
    fun runningWithoutEvidenceExplainsAutomaticObservation() {
        assertEquals(
            ProjectStatusGuidancePolicy.Message.RUNNING_OBSERVING,
            ProjectStatusGuidancePolicy.resolve(
                state = RuntimeState.RUNNING,
                webEndpointVerified = false,
                richResultAvailable = false,
            ),
        )
    }

    @Test
    fun verifiedWebEndpointExplainsLiveInterface() {
        assertEquals(
            ProjectStatusGuidancePolicy.Message.RUNNING_WEB_AVAILABLE,
            ProjectStatusGuidancePolicy.resolve(
                state = RuntimeState.RUNNING,
                webEndpointVerified = true,
                richResultAvailable = false,
            ),
        )
    }

    @Test
    fun completedRichResultIsReadyToOpen() {
        assertEquals(
            ProjectStatusGuidancePolicy.Message.RESULT_READY,
            ProjectStatusGuidancePolicy.resolve(
                state = RuntimeState.EXITED_SUCCESS,
                webEndpointVerified = false,
                richResultAvailable = true,
            ),
        )
    }

    @Test
    fun completedWithoutRichResultKeepsLogsAsDiagnostics() {
        assertEquals(
            ProjectStatusGuidancePolicy.Message.COMPLETED_OUTPUT_AVAILABLE,
            ProjectStatusGuidancePolicy.resolve(
                state = RuntimeState.EXITED_SUCCESS,
                webEndpointVerified = false,
                richResultAvailable = false,
            ),
        )
    }

    @Test
    fun stoppedByUserIsNotReportedAsCompleted() {
        assertEquals(
            ProjectStatusGuidancePolicy.Message.STOPPED_INCOMPLETE,
            ProjectStatusGuidancePolicy.resolve(
                state = RuntimeState.STOPPED_BY_USER,
                webEndpointVerified = false,
                richResultAvailable = false,
            ),
        )
    }

    @Test
    fun failuresKeepExistingFailureGuidance() {
        assertNull(
            ProjectStatusGuidancePolicy.resolve(
                state = RuntimeState.EXITED_ERROR,
                webEndpointVerified = false,
                richResultAvailable = false,
            ),
        )
    }
}
