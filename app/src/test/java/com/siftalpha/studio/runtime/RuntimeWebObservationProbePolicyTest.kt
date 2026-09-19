package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebObservationProbePolicyTest {

    @Test
    fun candidateExistsButEndpointIsUnreachableAllowsBoundedFollowUpLogs() {
        assertTrue(
            allow(
                candidateExists = true,
                endpointVerified = false,
                probeCount = 1,
            ),
        )
    }

    @Test
    fun verifiedReachableEndpointStopsFurtherDiscoveryLogs() {
        assertFalse(
            allow(
                candidateExists = true,
                endpointVerified = true,
                probeCount = 1,
            ),
        )
    }

    @Test
    fun replacementCandidateCanBeProbedAfterOldCandidateIsUnreachable() {
        // Candidate A was retained but failed its endpoint probe. A later LOGS result may expose B.
        assertTrue(
            allow(
                candidateExists = true,
                endpointVerified = false,
                probeCount = 2,
            ),
        )
        // Once B is verified, discovery LOGS stop immediately.
        assertFalse(
            allow(
                candidateExists = true,
                endpointVerified = true,
                probeCount = 2,
            ),
        )
    }

    @Test
    fun maximumProbeCountStopsDiscoveryEvenWithoutAReachableEndpoint() {
        assertFalse(
            allow(
                candidateExists = true,
                endpointVerified = false,
                probeCount = 3,
                maxProbeCount = 3,
            ),
        )
    }


    @Test
    fun runtimeHintsPermitOwnedProcfsDiscoveryWithoutEnablingWeakLogUrlDiscovery() {
        assertTrue(
            RuntimeWebObservationProbePolicy.shouldProbe(
                RuntimeWebObservationProbePolicy.Input(
                    webLogDiscoveryAllowed = false,
                    runtimeHintDiscoveryAllowed = true,
                    probeCount = 0,
                    maxProbeCount = 3,
                    candidateExists = false,
                    endpointVerified = false,
                ),
            ),
        )
    }

    @Test
    fun discoveryDisabledStopsLogsRegardlessOfCandidateFacts() {
        assertFalse(
            allow(
                candidateExists = false,
                endpointVerified = false,
                probeCount = 0,
                webLogDiscoveryAllowed = false,
            ),
        )
    }

    private fun allow(
        candidateExists: Boolean,
        endpointVerified: Boolean,
        probeCount: Int,
        maxProbeCount: Int = 3,
        webLogDiscoveryAllowed: Boolean = true,
    ): Boolean = RuntimeWebObservationProbePolicy.shouldProbe(
        RuntimeWebObservationProbePolicy.Input(
            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
            probeCount = probeCount,
            maxProbeCount = maxProbeCount,
            candidateExists = candidateExists,
            endpointVerified = endpointVerified,
        ),
    )
}
