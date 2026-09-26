package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebHintPolicyTest {

    @Test
    fun currentDetectedPortOutranksLearnedEndpointForServerFramework() {
        val ports = RuntimeWebHintPolicy.ports(
            detectedPort = 9234,
            framework = "fastapi",
            learnedPort = 9127,
        )

        assertEquals(9234, ports.first())
        assertEquals(8000, ports[1])
        assertEquals(9127, ports[2])
        assertTrue(5173 in ports)
        assertEquals(ports.distinct(), ports)
    }

    @Test
    fun pythonViteBrowserUiOutranksDetectedBackendAndLearnedApiPort() {
        val ports = RuntimeWebHintPolicy.ports(
            detectedPort = 8000,
            framework = "python-vite-web",
            learnedPort = 9127,
            detectedSource = "signature-project-script+web-extra+vite+web-subcommand",
        )

        assertEquals(5173, ports.first())
        assertEquals(8000, ports[1])
        assertEquals(9127, ports[2])
    }

    @Test
    fun explicitConfiguredPortRemainsAuthoritativeEvenForViteHybrid() {
        val ports = RuntimeWebHintPolicy.ports(
            detectedPort = 9234,
            framework = "python-vite-web",
            learnedPort = 8000,
            detectedSource = "config",
        )

        assertEquals(9234, ports.first())
        assertEquals(5173, ports[1])
        assertEquals(8000, ports[2])
    }

    @Test
    fun frameworkDefaultWinsWhenNoLearnedOrExplicitPortExists() {
        val ports = RuntimeWebHintPolicy.ports(
            detectedPort = null,
            framework = "flask",
        )

        assertEquals(5000, ports.first())
        assertTrue(8080 in ports)
    }

    @Test
    fun unknownProjectGetsOnlyBoundedCommonHints() {
        val ports = RuntimeWebHintPolicy.ports(
            detectedPort = null,
            framework = null,
        )

        assertEquals(5173, ports.first())
        assertTrue(ports.size <= RuntimeWebHintPolicy.MAX_HINT_PORTS)
    }
}
