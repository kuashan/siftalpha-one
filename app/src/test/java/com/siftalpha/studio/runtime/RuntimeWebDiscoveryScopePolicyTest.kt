package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebDiscoveryScopePolicyTest {

    @Test
    fun sherlockProxyHelpIsNotAWebCandidateForNonWebProject() {
        val output = """
            usage: sherlock [options]
            --proxy PROXY_URL
            e.g. socks5://127.0.0.1:1080
        """.trimIndent()

        assertFalse(RuntimeWebDiscoveryScopePolicy.allowRuntimeLogDiscovery(false))
        assertNull(RuntimeWebDiscoveryScopePolicy.candidateFromOutput(output, webCapabilityEnabled = false))
    }

    @Test
    fun ordinaryLocalUrlTextIsNotEnoughForNonWebProject() {
        assertNull(
            RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
                output = "documentation example: http://localhost:8000",
                webCapabilityEnabled = false,
            ),
        )
    }

    @Test
    fun webProjectStillUsesRuntimeLogFallback() {
        val candidate = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = "Server running on http://0.0.0.0:8000",
            webCapabilityEnabled = true,
        )

        assertEquals("http://127.0.0.1:8000", candidate?.url)
        assertEquals(RuntimeWebCandidateSource.RUNTIME_LOG, candidate?.source)
    }

    @Test
    fun webProjectBareLocalhostStillUsesRuntimeLogFallback() {
        val candidate = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = "Server running at localhost:3000",
            webCapabilityEnabled = true,
        )

        assertEquals("http://localhost:3000", candidate?.url)
        assertEquals(RuntimeWebCandidateSource.RUNTIME_LOG, candidate?.source)
    }

    @Test
    fun projectScopedSocketEvidenceRemainsUsableWithoutStaticWebProfile() {
        val candidate = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = """
                SIFTALPHA_WEB_AUTODISCOVERY=PASS source=PROJECT_PID_SCOPE
                SIFTALPHA_WEB_URL=http://127.0.0.1:8000
            """.trimIndent(),
            webCapabilityEnabled = false,
        )

        assertEquals("http://127.0.0.1:8000", candidate?.url)
        assertEquals(RuntimeWebCandidateSource.PID_SOCKET, candidate?.source)
    }

    @Test
    fun nonWebProjectsClearLegacyAndLogCandidatesButRetainSocketCandidates() {
        assertTrue(
            RuntimeWebDiscoveryScopePolicy.shouldClearPersistedCandidate(
                webCapabilityEnabled = false,
                source = RuntimeWebCandidateSource.UNKNOWN,
            ),
        )
        assertTrue(
            RuntimeWebDiscoveryScopePolicy.shouldClearPersistedCandidate(
                webCapabilityEnabled = false,
                source = RuntimeWebCandidateSource.RUNTIME_LOG,
            ),
        )
        assertFalse(
            RuntimeWebDiscoveryScopePolicy.shouldClearPersistedCandidate(
                webCapabilityEnabled = false,
                source = RuntimeWebCandidateSource.PID_SOCKET,
            ),
        )
    }

    @Test
    fun disabledLogShellDoesNotEmitUrlOrRuntimeLogSource() {
        val skipped = RuntimeWebLogDiscoveryShell.shellSnippet(allowRuntimeLogDiscovery = false)
        assertTrue(skipped.contains("SKIPPED_NOT_WEB_PROJECT"))
        assertFalse(skipped.contains("SIFTALPHA_WEB_DISCOVERY_SOURCE=RUNTIME_LOG"))
        assertFalse(skipped.contains("SIFTALPHA_WEB_URL="))

        val allowed = RuntimeWebLogDiscoveryShell.shellSnippet(allowRuntimeLogDiscovery = true)
        assertTrue(allowed.contains("SIFTALPHA_WEB_DISCOVERY_SOURCE=RUNTIME_LOG"))
        assertTrue(allowed.contains("SIFTALPHA_WEB_URL=%s"))
    }
}
