package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalRuntimeForegroundServiceTest {

    @Test
    fun sessionLeasesAreIndependentAndProjectScopedStopsCannotDropSiblingLease() {
        val leases = InternalRuntimeProjectSet()

        assertTrue(leases.acquire("session-a"))
        assertTrue(leases.acquire("session-b"))
        assertFalse(leases.acquire("session-a"))
        assertEquals(2, leases.size())

        assertTrue(leases.release("session-a"))
        assertEquals(1, leases.size())
        assertFalse(leases.isEmpty())

        assertTrue(leases.release("session-b"))
        assertTrue(leases.isEmpty())
    }
}
