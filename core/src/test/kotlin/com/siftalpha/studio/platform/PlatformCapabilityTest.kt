package com.siftalpha.studio.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCapabilityTest {
    @Test
    fun missingCapabilityIsUnknownAndNeverImplicitlyAvailable() {
        val snapshot = PlatformCapabilitySnapshot()

        assertEquals(
            CapabilityAvailability.UNKNOWN,
            snapshot.availabilityOf(StandardPlatformCapabilities.CONTAINER_RUNTIME),
        )
        assertFalse(snapshot.supports(StandardPlatformCapabilities.CONTAINER_RUNTIME))
    }

    @Test
    fun onePlatformCapabilityDoesNotForceAnotherCapabilityToExist() {
        val snapshot = PlatformCapabilitySnapshot(
            mapOf(
                StandardPlatformCapabilities.HOST_PROCESS_EXECUTION to CapabilityAvailability.AVAILABLE,
                StandardPlatformCapabilities.CONTAINER_RUNTIME to CapabilityAvailability.UNAVAILABLE,
            ),
        )

        assertTrue(snapshot.supports(StandardPlatformCapabilities.HOST_PROCESS_EXECUTION))
        assertFalse(snapshot.supports(StandardPlatformCapabilities.CONTAINER_RUNTIME))
    }

    @Test
    fun unavailableCapabilityIsAValidPlatformFactNotAnError() {
        val snapshot = PlatformCapabilitySnapshot(
            mapOf(
                StandardPlatformCapabilities.VIRTUAL_MACHINE_RUNTIME to CapabilityAvailability.UNAVAILABLE,
            ),
        )

        assertEquals(
            CapabilityAvailability.UNAVAILABLE,
            snapshot.availabilityOf(StandardPlatformCapabilities.VIRTUAL_MACHINE_RUNTIME),
        )
        assertFalse(snapshot.supports(StandardPlatformCapabilities.VIRTUAL_MACHINE_RUNTIME))
    }
}
