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
    fun checksumParserSelectsExactAssetFromMultiFileManifest() {
        val manifest = """
            bbdef91774885a0d05f7b048c4eb89ae2bcf3a0c252ae7ca7934e63df76d93c3  lima-2.2.0-Darwin-arm64.tar.gz
            0d6f99c19f6e4bc3c92730c4c29d929e6927f0cb0a1ba1a84383367135a8ff31  lima-2.2.0-Darwin-x86_64.tar.gz
        """.trimIndent()

        assertEquals(
            "0d6f99c19f6e4bc3c92730c4c29d929e6927f0cb0a1ba1a84383367135a8ff31",
            MacManagedContainerChecksum.expectedFor(
                manifest,
                "lima-2.2.0-Darwin-x86_64.tar.gz",
            ),
        )
        assertNull(
            MacManagedContainerChecksum.expectedFor(
                manifest,
                "lima-2.2.0-Darwin-riscv64.tar.gz",
            ),
        )
    }

    @Test
    fun checksumParserAcceptsSingleDigestSidecar() {
        val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        assertEquals(
            digest,
            MacManagedContainerChecksum.expectedFor(digest + "\n", "single-asset"),
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
