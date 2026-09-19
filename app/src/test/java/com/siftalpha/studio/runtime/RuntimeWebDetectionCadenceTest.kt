package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebDetectionCadenceTest {

    @Test
    fun newEndpointGetsBoundedFastRetryBurstThenNormalCadence() {
        assertEquals(150L, RuntimeWebDetectionCadence.endpointRecheckDelay(false, 1))
        assertEquals(300L, RuntimeWebDetectionCadence.endpointRecheckDelay(false, 2))
        assertEquals(600L, RuntimeWebDetectionCadence.endpointRecheckDelay(false, 3))
        assertEquals(2_000L, RuntimeWebDetectionCadence.endpointRecheckDelay(false, 4))

        assertTrue(RuntimeWebDetectionCadence.initialVerificationPending(false, 1))
        assertTrue(RuntimeWebDetectionCadence.initialVerificationPending(false, 3))
        assertFalse(RuntimeWebDetectionCadence.initialVerificationPending(false, 4))
    }

    @Test
    fun verifiedEndpointAlwaysUsesSteadyStateHealthCadence() {
        assertEquals(2_000L, RuntimeWebDetectionCadence.endpointRecheckDelay(true, 1))
        assertFalse(RuntimeWebDetectionCadence.initialVerificationPending(true, 1))
    }

    @Test
    fun externalObservationIsFastOnlyWhileWebDiscoveryIsUseful() {
        assertEquals(500L, RuntimeWebDetectionCadence.externalObservationDelay(true))
        assertEquals(2_000L, RuntimeWebDetectionCadence.externalObservationDelay(false))
    }
}
