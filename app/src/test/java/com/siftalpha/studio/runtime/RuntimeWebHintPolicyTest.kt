package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebHintPolicyTest {

    @Test
    fun detectedProjectPortWinsBeforeFrameworkAndCommonFallbacks() {
        val ports = RuntimeWebHintPolicy.ports(
            detectedPort = 9234,
            framework = "fastapi",
        )

        assertEquals(9234, ports.first())
        assertEquals(8000, ports[1])
        assertTrue(5173 in ports)
        assertEquals(ports.distinct(), ports)
    }

    @Test
    fun frameworkDefaultWinsWhenNoExplicitOrSourcePortExists() {
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
        assertEquals(RuntimeWebHintPolicy.MAX_HINT_PORTS, ports.size)
    }
}
