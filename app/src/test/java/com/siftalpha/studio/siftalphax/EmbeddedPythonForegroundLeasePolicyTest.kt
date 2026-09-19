package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonForegroundLeasePolicyTest {

    @Test
    fun blankIdleSnapshotCannotReleaseFreshSessionLease() {
        assertFalse(
            EmbeddedPythonForegroundLeasePolicy.shouldRelease(
                current = EmbeddedPythonSnapshot(state = EmbeddedPythonState.IDLE),
                sessionId = "session-a",
                generation = 7L,
            ),
        )
    }

    @Test
    fun currentSessionReleasesOnlyAtTerminalState() {
        val running = EmbeddedPythonSnapshot(
            sessionId = "session-a",
            generation = 7L,
            state = EmbeddedPythonState.RUNNING,
        )
        val stopped = running.copy(state = EmbeddedPythonState.STOPPED)

        assertFalse(EmbeddedPythonForegroundLeasePolicy.shouldRelease(running, "session-a", 7L))
        assertTrue(EmbeddedPythonForegroundLeasePolicy.shouldRelease(stopped, "session-a", 7L))
    }

    @Test
    fun replacementSessionReleasesOlderLease() {
        assertTrue(
            EmbeddedPythonForegroundLeasePolicy.shouldRelease(
                current = EmbeddedPythonSnapshot(
                    sessionId = "session-b",
                    generation = 8L,
                    state = EmbeddedPythonState.RUNNING,
                ),
                sessionId = "session-a",
                generation = 7L,
            ),
        )
    }
}
