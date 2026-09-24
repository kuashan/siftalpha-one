package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacContainerRuntimeDiscoveryTest {
    @Test
    fun dockerRuntimeAndComposeCanBeDiscoveredWithoutPlatformBranchingInCore() {
        val temp = Files.createTempDirectory("siftalpha-docker-discovery-").toFile()
        try {
            val docker = temp.resolve("docker")
            docker.writeText(
                """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "Docker version 28.0.0"
                  exit 0
                fi
                if [ "$1" = "info" ]; then
                  echo "28.0.0"
                  exit 0
                fi
                if [ "$1" = "compose" ]; then
                  echo "Docker Compose version v2.39.0"
                  exit 0
                fi
                exit 2
                """.trimIndent() + "\n",
            )
            assertTrue(docker.setExecutable(true))

            val snapshot = MacContainerRuntimeDiscovery(
                environment = mapOf("PATH" to temp.absolutePath),
                userHome = temp,
            ).discover(MacContainerProviderKind.DOCKER)

            assertEquals(CapabilityAvailability.AVAILABLE, snapshot.availability)
            assertTrue(snapshot.composeAvailable)
            assertTrue(snapshot.version?.contains("Docker version") == true)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun installedButUnreachableProviderIsUnknownRatherThanUnavailable() {
        val temp = Files.createTempDirectory("siftalpha-podman-discovery-").toFile()
        try {
            val podman = temp.resolve("podman")
            podman.writeText(
                """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "podman version 5.6.0"
                  exit 0
                fi
                if [ "$1" = "compose" ]; then
                  echo "podman compose version 1.4.0"
                  exit 0
                fi
                exit 125
                """.trimIndent() + "\n",
            )
            assertTrue(podman.setExecutable(true))

            val snapshot = MacContainerRuntimeDiscovery(
                environment = mapOf("PATH" to temp.absolutePath),
                userHome = temp,
            ).discover(MacContainerProviderKind.PODMAN)

            assertEquals(CapabilityAvailability.UNKNOWN, snapshot.availability)
            assertTrue(snapshot.composeAvailable)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun providerAggregationUsesAvailableUnknownUnavailableSemantics() {
        val available = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.DOCKER,
            availability = CapabilityAvailability.AVAILABLE,
        )
        val unknown = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.PODMAN,
            availability = CapabilityAvailability.UNKNOWN,
        )

        assertEquals(
            CapabilityAvailability.AVAILABLE,
            MacContainerRuntimeDiscovery.capabilityAvailability(listOf(available, unknown)),
        )
        assertEquals(
            CapabilityAvailability.UNKNOWN,
            MacContainerRuntimeDiscovery.capabilityAvailability(listOf(unknown)),
        )
        assertEquals(
            CapabilityAvailability.UNAVAILABLE,
            MacContainerRuntimeDiscovery.capabilityAvailability(emptyList()),
        )
    }
}
