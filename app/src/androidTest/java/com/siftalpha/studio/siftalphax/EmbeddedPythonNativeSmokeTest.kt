package com.siftalpha.studio.siftalphax

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedPythonNativeSmokeTest {
    @Test
    fun fileBackedProjectScriptProvidesPythonFileSemantics() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = readySession(context)

        session.start(EmbeddedPythonProjectFixture.PROJECT_A)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val snapshot = session.snapshot()

        assertEquals(EmbeddedPythonState.SUCCEEDED, snapshot.state)
        assertEquals(0, snapshot.exitCode)
        assertEquals("fixture-project-a", snapshot.projectIdentity)
        assertTrue(snapshot.executionRoot.contains("/siftalphax/projects/"))
        assertTrue(snapshot.entrypoint.endsWith("/main.py"))
        assertEquals(snapshot.executionRoot, snapshot.workingDirectory)
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PROJECT_A_SUCCESS"))
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PROJECT_HELPER=PROJECT_A_HELPER"))
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PROJECT_NAME=__main__"))
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PROJECT_FILE=" + snapshot.entrypoint))
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PROJECT_CWD=" + snapshot.workingDirectory))
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_PROJECT_ARGV0=" + snapshot.entrypoint))
    }

    @Test
    fun fileBackedFailureCapturesRealTracebackFilename() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = readySession(context)

        session.start(EmbeddedPythonProjectFixture.PROJECT_B)
        awaitState(session, EmbeddedPythonState.FAILED, 5_000L)
        val snapshot = session.snapshot()

        assertEquals(1, snapshot.exitCode)
        assertTrue(snapshot.stdout.contains("SIFTALPHA_X_TEST_B_STDOUT"))
        assertTrue(snapshot.stderr.contains("SIFTALPHA_X_TEST_B_STDERR"))
        assertTrue(snapshot.stderr.contains("SIFTALPHA_X_TEST_B_FAILURE"))
        assertTrue(snapshot.stderr.contains(snapshot.entrypoint))
        assertTrue(snapshot.stderr.contains("main.py"))
        assertFalse(snapshot.stderr.contains("<string>"))
    }

    @Test
    fun longRunningFileBackedScriptStopsAndAllowsReentry() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = readySession(context)

        session.start(EmbeddedPythonProjectFixture.PROJECT_A)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val snapshotA = session.snapshot()

        session.start(EmbeddedPythonProjectFixture.PROJECT_C)
        awaitState(session, EmbeddedPythonState.RUNNING, 5_000L)
        awaitRuntimePhase(session, EmbeddedPythonRuntimePhase.PYTHON_EXEC_BEGIN, 5_000L)
        val snapshotC = session.snapshot()
        assertNotEquals(snapshotA.sessionId, snapshotC.sessionId)
        assertTrue(snapshotA.generation < snapshotC.generation)

        assertTrue("The cooperative stop request was not accepted", session.requestStop())
        awaitState(session, EmbeddedPythonState.STOPPED, 5_000L)
        awaitStopDiagnostics(session, 5_000L)

        val stopped = session.snapshot()
        assertEquals(130, stopped.exitCode)
        assertEquals(snapshotC.sessionId, stopped.sessionId)
        assertEquals(snapshotC.generation, stopped.generation)
        assertEquals(EmbeddedPythonRuntimePhase.TERMINAL, stopped.runtimePhase)
        assertEquals(EmbeddedPythonStopResult.INTERRUPT_DELIVERED, stopped.stopResult)
        assertTrue(stopped.stdout.contains("SIFTALPHA_X_TEST_C_COOPERATIVE_STOP"))
        assertTrue(stopped.stderr.contains("SIFTALPHA_X_STOP=COOPERATIVE"))

        session.start(EmbeddedPythonProjectFixture.PROJECT_A)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val snapshotAAgain = session.snapshot()
        assertNotEquals(stopped.sessionId, snapshotAAgain.sessionId)
        assertTrue(stopped.generation < snapshotAAgain.generation)
        assertTrue(snapshotAAgain.stdout.contains("SIFTALPHA_X_PROJECT_A_SUCCESS"))
    }

    @Test
    fun namespaceIsolationUsesFreshMainAndProjectModuleState() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = readySession(context)

        session.start(EmbeddedPythonProjectFixture.PROJECT_A)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val snapshotA = session.snapshot()

        session.start(EmbeddedPythonProjectFixture.PROJECT_D_ISOLATION)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val snapshotD = session.snapshot()

        assertNotEquals(snapshotA.sessionId, snapshotD.sessionId)
        assertTrue(snapshotA.generation < snapshotD.generation)
        assertTrue(snapshotD.stdout.contains("SIFTALPHA_X_PROJECT_D_ISOLATION_OK"))
        assertTrue(snapshotD.stdout.contains("SIFTALPHA_X_PROJECT_HELPER=PROJECT_D_HELPER"))
        assertFalse(snapshotD.stdout.contains("PROJECT_A_HELPER"))
    }

    @Test
    fun systemExitIsMappedWithoutKillingTheRuntime() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = readySession(context)

        session.start(EmbeddedPythonProjectFixture.PROJECT_E_SYSTEM_EXIT_ZERO)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val zero = session.snapshot()
        assertEquals(0, zero.exitCode)
        assertTrue(zero.stdout.contains("SIFTALPHA_X_SYSTEM_EXIT_ZERO"))

        session.start(EmbeddedPythonProjectFixture.PROJECT_F_SYSTEM_EXIT_NONZERO)
        awaitState(session, EmbeddedPythonState.FAILED, 5_000L)
        val nonzero = session.snapshot()
        assertEquals(7, nonzero.exitCode)
        assertTrue(nonzero.stdout.contains("SIFTALPHA_X_SYSTEM_EXIT_NONZERO"))
    }

    private fun readySession(context: android.content.Context): EmbeddedPythonSession {
        val provenance = EmbeddedPythonFiles.provenance(context)
        assertTrue(provenance.contains("CPython version: `3.14.7`"))
        assertTrue(provenance.contains("Target ABI: `arm64-v8a`"))
        assertTrue(provenance.contains("6d50cc3aa66e414a439594089bcdfb5f1264358155c70c1f00471c24cfb477fb"))
        val session = EmbeddedPythonSession.shared(context)
        assumeTrue(
            "A native smoke test session is already active",
            EmbeddedPythonStatePolicy.canStart(session.snapshot().state),
        )
        return session
    }

    private fun assumeArm64() {
        assumeTrue(
            "The first PoC only packages arm64-v8a",
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
        )
    }

    private fun awaitRuntimePhase(
        session: EmbeddedPythonSession,
        expected: EmbeddedPythonRuntimePhase,
        timeoutMs: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var snapshot = session.snapshot()
        while (snapshot.runtimePhase != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
            snapshot = session.snapshot()
        }
        assertEquals(expected, snapshot.runtimePhase)
    }

    private fun awaitStopDiagnostics(
        session: EmbeddedPythonSession,
        timeoutMs: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var snapshot = session.snapshot()
        while (
            (snapshot.stopPhase != EmbeddedPythonStopPhase.STOP_REQUEST_RETURNED ||
                snapshot.stopResult != EmbeddedPythonStopResult.INTERRUPT_DELIVERED) &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(50L)
            snapshot = session.snapshot()
        }
        assertEquals(EmbeddedPythonStopPhase.STOP_REQUEST_RETURNED, snapshot.stopPhase)
        assertEquals(EmbeddedPythonStopResult.INTERRUPT_DELIVERED, snapshot.stopResult)
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
