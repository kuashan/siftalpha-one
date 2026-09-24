package com.siftalpha.macos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacHostRuntimeDiscoveryTest {
    @Test
    fun discoveryReturnsFactsForEveryM3HostTool() {
        val results = MacHostRuntimeDiscovery().discoverAll()
        assertEquals(MacHostToolKind.entries.toSet(), results.map { it.kind }.toSet())
        results.filter { it.availability == MacHostToolAvailability.AVAILABLE }.forEach {
            assertNotNull(it.executablePath)
            assertTrue(!it.version.isNullOrBlank())
        }
    }

    @Test
    fun pythonPolicyAcceptsPython3AndRejectsLegacyPython2() {
        assertTrue(MacHostDiscoveryPolicy.acceptsVersion(MacHostToolKind.PYTHON, "Python 3.11.9"))
        assertFalse(MacHostDiscoveryPolicy.acceptsVersion(MacHostToolKind.PYTHON, "Python 2.7.16"))
    }

    @Test
    fun developerToolShimsAreSkippedWhenCommandLineToolsAreMissing() {
        assertTrue(
            MacHostDiscoveryPolicy.shouldSkipDeveloperToolStub(
                MacHostToolKind.PYTHON,
                "/usr/bin/python3",
                developerToolsAvailable = false,
            ),
        )
        assertTrue(
            MacHostDiscoveryPolicy.shouldSkipDeveloperToolStub(
                MacHostToolKind.GIT,
                "/usr/bin/git",
                developerToolsAvailable = false,
            ),
        )
        assertFalse(
            MacHostDiscoveryPolicy.shouldSkipDeveloperToolStub(
                MacHostToolKind.GIT,
                "/usr/local/bin/git",
                developerToolsAvailable = false,
            ),
        )
    }

    @Test
    fun developerToolShimsCanBeProbedWhenCommandLineToolsAreConfigured() {
        assertFalse(
            MacHostDiscoveryPolicy.shouldSkipDeveloperToolStub(
                MacHostToolKind.PYTHON,
                "/usr/bin/python3",
                developerToolsAvailable = true,
            ),
        )
        assertFalse(
            MacHostDiscoveryPolicy.shouldSkipDeveloperToolStub(
                MacHostToolKind.GIT,
                "/usr/bin/git",
                developerToolsAvailable = true,
            ),
        )
    }

    @Test
    fun macOSSystemGitIsDetectedOnTheCloudHost() {
        val git = MacHostRuntimeDiscovery().discover(MacHostToolKind.GIT)
        assertEquals(MacHostToolAvailability.AVAILABLE, git.availability)
        assertTrue(git.executablePath?.endsWith("/git") == true)
    }

    @Test
    fun macOSSystemShellIsDetectedOnTheCloudHost() {
        val shell = MacHostRuntimeDiscovery().discover(MacHostToolKind.SHELL)
        assertEquals(MacHostToolAvailability.AVAILABLE, shell.availability)
        assertTrue(
            shell.executablePath?.let {
                it.endsWith("/zsh") || it.endsWith("/bash") || it.endsWith("/sh")
            } == true,
        )
    }
}
