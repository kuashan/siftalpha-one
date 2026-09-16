package com.siftalpha.studio.siftalphax

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedPythonNativeSmokeTest {
    @Test
    fun longRunningScriptStopsThroughEmbeddedCpython() {
        assumeTrue(
            "The first PoC only packages arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provenance = EmbeddedPythonFiles.provenance(context)
        assertTrue(provenance.contains("CPython version: `3.14.7`"))
        assertTrue(provenance.contains("Target ABI: `arm64-v8a`"))
        assertTrue(provenance.contains("6d50cc3aa66e414a439594089bcdfb5f1264358155c70c1f00471c24cfb477fb"))

        val session = EmbeddedPythonSession.shared(context)
        assumeTrue(
            "A native smoke test session is already active",
            EmbeddedPythonStatePolicy.canStart(session.snapshot().state),
        )
        session.start(EmbeddedPythonScenario.LONG_RUNNING)
        awaitState(session, EmbeddedPythonState.RUNNING, 5_000L)
        assertTrue("The cooperative stop request was not accepted", session.requestStop())
        awaitState(session, EmbeddedPythonState.STOPPED, 5_000L)

        val snapshot = session.snapshot()
        assertEquals(EmbeddedPythonState.STOPPED, snapshot.state)
        assertEquals(130, snapshot.exitCode)
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_TEST_C_STARTED"))
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_TEST_C_COOPERATIVE_STOP"))
    }

    @Test
    fun sequentialSessionsPreserveIdentityAndResults() {
        assumeTrue(
            "The first PoC only packages arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = EmbeddedPythonSession.shared(context)
        assumeTrue(
            "A native smoke test session is already active",
            EmbeddedPythonStatePolicy.canStart(session.snapshot().state),
        )

        session.start(EmbeddedPythonScenario.NORMAL)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val snapshotA = session.snapshot()
        assertEquals(snapshotA, session.snapshot())

        session.start(EmbeddedPythonScenario.STDOUT_STDERR_FAILURE)
        awaitState(session, EmbeddedPythonState.FAILED, 5_000L)
        val snapshotB = session.snapshot()

        assertNotEquals(snapshotA.sessionId, snapshotB.sessionId)
        assertTrue(snapshotB.generation > snapshotA.generation)
        assertEquals(EmbeddedPythonState.SUCCEEDED, snapshotA.state)
        assertTrue(snapshotA.stdout.contains("SIFTALPHA_X_PYTHON_OK"))
        assertEquals(EmbeddedPythonState.FAILED, snapshotB.state)
        assertTrue(snapshotB.stdout.contains("SIFTALPHA_X_TEST_B_STDOUT"))
        assertTrue(snapshotB.stderr.contains("SIFTALPHA_X_TEST_B_STDERR"))
        assertTrue(snapshotB.stderr.contains("SIFTALPHA_X_TEST_B_FAILURE"))
    }

    private fun awaitState(
        session: EmbeddedPythonSession,
        expected: EmbeddedPythonState,
        timeoutMs: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var snapshot = session.snapshot()
        while (snapshot.state != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
            snapshot = session.snapshot()
        }
        assertEquals(expected, snapshot.state)
    }
}
