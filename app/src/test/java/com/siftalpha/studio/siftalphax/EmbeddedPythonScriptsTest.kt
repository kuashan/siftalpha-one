package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonScriptsTest {
    @Test
    fun includesThreeFixedReviewableScenarios() {
        assertTrue(EmbeddedPythonScenario.entries.map { it.name }.containsAll(listOf(
            "NORMAL",
            "STDOUT_STDERR_FAILURE",
            "LONG_RUNNING",
        )))

        val normal = EmbeddedPythonScenario.NORMAL.script
        assertTrue(normal.contains("SIFTALPHA_X_PYTHON_OK"))
        assertTrue(normal.contains("sys.version"))
        assertTrue(normal.contains("sys.platform"))
        assertTrue(normal.contains("os.getcwd()"))
        assertTrue(normal.contains("sys.path"))
        assertTrue(normal.contains("import json"))

        val failure = EmbeddedPythonScenario.STDOUT_STDERR_FAILURE.script
        assertTrue(failure.contains("SIFTALPHA_X_TEST_B_STDOUT"))
        assertTrue(failure.contains("SIFTALPHA_X_TEST_B_STDERR"))
        assertTrue(failure.contains("raise RuntimeError"))

        val longRunning = EmbeddedPythonScenario.LONG_RUNNING.script
        assertTrue(longRunning.contains("SIFTALPHA_X_TEST_C_STARTED"))
        assertTrue(longRunning.contains("while True"))
        assertTrue(longRunning.contains("KeyboardInterrupt"))
        assertTrue(longRunning.contains("time.sleep"))
    }

    @Test
    fun scriptsDoNotExposeArbitraryExecutionOrExternalRuntimeCommands() {
        EmbeddedPythonScenario.entries.forEach { scenario ->
            val script = scenario.script.lowercase()
            assertFalse(script.contains("eval("))
            assertFalse(script.contains("exec("))
            assertFalse(script.contains("subprocess"))
            assertFalse(script.contains("os.system"))
            assertFalse(script.contains("termux"))
            assertFalse(script.contains("run_command"))
            assertFalse(script.contains("proot"))
            assertFalse(script.contains("apt"))
        }
    }
}
