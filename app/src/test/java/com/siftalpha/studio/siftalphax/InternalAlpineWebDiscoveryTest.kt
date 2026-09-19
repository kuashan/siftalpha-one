package com.siftalpha.studio.siftalphax

import com.siftalpha.studio.runtime.RuntimeWebCandidateSource
import com.siftalpha.studio.runtime.RuntimeWebDiscoveryScopePolicy
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalAlpineWebDiscoveryTest {
    @Test
    fun explicitInternalOutputKeepsUsingExistingDiscoveryPolicy() {
        val candidate = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = "SIFTALPHA_WEB_URL=http://127.0.0.1:8080/",
            webCapabilityEnabled = false,
        )

        assertEquals("http://127.0.0.1:8080/", candidate?.url)
        assertEquals(RuntimeWebCandidateSource.EXPLICIT, candidate?.source)
    }

    @Test
    fun ordinaryInternalOutputKeepsUsingExistingDiscoveryPolicy() {
        val candidate = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = "server listening on http://127.0.0.1:8080/",
            webCapabilityEnabled = true,
        )

        assertEquals("http://127.0.0.1:8080/", candidate?.url)
        assertEquals(RuntimeWebCandidateSource.RUNTIME_LOG, candidate?.source)
    }

    @Test
    fun emptyOutputUsesOnlyProjectOwnedListenerSockets() {
        val proc = fakeProcRoot()
        try {
            projectTree(proc, rootPid = 100, childPid = 101)
            socketFd(proc, pid = 101, fd = 3, inode = 12345)
            socketFd(proc, pid = 999, fd = 4, inode = 54321)
            writeTcpTables(
                proc = proc,
                rows = listOf(
                    tcpRow(port = 8080, inode = 12345),
                    tcpRow(port = 9000, inode = 54321),
                ),
            )

            val observation = InternalAlpineWebDiscovery.observe(proc, rootPid = 100)

            assertEquals(listOf(8080), observation.ports)
            assertEquals(2, observation.projectPidCount)
            assertEquals(1, observation.socketInodeCount)
            assertEquals(
                listOf(
                    "SIFTALPHA_X_INTERNAL_WEB_DISCOVERY=PASS",
                    "SIFTALPHA_X_INTERNAL_WEB_PORT=8080",
                    "SIFTALPHA_X_INTERNAL_WEB_SOURCE=PROJECT_PID_SOCKET",
                ),
                observation.diagnosticLines(),
            )
        } finally {
            proc.deleteRecursively()
        }
    }

    @Test
    fun unrelatedPidListenerCannotBecomeCurrentProjectCandidate() {
        val proc = fakeProcRoot()
        try {
            projectTree(proc, rootPid = 100, childPid = 101)
            socketFd(proc, pid = 999, fd = 4, inode = 54321)
            writeTcpTables(proc, rows = listOf(tcpRow(port = 8080, inode = 54321)))

            val observation = InternalAlpineWebDiscovery.observe(proc, rootPid = 100)

            assertTrue(observation.ports.isEmpty())
            assertEquals(0, observation.socketInodeCount)
        } finally {
            proc.deleteRecursively()
        }
    }

    @Test
    fun noListeningSocketDoesNotCreateFakeCandidate() {
        val proc = fakeProcRoot()
        try {
            projectTree(proc, rootPid = 100, childPid = 101)
            socketFd(proc, pid = 101, fd = 3, inode = 12345)
            writeTcpTables(proc, rows = listOf(tcpRow(port = 8080, inode = 12345, state = "01")))

            val observation = InternalAlpineWebDiscovery.observe(proc, rootPid = 100)

            assertTrue(observation.ports.isEmpty())
            assertEquals(
                listOf("SIFTALPHA_X_INTERNAL_WEB_DISCOVERY=NO_CANDIDATE"),
                observation.diagnosticLines(),
            )
        } finally {
            proc.deleteRecursively()
        }
    }

    @Test
    fun staleSessionOrGenerationCannotPublishListenerObservation() {
        assertFalse(
            InternalAlpineWebDiscovery.belongsToExecution(
                expectedSessionId = "session-a",
                expectedGeneration = 1L,
                currentSessionId = "session-b",
                currentGeneration = 2L,
            ),
        )
        assertFalse(
            InternalAlpineWebDiscovery.belongsToExecution(
                expectedSessionId = "session-a",
                expectedGeneration = 1L,
                currentSessionId = "session-a",
                currentGeneration = 2L,
            ),
        )
        assertTrue(
            InternalAlpineWebDiscovery.belongsToExecution(
                expectedSessionId = "session-a",
                expectedGeneration = 1L,
                currentSessionId = "session-a",
                currentGeneration = 1L,
            ),
        )
    }

    private fun fakeProcRoot(): File = Files.createTempDirectory("siftalpha-internal-web-proc").toFile()

    private fun projectTree(proc: File, rootPid: Int, childPid: Int) {
        File(proc, "$rootPid/task/$rootPid/children").apply {
            parentFile.mkdirs()
            writeText("$childPid\n")
        }
        File(proc, childPid.toString()).mkdirs()
    }

    private fun socketFd(proc: File, pid: Int, fd: Int, inode: Long) {
        val directory = File(proc, "$pid/fd").apply { mkdirs() }
        Files.createSymbolicLink(
            File(directory, fd.toString()).toPath(),
            java.nio.file.Paths.get("socket:[$inode]"),
        )
    }

    private fun writeTcpTables(proc: File, rows: List<String>) {
        File(proc, "net/tcp").apply {
            parentFile.mkdirs()
            writeText("header\n" + rows.joinToString("\n") + "\n")
        }
    }

    private fun tcpRow(port: Int, inode: Long, state: String = "0A"): String =
        "0: 0100007F:${port.toString(16).uppercase().padStart(4, '0')} " +
            "00000000:0000 $state 00000000:00000000 00:00000000 00000000 0 0 $inode"
}
