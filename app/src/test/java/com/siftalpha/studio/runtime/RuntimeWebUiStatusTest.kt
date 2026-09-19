package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeWebUiStatusTest {

    @Test
    fun runningWithoutWebEvidenceUsesAutoDetect() {
        assertEquals(
            RuntimeWebUiStatus.AUTO_DETECT,
            RuntimeWebUiStatus.resolve(
                profileEnabled = false,
                hasCandidateRuntimeUrl = false,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = false,
            ),
        )
    }

    @Test
    fun runningWithUnknownProbeStateIsDetecting() {
        assertEquals(
            RuntimeWebUiStatus.DETECTING,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = null,
            ),
        )
    }

    @Test
    fun previouslyVerifiedWebRemainsExpectedWhileForegroundProbeIsPending() {
        val web = com.siftalpha.studio.presentation.ProjectUiSnapshot.Web.resolve(
            profileEnabled = false,
            hasCandidateRuntimeUrl = true,
            hasConfiguredLocalUrl = false,
            runtimeState = RuntimeState.RUNNING,
            endpointReachable = null,
            verifiedWebIdentity = true,
        )
        assertEquals(true, web.expected)
        assertEquals(RuntimeWebUiStatus.AVAILABLE, web.status)
    }

    @Test
    fun confirmedFailureStillDowngradesVerifiedIdentity() {
        val web = com.siftalpha.studio.presentation.ProjectUiSnapshot.Web.resolve(
            profileEnabled = true,
            hasCandidateRuntimeUrl = true,
            hasConfiguredLocalUrl = false,
            runtimeState = RuntimeState.RUNNING,
            endpointReachable = false,
            verifiedWebIdentity = true,
        )
        assertEquals(RuntimeWebUiStatus.UNAVAILABLE, web.status)
    }

    @Test
    fun runningWithVerifiedEndpointIsAvailable() {
        assertEquals(
            RuntimeWebUiStatus.AVAILABLE,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = true,
            ),
        )
    }

    @Test
    fun completedNegativeProbeIsExplainableUnavailableState() {
        assertEquals(
            RuntimeWebUiStatus.UNAVAILABLE,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = false,
            ),
        )
    }

    @Test
    fun candidateUrlWithClosedEndpointIsUnavailable() {
        assertEquals(
            RuntimeWebUiStatus.UNAVAILABLE,
            RuntimeWebUiStatus.resolve(
                profileEnabled = false,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = false,
            ),
        )
    }

    @Test
    fun detectedWebProjectNotRunningIsWaiting() {
        assertEquals(
            RuntimeWebUiStatus.WAITING,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = false,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.STOPPED_BY_USER,
                endpointReachable = null,
            ),
        )
    }

    @Test
    fun unknownProjectWithoutUrlUsesAutoDetect() {
        assertEquals(
            RuntimeWebUiStatus.AUTO_DETECT,
            RuntimeWebUiStatus.resolve(
                profileEnabled = false,
                hasCandidateRuntimeUrl = false,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.UNKNOWN,
                endpointReachable = null,
            ),
        )
    }

    @Test
    fun configuredUrlWithClosedEndpointIsUnavailable() {
        assertEquals(
            RuntimeWebUiStatus.UNAVAILABLE,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = false,
                hasConfiguredLocalUrl = true,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = false,
            ),
        )
    }
}
