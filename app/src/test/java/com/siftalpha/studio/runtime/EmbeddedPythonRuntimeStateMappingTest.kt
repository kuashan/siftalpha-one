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
    fun manualStatusRefreshIsNotDroppedWhenSnapshotIsStable() {
        val snapshot = EmbeddedPythonSnapshot(
            projectIdentity = "doc",
            state = EmbeddedPythonState.RUNNING,
            stdout = "steady",
        )

        assertFalse(
            EmbeddedPythonObservationPolicy.shouldPresent(
                previous = snapshot,
                current = snapshot,
                manualAction = null,
            ),
        )
        assertTrue(
            EmbeddedPythonObservationPolicy.shouldPresent(
                previous = snapshot,
                current = snapshot,
                manualAction = EmbeddedPythonObservationPolicy.ManualAction.STATUS,
            ),
        )
        val output = EmbeddedPythonObservationPolicy.outputText(
            snapshot,
            EmbeddedPythonObservationPolicy.ManualAction.STATUS,
        )
        assertTrue(output.contains("SIFTALPHA_X_STATUS_CHECK=PASS"))
        assertTrue(output.contains("SIFTALPHA_X_STATUS_SOURCE=INTERNAL_SNAPSHOT"))
    }

    @Test
    fun outputOnlyGrowthDoesNotRequireRuntimeCenterCardRebuild() {
        val previous = EmbeddedPythonSnapshot(
            projectIdentity = "doc",
            sessionId = "session-1",
            generation = 3,
            state = EmbeddedPythonState.RUNNING,
            stdout = "attempt 1",
        )
        val current = previous.copy(
            stdout = "attempt 1\nattempt 2",
            stderr = "diagnostic tail",
        )

        assertTrue(EmbeddedPythonObservationPolicy.shouldPresent(previous, current, null))
        assertFalse(EmbeddedPythonObservationPolicy.requiresCardRefresh(previous, current))
        assertTrue(
            EmbeddedPythonObservationPolicy.requiresCardRefresh(
                previous,
                current.copy(state = EmbeddedPythonState.SUCCEEDED, exitCode = 0),
            ),
        )
        assertTrue(
            EmbeddedPythonObservationPolicy.requiresCardRefresh(
                previous,
                current.copy(sessionId = "session-2", generation = 4),
            ),
        )
    }

    @Test
    fun liveOutputRenderingIsThrottledButManualAndTerminalUpdatesAreImmediate() {
        assertFalse(
            EmbeddedPythonObservationPolicy.shouldRenderOutput(
                lastRenderedAtEpochMs = 1_000L,
                nowEpochMs = 1_500L,
                intervalMs = 750L,
                manualAction = null,
                structuralChanged = false,
                active = true,
            ),
        )
        assertTrue(
            EmbeddedPythonObservationPolicy.shouldRenderOutput(
                lastRenderedAtEpochMs = 1_000L,
                nowEpochMs = 1_750L,
                intervalMs = 750L,
                manualAction = null,
                structuralChanged = false,
                active = true,
            ),
        )
        assertTrue(
            EmbeddedPythonObservationPolicy.shouldRenderOutput(
                lastRenderedAtEpochMs = 1_700L,
                nowEpochMs = 1_750L,
                intervalMs = 750L,
                manualAction = EmbeddedPythonObservationPolicy.ManualAction.LOGS,
                structuralChanged = false,
                active = true,
            ),
        )
        assertTrue(
            EmbeddedPythonObservationPolicy.shouldRenderOutput(
                lastRenderedAtEpochMs = 1_700L,
                nowEpochMs = 1_750L,
                intervalMs = 750L,
                manualAction = null,
                structuralChanged = false,
                active = false,
            ),
        )
    }

    @Test
    fun activeEmbeddedStateCannotStartAnotherSession() {
        assertFalse(com.siftalpha.studio.siftalphax.EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.RUNNING))
        assertTrue(com.siftalpha.studio.siftalphax.EmbeddedPythonStatePolicy.canStart(EmbeddedPythonState.STOPPED))
    }
}
