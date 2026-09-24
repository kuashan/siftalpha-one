package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.core.process.ProjectProcessState
import com.siftalpha.core.process.ProjectStopOutcome
import java.util.concurrent.TimeUnit

data class MacM3SelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

/** Small real-host acceptance probe used by the temporary M3 diagnostic UI and cloud verification. */
object MacM3SelfTest {
    fun run(): MacM3SelfTestResult {
        val control = MacProjectProcessControl()
        val a = ProjectProcessScope("m3-selftest-a")
        val b = ProjectProcessScope("m3-selftest-b")
        val lines = mutableListOf<String>()

        return runCatching {
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
                    control.status(b).state == ProjectProcessState.RUNNING &&
                    control.logs(a, 4096).stdout.contains("A_READY") &&
                    control.logs(b, 4096).stdout.contains("B_READY")
            }

            val stopA = control.stopProject(a)
            val bAfterStopA = control.status(b)
            val stopB = control.stopProject(b)

            val passed =
                stopA.outcome == ProjectStopOutcome.STOPPED &&
                    bAfterStopA.state == ProjectProcessState.RUNNING &&
                    stopB.outcome == ProjectStopOutcome.STOPPED

            lines += "project_a_stop=" + stopA.outcome
            lines += "project_b_after_a_stop=" + bAfterStopA.state
            lines += "project_b_stop=" + stopB.outcome
            MacM3SelfTestResult(passed, lines)
        }.getOrElse { error ->
            control.stopProject(a)
            control.stopProject(b)
            MacM3SelfTestResult(
                passed = false,
                lines = lines + ("error=" + (error.message ?: error.javaClass.simpleName)),
            )
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
        check(condition()) { "M3 host process self-test timed out" }
    }
}
