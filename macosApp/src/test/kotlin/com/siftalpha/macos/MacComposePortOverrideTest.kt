package com.siftalpha.macos

import com.siftalpha.studio.container.ComposePortBinding
import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.container.ComposeProjectPlanStatus
import com.siftalpha.studio.container.ComposeServicePlan
import com.siftalpha.studio.platform.CapabilityAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacComposePortOverrideTest {
    private val plan = ComposeProjectPlan(
        status = ComposeProjectPlanStatus.READY,
        manifestPath = "compose.yaml",
        services = listOf(
            ComposeServicePlan("web", "nginx", null, emptyList(), listOf(ComposePortBinding("18080:80", 18080, 80, "tcp"))),
        ),
        containerAvailability = CapabilityAvailability.AVAILABLE,
        issues = emptyList(),
    )

    @Test fun remapsOnlyOccupiedPublishedPort() {
        val resolution = MacComposePortOverride.resolve(plan) { it != 18080 && it != 18081 }
        assertEquals(mapOf(18080 to 18082), resolution.remappedPorts)
        assertTrue(resolution.yaml.contains("ports: !override"))
        assertTrue(resolution.yaml.contains("18082:80/tcp"))
    }

    @Test fun leavesManifestUntouchedWhenAllPortsAreFree() {
        val resolution = MacComposePortOverride.resolve(plan) { true }
        assertTrue(resolution.remappedPorts.isEmpty())
        assertEquals("", resolution.yaml)
    }
}
