package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeWebUiStatusTest {

    @Test
    fun runningWithoutWebEvidenceIsSearching() {
        assertEquals(
            RuntimeWebUiStatus.SEARCHING,
            RuntimeWebUiStatus.resolve(
                profileEnabled = false,
                hasCandidateRuntimeUrl = false,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = null,
            ),
        )
    }

    @Test
    fun runningWebProfileWithoutListenerIsSearching() {
        assertEquals(
            RuntimeWebUiStatus.SEARCHING,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = false,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = null,
            ),
        )
    }

    @Test
    fun ownedListenerBeforeHttpProbeIsListenerFound() {
        assertEquals(
            RuntimeWebUiStatus.LISTENER_FOUND,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = null,
                listenerFound = true,
            ),
        )
    }

    @Test
    fun ownedListenerWithFailedHttpReadinessIsStartingWeb() {
        assertEquals(
            RuntimeWebUiStatus.STARTING_WEB,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = false,
                listenerFound = true,
            ),
        )
    }

    @Test
    fun previouslyVerifiedWebRemainsAvailableWhileForegroundProbeIsPending() {
        val web = com.siftalpha.studio.presentation.ProjectUiSnapshot.Web.resolve(
            profileEnabled = false,
            hasCandidateRuntimeUrl = true,
            hasConfiguredLocalUrl = false,
            runtimeState = RuntimeState.RUNNING,
            endpointReachable = null,
            listenerFound = true,
            verifiedWebIdentity = true,
        )
        assertEquals(true, web.expected)
        assertEquals(RuntimeWebUiStatus.AVAILABLE, web.status)
    }

    @Test
    fun verifiedListenerWithFreshHttpFailureShowsStartingWeb() {
        val web = com.siftalpha.studio.presentation.ProjectUiSnapshot.Web.resolve(
            profileEnabled = true,
            hasCandidateRuntimeUrl = true,
            hasConfiguredLocalUrl = false,
            runtimeState = RuntimeState.RUNNING,
            endpointReachable = false,
            listenerFound = true,
            verifiedWebIdentity = true,
        )
        assertEquals(RuntimeWebUiStatus.STARTING_WEB, web.status)
    }

    @Test
    fun runningWithHttpReadyEndpointIsAvailable() {
        assertEquals(
            RuntimeWebUiStatus.AVAILABLE,
            RuntimeWebUiStatus.resolve(
                profileEnabled = true,
                hasCandidateRuntimeUrl = true,
                hasConfiguredLocalUrl = false,
                runtimeState = RuntimeState.RUNNING,
                endpointReachable = true,
                listenerFound = true,
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
}
