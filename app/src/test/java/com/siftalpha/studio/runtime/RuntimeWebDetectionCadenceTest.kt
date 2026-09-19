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
    fun verifiedEndpointNeedsRepeatedFreshFailuresBeforeDowngrade() {
        assertTrue(RuntimeWebDetectionCadence.verifiedFailurePending(true, 1))
        assertFalse(RuntimeWebDetectionCadence.verifiedFailurePending(true, 2))
        assertEquals(500L, RuntimeWebDetectionCadence.endpointRecheckDelay(true, 1))
        assertEquals(2_000L, RuntimeWebDetectionCadence.endpointRecheckDelay(true, 2))
    }

    @Test
    fun internalDiscoveryMissesUseBoundedFastBurstThenNormalCadence() {
        assertEquals(150L, RuntimeWebDetectionCadence.internalDiscoveryRetryDelay(1))
        assertEquals(300L, RuntimeWebDetectionCadence.internalDiscoveryRetryDelay(2))
        assertEquals(600L, RuntimeWebDetectionCadence.internalDiscoveryRetryDelay(3))
        assertEquals(1_000L, RuntimeWebDetectionCadence.internalDiscoveryRetryDelay(4))
        assertEquals(2_000L, RuntimeWebDetectionCadence.internalDiscoveryRetryDelay(5))
    }

    @Test
    fun externalObservationIsFastOnlyWhileWebDiscoveryIsUseful() {
        assertEquals(500L, RuntimeWebDetectionCadence.externalObservationDelay(true))
        assertEquals(2_000L, RuntimeWebDetectionCadence.externalObservationDelay(false))
    }
}
