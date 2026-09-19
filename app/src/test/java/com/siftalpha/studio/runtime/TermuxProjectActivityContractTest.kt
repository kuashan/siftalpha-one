package com.siftalpha.studio.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TermuxProjectActivityContractTest {
    @Test
    fun externalProjectActivityKeepsPayloadInForeground() {
        val script = TermuxProjectActivityContract.wrap(
            runtimeId = "project-a",
            operation = "status",
            quotedShellScript = "'true'",
            hostPreamble = "set -e",
            hostProcessHelpers = "siftalpha_pid_alive() { return 1; }",
        )

        val payloadLaunch = script.indexOf("bash -lc 'true'")
        val pidAssignment = script.indexOf("activity_pid=\$\$")
        val pidWrite = script.indexOf(
            "printf '%s\\n' \"\$activity_pid\" >\"\$activity_pid_file\"",
            pidAssignment,
        )

        assertTrue("payload must be present", payloadLaunch >= 0)
        assertTrue("owner PID must be this foreground shell", pidAssignment >= 0)
        assertTrue("ownership must be published before payload", pidWrite >= 0 && pidWrite < payloadLaunch)
        assertFalse("payload must not be backgrounded", script.contains("bash -lc 'true' &"))
        assertFalse("wrapper must not rely on unverified setsid semantics", script.contains("setsid bash -lc"))
        assertFalse("wrapper must not use a background-child PID", script.contains("activity_pid=\$!"))
        assertFalse("wrapper must not wait on a background child", script.contains("wait \"\$activity_pid\""))
        assertFalse("wrapper must not contain an elapsed-time watchdog", script.contains("SECONDS"))
        assertFalse("wrapper must not synthesize a timeout result", script.contains("SIFTALPHA_OPERATION_RESULT=TIMED_OUT"))
        assertTrue("normal exit must clean ownership", script.contains("trap activity_cleanup EXIT"))
        assertTrue("INT must clean ownership", script.contains("trap 'activity_cleanup; exit 130' INT"))
        assertTrue("TERM must clean ownership", script.contains("trap 'activity_cleanup; exit 143' TERM"))
        assertTrue("HUP must clean ownership", script.contains("trap 'activity_cleanup; exit 129' HUP"))
        assertTrue(script.contains("project-a.activity.status.pid"))
        assertTrue(script.contains("project-a.activity.status.pgid"))
        assertTrue("PGID ownership must be cleared when no dedicated PGID exists", script.contains("activity_pgid_file"))
    }

    @Test
    fun externalProjectActivityPropagatesOutputExitCodeAndCleansOwnership() {
        val root = Files.createTempDirectory("siftalpha-activity-contract").toFile()
        try {
            val script = TermuxProjectActivityContract.wrap(
                runtimeId = "project-a",
                operation = "clean",
                quotedShellScript = "'printf FOREGROUND_STDOUT; printf FOREGROUND_STDERR >&2; exit 37'",
                hostPreamble = "set -e; runtime_dir='${root.absolutePath}'",
                hostProcessHelpers = "siftalpha_pid_alive() { return 1; }; siftalpha_group_alive() { return 1; }",
            )

            val process = ProcessBuilder("bash", "-lc", script).start()
            val stdout = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val stderr = process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
            assertEquals(37, process.waitFor())
            assertTrue(stdout.contains("FOREGROUND_STDOUT"))
            assertTrue(stderr.contains("FOREGROUND_STDERR"))
            assertFalse(Files.exists(root.toPath().resolve("project-a.activity.clean.pid")))
            assertFalse(Files.exists(root.toPath().resolve("project-a.activity.clean.pgid")))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun staleOwnershipAllowsNewActivityButLiveOwnershipRejectsIt() {
        val root = Files.createTempDirectory("siftalpha-activity-stale").toFile()
        try {
            val stalePidFile = root.toPath().resolve("project-a.activity.clean.pid")
            Files.writeString(stalePidFile, "99999999\n")
            val staleScript = TermuxProjectActivityContract.wrap(
                runtimeId = "project-a",
                operation = "clean",
                quotedShellScript = "'exit 0'",
                hostPreamble = "set -e; runtime_dir='${root.absolutePath}'",
                hostProcessHelpers = "siftalpha_pid_alive() { return 1; }; siftalpha_group_alive() { return 1; }",
            )
            val staleProcess = ProcessBuilder("bash", "-lc", staleScript).start()
            assertEquals(0, staleProcess.waitFor())

            Files.writeString(stalePidFile, ProcessHandle.current().pid().toString())
            val liveScript = TermuxProjectActivityContract.wrap(
                runtimeId = "project-a",
                operation = "clean",
                quotedShellScript = "'printf SHOULD_NOT_RUN; exit 0'",
                hostPreamble = "set -e; runtime_dir='${root.absolutePath}'",
                hostProcessHelpers = "siftalpha_pid_alive() { [ \"\$1\" = ${ProcessHandle.current().pid()} ]; }; siftalpha_group_alive() { return 1; }",
            )
            val liveProcess = ProcessBuilder("bash", "-lc", liveScript).start()
            val liveStdout = liveProcess.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            assertEquals(80, liveProcess.waitFor())
            assertTrue(liveStdout.contains("SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE"))
            assertFalse(liveStdout.contains("SHOULD_NOT_RUN"))
        } finally {
            root.deleteRecursively()
        }
    }
}
