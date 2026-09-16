package com.siftalpha.studio.runtime

import com.siftalpha.studio.siftalphax.EmbeddedPythonSnapshot
import com.siftalpha.studio.siftalphax.EmbeddedPythonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonRuntimeStateMappingTest {
    @Test
    fun mapsStructuredStatesWithoutMarkerRoundTrip() {
        assertEquals(RuntimeState.UNKNOWN, EmbeddedPythonRuntimeStateMapping.toRuntimeState(EmbeddedPythonState.IDLE))
        assertEquals(RuntimeState.STARTING, EmbeddedPythonRuntimeStateMapping.toRuntimeState(EmbeddedPythonState.STARTING))
        assertEquals(RuntimeState.RUNNING, EmbeddedPythonRuntimeStateMapping.toRuntimeState(EmbeddedPythonState.RUNNING))
        assertEquals(RuntimeState.EXITED_SUCCESS, EmbeddedPythonRuntimeStateMapping.toRuntimeState(EmbeddedPythonState.SUCCEEDED))
        assertEquals(RuntimeState.EXITED_ERROR, EmbeddedPythonRuntimeStateMapping.toRuntimeState(EmbeddedPythonState.FAILED))
        assertEquals(RuntimeState.STOPPED_BY_USER, EmbeddedPythonRuntimeStateMapping.toRuntimeState(EmbeddedPythonState.STOPPED))
    }

    @Test
    fun retainsProjectSessionGenerationAndOutputFacts() {
        val snapshot = EmbeddedPythonSnapshot(
            projectIdentity = "saf-document-id",
            executionRoot = "/data/user/0/app/files/siftalphax/projects/session",
            entrypoint = "/data/user/0/app/files/siftalphax/projects/session/main.py",
            workingDirectory = "/data/user/0/app/files/siftalphax/projects/session",
            sessionId = "siftalpha-x-session",
            generation = 4L,
            state = EmbeddedPythonState.SUCCEEDED,
            stdout = "PROJECT_OUTPUT",
            stderr = "PROJECT_ERROR",
            exitCode = 0,
        )
        val output = EmbeddedPythonRuntimeStateMapping.outputText(snapshot)

        assertEquals(RuntimeState.EXITED_SUCCESS, EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot))
        assertTrue(output.contains("SIFTALPHA_X_PROJECT_ID=saf-document-id"))
        assertTrue(output.contains("SIFTALPHA_X_SESSION_ID=siftalpha-x-session"))
        assertTrue(output.contains("SIFTALPHA_X_GENERATION=4"))
        assertTrue(output.contains("PROJECT_OUTPUT"))
        assertTrue(output.contains("PROJECT_ERROR"))
        assertFalse(output.contains("executionId ="))
    }

    @Test
    fun activeEmbeddedStateCannotStartAnotherSession() {
        assertFalse(com.siftalpha.studio.siftalphax.EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.RUNNING))
        assertTrue(com.siftalpha.studio.siftalphax.EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.STOPPED))
    }
}
