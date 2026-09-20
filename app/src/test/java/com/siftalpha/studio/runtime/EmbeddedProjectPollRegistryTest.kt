package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedProjectPollRegistryTest {

    @Test
    fun projectsTrackAndPollIndependently() {
        val registry = EmbeddedProjectPollRegistry<String>()
        registry.track("a", "project-a")
        registry.track("b", "project-b")

        assertTrue(registry.begin("a"))
        assertTrue(registry.isInFlight("a"))
        assertFalse(registry.isInFlight("b"))

        assertTrue("B must not be blocked by A in-flight polling", registry.begin("b"))
        assertTrue(registry.isInFlight("b"))

        registry.finish("a")
        assertFalse(registry.isInFlight("a"))
        assertTrue(registry.isInFlight("b"))
        assertEquals(setOf("a", "b"), registry.trackedKeys())
    }

    @Test
    fun untrackingOneProjectDoesNotAffectOthers() {
        val registry = EmbeddedProjectPollRegistry<String>()
        registry.track("a", "project-a")
        registry.track("b", "project-b")
        assertTrue(registry.begin("a"))
        assertTrue(registry.begin("b"))

        assertEquals("project-a", registry.untrack("a"))

        assertNull(registry.project("a"))
        assertFalse(registry.isInFlight("a"))
        assertEquals("project-b", registry.project("b"))
        assertTrue(registry.isInFlight("b"))
        assertEquals(setOf("b"), registry.trackedKeys())
    }

    @Test
    fun missingProjectCannotEnterInFlightState() {
        val registry = EmbeddedProjectPollRegistry<String>()

        assertFalse(registry.begin("missing"))
        assertFalse(registry.isInFlight("missing"))
    }
}
