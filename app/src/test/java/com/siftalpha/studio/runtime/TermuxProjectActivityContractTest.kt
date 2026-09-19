package com.siftalpha.studio.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxProjectActivityContractTest {
    @Test
    fun parentWrapperPublishesProjectOwnershipBeforeWait() {
        val script = TermuxProjectActivityContract.wrap(
            runtimeId = "project-a",
            operation = "status",
            quotedShellScript = "'true'",
            hostPreamble = "set -e",
            hostProcessHelpers = "siftalpha_pid_alive() { return 1; }",
        )

        val setsidLaunch = script.indexOf("setsid bash -lc 'true' &")
        val pidCapture = script.indexOf("activity_pid=\$!", setsidLaunch)
        val pidWrite = script.indexOf(
            "printf '%s\\n' \"\$activity_pid\" >\"\$activity_pid_file\"",
            pidCapture,
        )
        val pgidWrite = script.indexOf(
            "printf '%s\\n' \"\$activity_pid\" >\"\$activity_pgid_file\"",
            pidCapture,
        )
        val wait = script.indexOf("wait \"\$activity_pid\"", pidCapture)

        assertTrue("parent wrapper must launch the child", setsidLaunch >= 0)
        assertTrue("parent wrapper must capture the child PID", pidCapture > setsidLaunch)
        assertTrue("parent wrapper must publish PID before wait", pidWrite > pidCapture && pidWrite < wait)
        assertTrue("setsid branch must publish PGID before wait", pgidWrite > pidCapture && pgidWrite < wait)
        assertTrue("wait must happen only after ownership files are durable", wait > pgidWrite)
        assertTrue(script.contains("project-a.activity.status.pid"))
        assertTrue(script.contains("project-a.activity.status.pgid"))
        assertTrue("watchdog must not wait from a sibling subshell", !script.contains("activity_waiter"))
        assertTrue("parent must observe the owned process before waiting", script.contains("/proc/\$activity_pid/stat"))
    }

    @Test
    fun watchdogIsProjectScopedAndDoesNotRequireGnuTimeout() {
        val script = TermuxProjectActivityContract.wrap(
            runtimeId = "project-a",
            operation = "clean",
            quotedShellScript = "'sleep 10'",
            hostPreamble = "set -e",
            hostProcessHelpers = "siftalpha_pid_alive() { return 1; }; siftalpha_stop_tree() { return 0; }",
            timeoutMs = 2_000L,
        )

        assertTrue(script.contains("activity_timed_out=0"))
        assertTrue(script.contains("siftalpha_stop_tree \"\$activity_pid\""))
        assertTrue(script.contains("SIFTALPHA_OPERATION_RESULT=TIMED_OUT"))
        assertTrue(script.contains("SIFTALPHA_ERROR=OPERATION_TIMEOUT"))
        assertTrue(!script.contains("command -v timeout"))
        assertTrue(script.contains("project-a.activity.clean.pid"))
    }
}
