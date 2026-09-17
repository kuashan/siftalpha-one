package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProjectStatusPresentationPolicyTest {

    @Test
    fun richResultGuidanceIsThePrimaryUserStatusSummary() {
        val guidance = ProjectStatusGuidancePolicy.resolve(
            state = RuntimeState.EXITED_SUCCESS,
            webEndpointVerified = false,
            richResultAvailable = true,
        )

        assertEquals(
            ProjectStatusGuidancePolicy.Message.RESULT_READY,
            guidance,
        )
        assertEquals(
            ProjectStatusPresentationPolicy.SummarySource.GUIDANCE,
            ProjectStatusPresentationPolicy.summarySource(guidance),
        )
    }

    @Test
    fun verifiedWebGuidanceIsThePrimaryUserStatusSummary() {
        val guidance = ProjectStatusGuidancePolicy.resolve(
            state = RuntimeState.RUNNING,
            webEndpointVerified = true,
            richResultAvailable = false,
        )

        assertEquals(
            ProjectStatusGuidancePolicy.Message.RUNNING_WEB_AVAILABLE,
            guidance,
        )
        assertEquals(
            ProjectStatusPresentationPolicy.SummarySource.GUIDANCE,
            ProjectStatusPresentationPolicy.summarySource(guidance),
        )
    }

    @Test
    fun actionPolicyIsUsedOnlyWhenGuidanceIsMissing() {
        assertEquals(
            ProjectStatusPresentationPolicy.SummarySource.ACTION_POLICY,
            ProjectStatusPresentationPolicy.summarySource(null),
        )
        assertNotEquals(
            ProjectStatusPresentationPolicy.SummarySource.ACTION_POLICY,
            ProjectStatusPresentationPolicy.summarySource(
                ProjectStatusGuidancePolicy.Message.RESULT_READY,
            ),
        )
    }
}
