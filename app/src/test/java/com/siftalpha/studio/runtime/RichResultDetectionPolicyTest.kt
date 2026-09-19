package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichResultDetectionPolicyTest {

    @Test
    fun webDiscoveryScopeDoesNotDisableRichResultInspection() {
        listOf(false, true).forEach { webLogDiscoveryAllowed ->
            assertTrue(
                RichResultDetectionPolicy.shouldInspectOutput(
                    ProjectRuntimeController.Action.START,
                    webLogDiscoveryAllowed,
                ),
            )
            assertTrue(
                RichResultDetectionPolicy.shouldInspectOutput(
                    ProjectRuntimeController.Action.STATUS,
                    webLogDiscoveryAllowed,
                ),
            )
            assertTrue(
                RichResultDetectionPolicy.shouldInspectOutput(
                    ProjectRuntimeController.Action.LOGS,
                    webLogDiscoveryAllowed,
                ),
            )
        }
    }

    @Test
    fun nonObservationActionsRemainOutsideRichResultInspection() {
        listOf(
            ProjectRuntimeController.Action.PREPARE,
            ProjectRuntimeController.Action.STOP,
            ProjectRuntimeController.Action.CLEAN,
            ProjectRuntimeController.Action.CLONE_GITHUB,
        ).forEach { action ->
            assertFalse(
                RichResultDetectionPolicy.shouldInspectOutput(
                    action,
                    webLogDiscoveryAllowed = true,
                ),
            )
        }
    }

    @Test
    fun webHintDiscoveryDoesNotSuppressRichResultInspection() {
        assertTrue(
            RichResultDetectionPolicy.shouldInspectOutput(
                action = ProjectRuntimeController.Action.LOGS,
                webLogDiscoveryAllowed = false,
            ),
        )
    }

}
