package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebLearnedEndpointPolicyTest {

    @Test
    fun loopbackVerifiedPortCanBeLearned() {
        assertEquals(9127, RuntimeWebLearnedEndpointPolicy.port("http://127.0.0.1:9127"))
        assertEquals(8000, RuntimeWebLearnedEndpointPolicy.port("http://localhost:8000/path"))
        assertEquals(7860, RuntimeWebLearnedEndpointPolicy.port("http://[::1]:7860"))
    }

    @Test
    fun externalOrAmbiguousEndpointsCannotBeLearned() {
        assertNull(RuntimeWebLearnedEndpointPolicy.port("https://example.com:443"))
        assertNull(RuntimeWebLearnedEndpointPolicy.port("http://127.0.0.1"))
        assertNull(RuntimeWebLearnedEndpointPolicy.port("http://user@127.0.0.1:9000"))
    }

    @Test
    fun learnedEndpointRequiresMatchingCurrentWebAuthority() {
        val current = RuntimeWebLearnedEndpointPolicy.authorityFingerprint(
            enabled = true,
            framework = "python-vite-web",
            source = "signature-project-script+web-extra+vite+web-subcommand",
            host = null,
            detectedPort = 8000,
        )
        val same = RuntimeWebLearnedEndpointPolicy.authorityFingerprint(
            enabled = true,
            framework = "python-vite-web",
            source = "signature-project-script+web-extra+vite+web-subcommand",
            host = null,
            detectedPort = 8000,
        )
        val changed = RuntimeWebLearnedEndpointPolicy.authorityFingerprint(
            enabled = true,
            framework = "python-vite-web",
            source = "signature-project-script+web-extra+vite+web-subcommand",
            host = null,
            detectedPort = 5173,
        )

        assertTrue(RuntimeWebLearnedEndpointPolicy.reusable(current, same))
        assertFalse(RuntimeWebLearnedEndpointPolicy.reusable(current, changed))
        assertFalse(RuntimeWebLearnedEndpointPolicy.reusable("", same))
    }

    @Test
    fun onlyPidSocketOwnedCandidateCanBecomeLongTermMemory() {
        assertTrue(
            RuntimeWebLearnedEndpointPolicy.canLearn(
                RuntimeWebCandidateSource.PID_SOCKET,
                "http://127.0.0.1:9234",
            ),
        )
        assertFalse(
            RuntimeWebLearnedEndpointPolicy.canLearn(
                RuntimeWebCandidateSource.RUNTIME_LOG,
                "http://127.0.0.1:9234",
            ),
        )
        assertFalse(
            RuntimeWebLearnedEndpointPolicy.canLearn(
                RuntimeWebCandidateSource.EXPLICIT,
                "http://127.0.0.1:9234",
            ),
        )
    }
}
