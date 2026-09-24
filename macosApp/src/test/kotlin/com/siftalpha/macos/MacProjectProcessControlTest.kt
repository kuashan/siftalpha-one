package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.core.process.ProjectProcessState
import com.siftalpha.core.process.ProjectStopOutcome
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacProjectProcessControlTest {
    @Test
    fun capturesStdoutStderrAndExitStatus() {
        val control = MacProjectProcessControl()
        val scope = ProjectProcessScope("process-output")
        control.start(
            ProjectProcessLaunchRequest(
                scope = scope,
                executable = "/bin/sh",
                arguments = listOf("-c", "echo OUT_OK; echo ERR_OK >&2; exit 0"),
            ),
        )

        waitUntil(2_000) {
            control.status(scope).state == ProjectProcessState.EXITED_SUCCESS
        }
        val logs = control.logs(scope, 16_384)

        assertTrue(logs.stdout.contains("OUT_OK"))
        assertTrue(logs.stderr.contains("ERR_OK"))
        assertEquals(ProjectProcessState.EXITED_SUCCESS, control.status(scope).state)
    }

    @Test
    fun stopIsProjectScopedAcrossConcurrentProcesses() {
        val control = MacProjectProcessControl()
        val a = ProjectProcessScope("project-a")
        val b = ProjectProcessScope("project-b")
        try {
            control.start(
                ProjectProcessLaunchRequest(
                    scope = a,
                    executable = "/bin/sh",
                    arguments = listOf("-c", "echo A_READY; while :; do sleep 1; done"),
                ),
            )
            control.start(
                ProjectProcessLaunchRequest(
                    scope = b,
                    executable = "/bin/sh",
                    arguments = listOf("-c", "echo B_READY; while :; do sleep 1; done"),
                ),
            )

            waitUntil(2_000) {
                control.status(a).state == ProjectProcessState.RUNNING &&
                    control.status(b).state == ProjectProcessState.RUNNING
            }

            assertEquals(ProjectStopOutcome.STOPPED, control.stopProject(a).outcome)
            assertEquals(ProjectProcessState.STOPPED, control.status(a).state)
            assertEquals(ProjectProcessState.RUNNING, control.status(b).state)
        } finally {
            control.stopProject(a)
            control.stopProject(b)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateRunningProcessForSameProjectIsRejected() {
        val control = MacProjectProcessControl()
        val scope = ProjectProcessScope("same-project")
        try {
            control.start(
                ProjectProcessLaunchRequest(
                    scope = scope,
                    executable = "/bin/sh",
                    arguments = listOf("-c", "while :; do sleep 1; done"),
                ),
            )
            control.start(
                ProjectProcessLaunchRequest(
                    scope = scope,
                    executable = "/bin/sh",
                    arguments = listOf("-c", "echo should-not-start"),
                ),
            )
        } finally {
            control.stopProject(scope)
        }
    }

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("condition timed out", condition())
    }
}
