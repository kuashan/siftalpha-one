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
    }
}
