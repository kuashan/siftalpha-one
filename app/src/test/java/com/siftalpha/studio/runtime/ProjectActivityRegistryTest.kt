package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectActivityRegistryTest {
    @Test
    fun cancellationIsScopedToOneProject() {
        val registry = ProjectActivityRegistry()
        var cancelledA = 0
        var cancelledB = 0
        val a = registry.begin("project-a", ProjectActivityRegistry.Kind.PREPARE) { cancelledA++ }
        val b = registry.begin("project-b", ProjectActivityRegistry.Kind.START) { cancelledB++ }

        assertEquals(1, registry.cancelProject("project-a"))
        assertEquals(1, cancelledA)
        assertEquals(0, cancelledB)
        assertFalse(registry.hasActive("project-a"))
        assertTrue(registry.hasActive("project-b"))
        assertFalse(registry.finish(a))
        assertTrue(registry.finish(b))
    }

    @Test
    fun attachedCancellationReplacesInitialCallback() {
        val registry = ProjectActivityRegistry()
        var initial = 0
        var attached = 0
        val token = registry.begin("project-a", ProjectActivityRegistry.Kind.PREPARE) { initial++ }

        assertTrue(registry.attachCancel(token) { attached++ })
        assertEquals(1, registry.cancelProject("project-a"))
        assertEquals(0, initial)
        assertEquals(1, attached)
    }
}
