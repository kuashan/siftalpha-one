package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonResultTest {
    @Test
    fun parsesSuccessfulSnapshot() {
        val snapshot = EmbeddedPythonSnapshotParser.parse(
            """
            {
              "sessionId":"siftalpha-x-a",
              "generation":1,
              "state":"SUCCEEDED",
              "startedAtEpochMs":10,
              "finishedAtEpochMs":20,
              "exitCode":0,
              "stdout":"SIFTALPHA_X_PYTHON_OK\\n",
              "stderr":""
            }
            """.trimIndent(),
        )

        assertEquals("siftalpha-x-a", snapshot.sessionId)
        assertEquals(1L, snapshot.generation)
        assertEquals(EmbeddedPythonState.SUCCEEDED, snapshot.state)
        assertEquals(10L, snapshot.startedAtEpochMs)
        assertEquals(20L, snapshot.finishedAtEpochMs)
        assertEquals(0, snapshot.exitCode)
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PYTHON_OK"))
        assertTrue(snapshot.stderr.isEmpty())
    }

    @Test
    fun preservesFailureOutputAndNullableRunningFields() {
        val snapshot = EmbeddedPythonSnapshotParser.parse(
            """
            {"sessionId":"siftalpha-x-b","generation":2,"state":"FAILED",
             "startedAtEpochMs":30,"finishedAtEpochMs":null,"exitCode":1,
             "stdout":"out","stderr":"traceback"}
            """.replace("\n", ""),
        )

        assertEquals(EmbeddedPythonState.FAILED, snapshot.state)
        assertEquals(1, snapshot.exitCode)
        assertEquals("out", snapshot.stdout)
        assertEquals("traceback", snapshot.stderr)
        assertNull(snapshot.finishedAtEpochMs)
    }

    @Test
    fun policyAllowsOnlyIdleStartAndRunningStop() {
        assertTrue(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.IDLE))
        assertFalse(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.RUNNING))
        assertTrue(EmbeddedPythonStatePolicy.canStop(EmbeddedPythonState.STARTING))
        assertTrue(EmbeddedPythonStatePolicy.canStop(EmbeddedPythonState.RUNNING))
        assertFalse(EmbeddedPythonStatePolicy.canStop(EmbeddedPythonState.SUCCEEDED))
        assertTrue(EmbeddedPythonStatePolicy.isTerminal(EmbeddedPythonState.STOPPED))
        assertFalse(EmbeddedPythonStatePolicy.isTerminal(EmbeddedPythonState.IDLE))
    }
}
