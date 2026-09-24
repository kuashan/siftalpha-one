package com.siftalpha.macos

import org.junit.Assert.assertEquals
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
