package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacContainerWorkflowTest {
    private val healthyFacts = MacSystemFacts(
        osVersion = "15.0",
        osMajor = 15,
        architecture = "arm64",
        processorCount = 10,
        physicalMemoryBytes = 16L * 1024L * 1024L * 1024L,
        usableDiskBytes = 200L * 1024L * 1024L * 1024L,
    )

    @Test
    fun composeProjectIdentityIsStableAndProjectScoped() {
        val a1 = MacComposeProjectIdentity.forProject("macos:project-a")
        val a2 = MacComposeProjectIdentity.forProject("macos:project-a")
        val b = MacComposeProjectIdentity.forProject("macos:project-b")

        assertEquals(a1, a2)
        assertNotEquals(a1, b)
        assertTrue(a1.startsWith("siftalpha-"))
    }

    @Test
    fun selectorPrefersReadyDockerAndRequiresCompose() {
        val docker = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.DOCKER,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/usr/local/bin/docker",
            composeAvailable = true,
        )
        val podman = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.PODMAN,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/usr/local/bin/podman",
            composeAvailable = true,
        )

        val selected = MacComposeProviderSelector.select(listOf(podman, docker))
        assertNotNull(selected)
        assertEquals(MacContainerProviderKind.DOCKER, selected?.snapshot?.kind)

        assertNull(
            MacComposeProviderSelector.select(
                listOf(docker.copy(composeAvailable = false)),
            ),
        )
    }

    @Test
    fun advisorDistinguishesReadyInstalledButStoppedAndMissingProvider() {
        val ready = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.DOCKER,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/usr/local/bin/docker",
            composeAvailable = true,
        )
        assertEquals(
            MacContainerAdviceState.READY,
            MacContainerEnvironmentAdvisor.advise(healthyFacts, listOf(ready)).state,
        )

        val stopped = ready.copy(
            availability = CapabilityAvailability.UNKNOWN,
            composeAvailable = true,
        )
        assertEquals(
            MacContainerAdviceState.START_EXISTING_PROVIDER,
            MacContainerEnvironmentAdvisor.advise(healthyFacts, listOf(stopped)).state,
        )

        val constrained = healthyFacts.copy(
            processorCount = 2,
            physicalMemoryBytes = 6L * 1024L * 1024L * 1024L,
            usableDiskBytes = 15L * 1024L * 1024L * 1024L,
        )
        val missing = MacContainerEnvironmentAdvisor.advise(constrained, emptyList())
        assertEquals(MacContainerAdviceState.RESOURCE_WARNING, missing.state)
        assertTrue(missing.suggestedOptions.isNotEmpty())
        assertTrue(missing.warnings.size >= 2)
    }
}
