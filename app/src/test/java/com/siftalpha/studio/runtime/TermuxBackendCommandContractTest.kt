package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxBackendCommandContractTest {

    @Test
    fun `Termux setup and probe use POSIX whitespace matching`() {
        val setup = TermuxBackend.FIRST_RUN_SETUP_COMMAND
        val probe = TermuxBackend.CONNECTION_TEST.shellScript

        assertTrue(setup.contains("[[:space:]]*allow-external-apps"))
        assertTrue(probe.contains("[[:space:]]*allow-external-apps"))
        assertFalse(setup.contains("\\\\s*allow-external-apps"))
        assertFalse(probe.contains("\\\\s*allow-external-apps"))
    }
}
