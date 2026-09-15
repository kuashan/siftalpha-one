package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebPortDiscoveryTest {

    @Test
    fun preferredFrameworkPortsAreRankedBeforeArbitraryPorts() {
        assertEquals(
            listOf(5173, 3000, 8000, 8501, 7860, 5000, 12345, 49152),
            RuntimeWebPortDiscovery.rankCandidates(
                listOf(49152, 5000, 12345, 8000, 7860, 8501, 3000, 5173, 8000),
            ),
        )
    }

    @Test
    fun invalidPortsAreDiscarded() {
        assertEquals(
            listOf(8080, 65535),
            RuntimeWebPortDiscovery.rankCandidates(listOf(-1, 0, 65536, 65535, 8080)),
        )
    }

    @Test
    fun noProjectPidsAreDiagnosed() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.NO_PROJECT_PIDS,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(projectPidCount = 0),
            ),
        )
    }

    @Test
    fun procfsUnreadableIsDiagnosed() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.PROCFS_UNREADABLE,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(procfsReadable = false),
            ),
        )
    }

    @Test
    fun pidWithoutSocketInodesIsDiagnosed() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.NO_SOCKET_INODES,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(socketInodeCount = 0),
            ),
        )
    }

    @Test
    fun socketWithoutInodeMatchIsDiagnosed() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.NO_INODE_MATCH,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(inodeMatchCount = 0),
            ),
        )
    }

    @Test
    fun matchingSocketWithoutListenPortIsDiagnosed() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.NO_LISTEN_PORT,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(listenPortCount = 0),
            ),
        )
    }

    @Test
    fun listenPortIsReportedBeforeHttpProbe() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.LISTEN_PORT_FOUND,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(httpEndpointReachable = null),
            ),
        )
    }

    @Test
    fun unreachableHttpEndpointIsDiagnosed() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.NO_HTTP_ENDPOINT,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(httpEndpointReachable = false),
            ),
        )
    }

    @Test
    fun reachableHttpEndpointPasses() {
        assertEquals(
            RuntimeWebDiscoveryDiagnosticStatus.PASS,
            RuntimeWebPortDiscovery.diagnosticStatus(
                observation(httpEndpointReachable = true),
            ),
        )
    }

    @Test
    fun shellProbeKeepsHostFastPathAndAddsProjectScopedProotFallback() {
        val script = RuntimeWebPortDiscovery.shellSnippet()

        assertTrue("PID descendants must scope host discovery", "siftalpha_descendants" in script)
        assertTrue("PGID members must be considered", "ps -eo pid=,pgid=" in script)
        assertTrue("TCP listeners should remain available as the fast path", "/proc/net/tcp" in script)
        assertTrue("Only LISTEN state should be selected", "== \"0A\"" in script)
        assertTrue("HTTP verification must be tightly bounded", "timeout 1" in script)
        assertTrue("runtime candidate probing must be capped", "SIFTALPHA_WEB_RUNTIME_CANDIDATE_LIMIT=REACHED" in script)
        assertTrue("PASS marker missing", "SIFTALPHA_WEB_AUTODISCOVERY=PASS" in script)
        assertTrue("URL marker missing", "SIFTALPHA_WEB_URL=http://127.0.0.1:" in script)
        assertTrue("PRoot fallback must run from Ubuntu guest", "proot-distro login" in script)
        assertTrue("PRoot fallback must remain project PID scoped", "PROOT_PROJECT_PID_SCOPE" in script)
        assertTrue("guest descendants must not scan unrelated processes", "/task/" in script && "/children" in script)
        assertTrue("guest discovery must consume the generic runtime identity", "siftalpha-web identity" in script)
        assertTrue("guest root PID must come from the identity sidecar", RuntimeIdentityStore.GUEST_ROOT_PID_KEY in script)
        assertTrue("guest root PGID must come from the identity sidecar", RuntimeIdentityStore.GUEST_ROOT_PGID_KEY in script)
        assertTrue("identity sidecar must use the neutral guest mount", RuntimeIdentityStore.GUEST_RUNTIME_ROOT in script)
        assertTrue("invalid identity must fail closed", "RUNTIME_IDENTITY_INVALID" in script)
        assertTrue("legacy PID fallback must remain available", "siftalpha-web legacy" in script)
        assertTrue("partial identity mode must be explicit", "identity_mode='partial_identity'" in script)
        assertTrue("full identity resolution must be visible", "SIFTALPHA_RUNTIME_IDENTITY_RESOLUTION=FULL_IDENTITY" in script)
        assertTrue("host-only identity resolution must be visible", "identity_resolution='HOST_ONLY'" in script)
        assertTrue("guest-only identity resolution must be visible", "identity_resolution='GUEST_ONLY'" in script)
        assertTrue("metadata mismatch resolution must be visible", "SIFTALPHA_RUNTIME_IDENTITY_RESOLUTION=METADATA_MISMATCH" in script)
        assertTrue("legacy identity resolution must be visible", "identity_resolution='LEGACY_PID'" in script)
        assertTrue("unavailable identity resolution must be visible", "identity_resolution='UNAVAILABLE'" in script)
        assertTrue("full identity usage must be visible", "SIFTALPHA_RUNTIME_IDENTITY_SOURCE=FULL_IDENTITY" in script)
        assertTrue("legacy identity usage must be visible", "SIFTALPHA_RUNTIME_IDENTITY_SOURCE=LEGACY_PID" in script)
        assertTrue("unavailable identity usage must be visible", "SIFTALPHA_RUNTIME_IDENTITY_SOURCE=UNAVAILABLE" in script)
        assertTrue("partial identity may use legacy only through the explicit fallback", "identity_mode" in script && "partial_identity" in script && "siftalpha_web_legacy_fallback" in script)
        assertTrue("partial resolution must not be reported as source", "SIFTALPHA_RUNTIME_IDENTITY_SOURCE=HOST_ONLY" !in script)
        assertTrue("partial resolution must not be reported as source", "SIFTALPHA_RUNTIME_IDENTITY_SOURCE=GUEST_ONLY" !in script)
        assertTrue("legacy fallback must not be silent", "SIFTALPHA_RUNTIME_IDENTITY_FALLBACK=LEGACY_PID" in script)
        assertTrue("guest root liveness must be diagnosed", "SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=ALIVE" in script)
        assertTrue("dead guest root must be diagnosed", "SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=NOT_ALIVE" in script)
        assertTrue("terminal no-listen result should be explicit", "NO_LISTEN_PORT source=PROJECT_AND_PROOT_PID_SCOPE" in script)
        assertTrue("no-PID diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=NO_PROJECT_PIDS" in script)
        assertTrue("procfs diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=PROCFS_UNREADABLE" in script)
        assertTrue("socket diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=NO_SOCKET_INODES" in script)
        assertTrue("inode diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=NO_INODE_MATCH" in script)
        assertTrue("listen diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=NO_LISTEN_PORT" in script)
        assertTrue("listen stage marker missing", "SIFTALPHA_WEB_DISCOVERY_STAGE=LISTEN_FOUND" in script)
        assertTrue("HTTP diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=NO_HTTP_ENDPOINT" in script)
        assertTrue("successful diagnostic marker missing", "SIFTALPHA_WEB_DISCOVERY_STATUS=PASS" in script)

        assertFalse("discovery must not start an active all-port scan", "SIFTALPHA_WEB_ACTIVE_SCAN=START" in script)
        assertFalse("discovery must not embed a Python TCP scanner", "base64.b64decode" in script)
        assertFalse("discovery must not invoke the old active scan source", "ACTIVE_LOOPBACK_SCAN" in script)
        assertFalse("discovery must not use the unsafe Termux UID fallback", "TERMUX_UID_UNIQUE_HTTP" in script)
    }

    private fun observation(
        projectPidCount: Int = 1,
        procfsReadable: Boolean = true,
        socketInodeCount: Int = 1,
        inodeMatchCount: Int = 1,
        listenPortCount: Int = 1,
        httpEndpointReachable: Boolean? = null,
    ) = RuntimeWebDiscoveryObservation(
        projectPidCount = projectPidCount,
        procfsReadable = procfsReadable,
        socketInodeCount = socketInodeCount,
        inodeMatchCount = inodeMatchCount,
        listenPortCount = listenPortCount,
        httpEndpointReachable = httpEndpointReachable,
    )
}
