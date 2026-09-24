package com.siftalpha.core.workflow

import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
import com.siftalpha.studio.runtime.RuntimeWebEndpointProbe
import com.siftalpha.studio.runtime.RuntimeWebUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedProjectWorkflowPolicyTest {
    @Test
    fun resolvesConventionalPythonEntrypoint() {
        assertEquals(
            "main.py",
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = null,
                filePaths = listOf("requirements.txt", "main.py"),
            ),
        )
    }

    @Test
    fun rejectsUnsafePythonEntrypoint() {
        assertEquals(
            null,
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = "../main.py",
                filePaths = listOf("main.py"),
            ),
        )
    }

    @Test
    fun normalizesWildcardLoopbackUrl() {
        assertEquals(
            "http://127.0.0.1:8765",
            RuntimeWebUrl.extractLocalHttpUrl("Running on http://0.0.0.0:8765"),
        )
    }

    @Test
    fun endpointProbeRejectsExternalHost() {
        assertFalse(RuntimeWebEndpointProbe.isListening("https://example.com", timeoutMs = 10))
        assertTrue(RuntimeWebEndpointProbe.targets("http://127.0.0.1:8765").isNotEmpty())
    }
}
