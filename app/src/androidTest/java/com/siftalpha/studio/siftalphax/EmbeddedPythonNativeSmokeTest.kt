package com.siftalpha.studio.siftalphax

import android.os.Build
import android.os.SystemClock
import java.io.File
import java.nio.file.Files
import java.util.UUID
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
    fun missingEntrypointPublishesExplicitFailedResult() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = readySession(context)

        session.start(EmbeddedPythonProjectFixture.PROJECT_MISSING_ENTRYPOINT)
        awaitState(session, EmbeddedPythonState.FAILED, 5_000L)
        val snapshot = session.snapshot()

        assertEquals(EmbeddedPythonState.FAILED, snapshot.state)
        assertEquals(1, snapshot.exitCode)
        assertEquals(EmbeddedPythonRuntimePhase.TERMINAL, snapshot.runtimePhase)
        assertTrue(snapshot.stderr.contains("SIFTALPHA_X_ENTRYPOINT_ERROR=missing entrypoint"))
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


    @Test
    fun rejectsExecutionRootOutsideAppPrivateStaging() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = EmbeddedPythonFiles.prepare(context)
        val outsideRoot = File(home.parentFile, "path-validation-outside-root-${UUID.randomUUID()}")
        check(outsideRoot.mkdirs())
        File(outsideRoot, "main.py").writeText("print('must not run')")
        try {
            assertNativePathFailure(
                context = context,
                executionRoot = outsideRoot,
                entrypoint = File(outsideRoot, "main.py"),
                workingDirectory = outsideRoot,
                expectedError = "execution root is outside staging path",
            )
        } finally {
            outsideRoot.deleteRecursively()
        }
    }

    @Test
    fun rejectsEntrypointTraversalOutsideExecutionRoot() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = EmbeddedPythonFiles.prepare(context)
        val fixtureSessionId = "path-validation-${UUID.randomUUID()}"
        val root = EmbeddedPythonFiles.stageProjectFixture(
            context,
            EmbeddedPythonProjectFixture.PROJECT_A,
            fixtureSessionId,
        )
        val outside = File(home.parentFile, "path-validation-outside-${UUID.randomUUID()}.py")
        outside.writeText("print('must not run')")
        try {
            assertNativePathFailure(
                context = context,
                executionRoot = root,
                entrypoint = File(root, "../${outside.name}"),
                workingDirectory = root,
                expectedError = "path traversal is not allowed",
            )
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun rejectsWorkingDirectoryTraversalOutsideExecutionRoot() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = EmbeddedPythonFiles.prepare(context)
        val fixtureSessionId = "path-validation-${UUID.randomUUID()}"
        val root = EmbeddedPythonFiles.stageProjectFixture(
            context,
            EmbeddedPythonProjectFixture.PROJECT_A,
            fixtureSessionId,
        )
        val outside = File(home.parentFile, "path-validation-outside-dir-${UUID.randomUUID()}")
        check(outside.mkdirs())
        try {
            assertNativePathFailure(
                context = context,
                executionRoot = root,
                entrypoint = File(root, "main.py"),
                workingDirectory = File(root, "../${outside.name}"),
                expectedError = "path traversal is not allowed",
            )
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun rejectsProjectControlledEntrypointSymlinkEscape() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = EmbeddedPythonFiles.prepare(context)
        val fixtureSessionId = "path-validation-${UUID.randomUUID()}"
        val root = EmbeddedPythonFiles.stageProjectFixture(
            context,
            EmbeddedPythonProjectFixture.PROJECT_A,
            fixtureSessionId,
        )
        val outside = File(home.parentFile, "path-validation-outside-${UUID.randomUUID()}.py")
        outside.writeText("print('must not run')")
        val entrypoint = File(root, "main.py")
        check(entrypoint.delete())
        Files.createSymbolicLink(entrypoint.toPath(), outside.toPath())
        try {
            assertNativePathFailure(
                context = context,
                executionRoot = root,
                entrypoint = entrypoint,
                workingDirectory = root,
                expectedError = "symlink path components are not supported",
            )
        } finally {
            Files.deleteIfExists(entrypoint.toPath())
            root.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun rejectsProjectControlledWorkingDirectorySymlinkEscape() {
        assumeArm64()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = EmbeddedPythonFiles.prepare(context)
        val fixtureSessionId = "path-validation-${UUID.randomUUID()}"
        val root = EmbeddedPythonFiles.stageProjectFixture(
            context,
            EmbeddedPythonProjectFixture.PROJECT_A,
            fixtureSessionId,
        )
        val outside = File(home.parentFile, "path-validation-outside-dir-${UUID.randomUUID()}")
        check(outside.mkdirs())
        val workingDirectory = File(root, "work")
        Files.createSymbolicLink(workingDirectory.toPath(), outside.toPath())
        try {
            assertNativePathFailure(
                context = context,
                executionRoot = root,
                entrypoint = File(root, "main.py"),
                workingDirectory = workingDirectory,
                expectedError = "symlink path components are not supported",
            )
        } finally {
            Files.deleteIfExists(workingDirectory.toPath())
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    private fun assertNativePathFailure(
        context: android.content.Context,
        executionRoot: File,
        entrypoint: File,
        workingDirectory: File,
        expectedError: String,
    ) {
        val session = readySession(context)
        session.start(EmbeddedPythonProjectFixture.PROJECT_A)
        awaitState(session, EmbeddedPythonState.SUCCEEDED, 5_000L)
        val previous = session.snapshot()
        val home = EmbeddedPythonFiles.prepare(context)
        val sessionId = "siftalpha-x-path-validation-${UUID.randomUUID()}"
        assertTrue(
            EmbeddedPythonBridge.nativeStart(
                home.absolutePath,
                "fixture-path-validation",
                executionRoot.absolutePath,
                entrypoint.absolutePath,
                workingDirectory.absolutePath,
                sessionId,
                previous.generation + 1L,
            ),
        )
        awaitState(session, EmbeddedPythonState.FAILED, 5_000L)
        val snapshot = session.snapshot()
        assertEquals(1, snapshot.exitCode)
        assertTrue(
            "Expected native path validation error $expectedError, got: ${snapshot.stderr}",
            snapshot.stderr.contains(expectedError),
        )
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
