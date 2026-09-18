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
              "projectIdentity":"fixture-project-a",
              "executionRoot":"/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-a",
              "entrypoint":"/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-a/main.py",
              "workingDirectory":"/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-a",
              "generation":1,
              "state":"SUCCEEDED",
              "startedAtEpochMs":10,
              "finishedAtEpochMs":20,
              "exitCode":0,
              "stdout":"SIFTALPHA_X_PYTHON_OK\n",
              "stderr":""
            }
            """.trimIndent(),
        )

        assertEquals("siftalpha-x-a", snapshot.sessionId)
        assertEquals("fixture-project-a", snapshot.projectIdentity)
        assertTrue(snapshot.executionRoot.endsWith("/projects/siftalpha-x-a"))
        assertTrue(snapshot.entrypoint.endsWith("/projects/siftalpha-x-a/main.py"))
        assertTrue(snapshot.workingDirectory.endsWith("/projects/siftalpha-x-a"))
        assertEquals(1L, snapshot.generation)
        assertEquals(EmbeddedPythonState.SUCCEEDED, snapshot.state)
        assertEquals(EmbeddedPythonStopPhase.IDLE, snapshot.stopPhase)
        assertEquals(EmbeddedPythonStopResult.NONE, snapshot.stopResult)
        assertEquals(10L, snapshot.startedAtEpochMs)
        assertEquals(20L, snapshot.finishedAtEpochMs)
        assertEquals(0, snapshot.exitCode)
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PYTHON_OK"))
        assertTrue(snapshot.stderr.isEmpty())
    }

    @Test
    fun parsesRuntimePhaseAndBuildsCopyableDiagnostics() {
        val snapshot = EmbeddedPythonSnapshotParser.parse(
            """
            {"sessionId":"siftalpha-x-phase","projectIdentity":"fixture-project-b",
             "executionRoot":"/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-phase",
             "entrypoint":"/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-phase/main.py",
             "workingDirectory":"/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-phase",
             "generation":4,"state":"FAILED",
             "runtimePhase":"TERMINAL",
             "stopPhase":"STOP_REQUEST_RETURNED",
             "stopResult":"INTERRUPT_DELIVERED",
             "startedAtEpochMs":30,
             "finishedAtEpochMs":40,"exitCode":1,
             "stdout":"out","stderr":"traceback"}
            """.replace("\n", ""),
        )

        assertEquals(EmbeddedPythonRuntimePhase.TERMINAL, snapshot.runtimePhase)
        assertEquals(EmbeddedPythonStopPhase.STOP_REQUEST_RETURNED, snapshot.stopPhase)
        assertEquals(EmbeddedPythonStopResult.INTERRUPT_DELIVERED, snapshot.stopResult)
        val diagnostics = EmbeddedPythonDiagnosticText.copyAll(snapshot)
        assertTrue(diagnostics.contains("SIFTALPHA_X_PROJECT_ID=fixture-project-b"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_EXECUTION_ROOT=/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-phase"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_ENTRYPOINT=/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-phase/main.py"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_WORKING_DIRECTORY=/data/user/0/com.siftalpha.studio/files/siftalphax/projects/siftalpha-x-phase"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_SESSION_ID=siftalpha-x-phase"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_RUNTIME_PHASE=TERMINAL"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_STOP_PHASE=STOP_REQUEST_RETURNED"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_STOP_RESULT=INTERRUPT_DELIVERED"))
        assertTrue(diagnostics.contains("stdout:\nout"))
        assertTrue(diagnostics.contains("stderr:\ntraceback"))
    }

    @Test
    fun alpineSnapshotReportsTruthfulInternalBackendDiagnostics() {
        val snapshot = EmbeddedPythonSnapshot(
            engine = InternalPythonBackend.ALPINE,
            sessionId = "alpine-a",
            projectIdentity = "project-a",
            state = EmbeddedPythonState.RUNNING,
        )
        val diagnostics = EmbeddedPythonDiagnosticText.copyAll(snapshot)

        assertTrue(diagnostics.contains("SIFTALPHA_X_ENGINE=ALPINE"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_TERMUX=NOT_USED"))
        assertTrue(diagnostics.contains("SIFTALPHA_X_PROOT=INTERNAL"))
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
        assertEquals("", snapshot.projectIdentity)
    }

    @Test
    fun policyAllowsOnlyOneActiveSessionAndRunningStop() {
        assertTrue(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.IDLE))
        assertTrue(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.SUCCEEDED))
        assertTrue(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.FAILED))
        assertTrue(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.STOPPED))
        assertFalse(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.STARTING))
        assertFalse(EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.RUNNING))
        assertTrue(EmbeddedPythonStatePolicy.canStop(EmbeddedPythonState.STARTING))
        assertTrue(EmbeddedPythonStatePolicy.canStop(EmbeddedPythonState.RUNNING))
        assertFalse(EmbeddedPythonStatePolicy.canStop(EmbeddedPythonState.SUCCEEDED))
        assertTrue(EmbeddedPythonStatePolicy.isTerminal(EmbeddedPythonState.STOPPED))
        assertFalse(EmbeddedPythonStatePolicy.isTerminal(EmbeddedPythonState.IDLE))
    }
}
