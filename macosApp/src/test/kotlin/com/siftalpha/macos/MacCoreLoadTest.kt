package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import com.siftalpha.studio.platform.StandardPlatformCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacCoreLoadTest {
    @Test
    fun macHostLoadsCoreCapabilitySnapshot() {
        val snapshot = MacPlatformCapabilities.snapshot()

        assertEquals(
            CapabilityAvailability.AVAILABLE,
            snapshot.availabilityOf(StandardPlatformCapabilities.HOST_PROCESS_EXECUTION),
        )
        assertEquals(
            CapabilityAvailability.UNKNOWN,
            snapshot.availabilityOf(StandardPlatformCapabilities.SECURE_SECRET_STORAGE),
        )
        assertEquals(
            CapabilityAvailability.UNKNOWN,
            snapshot.availabilityOf(StandardPlatformCapabilities.CONTAINER_RUNTIME),
        )
        assertFalse(snapshot.supports(StandardPlatformCapabilities.CONTAINER_RUNTIME))
    }

    @Test
    fun macHostPublishesContainerCapabilityFromAdapterDiscovery() {
        val snapshot = MacPlatformCapabilities.snapshot(CapabilityAvailability.AVAILABLE)

        assertEquals(
            CapabilityAvailability.AVAILABLE,
            snapshot.availabilityOf(StandardPlatformCapabilities.CONTAINER_RUNTIME),
        )
        assertTrue(snapshot.supports(StandardPlatformCapabilities.CONTAINER_RUNTIME))
    }
}
