package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessState

data class MacHybridLauncherResult(
    val success: Boolean,
    val cancelled: Boolean,
    val exitCode: Int? = null,
    val detail: String? = null,
)

internal class MacHybridLauncherRunner(
    private val processControl: MacProjectProcessControl,
) {
    fun run(
        request: ProjectProcessLaunchRequest,
        cancelled: () -> Boolean,
    ): MacHybridLauncherResult {
        return try {
            processControl.start(request)
            while (true) {
                if (cancelled()) {
                    processControl.stopProject(request.scope)
                    return MacHybridLauncherResult(
                        success = false,
                        cancelled = true,
                        detail = "launcher cancelled",
                    )
                }

                val status = processControl.status(request.scope)
                when (status.state) {
                    ProjectProcessState.RUNNING,
                    ProjectProcessState.STARTING,
                    -> Thread.sleep(80)

                    ProjectProcessState.EXITED_SUCCESS -> {
                        return MacHybridLauncherResult(
                            success = true,
                            cancelled = false,
                            exitCode = status.exitCode,
                        )
                    }

                    ProjectProcessState.EXITED_ERROR -> {
                        Thread.sleep(40)
                        val detail = terminalDetail(request)
                        return MacHybridLauncherResult(
                            success = false,
                            cancelled = false,
                            exitCode = status.exitCode,
                            detail = detail ?: "launcher exited with code " + status.exitCode,
                        )
                    }

                    ProjectProcessState.STOPPED -> {
                        return MacHybridLauncherResult(
                            success = false,
                            cancelled = true,
                            exitCode = status.exitCode,
                            detail = "launcher stopped",
                        )
                    }

                    ProjectProcessState.UNKNOWN -> {
                        return MacHybridLauncherResult(
                            success = false,
                            cancelled = false,
                            exitCode = status.exitCode,
                            detail = "launcher state became unknown",
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            MacHybridLauncherResult(
                success = false,
                cancelled = false,
                detail = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun terminalDetail(request: ProjectProcessLaunchRequest): String? {
        val logs = processControl.logs(request.scope, 128 * 1024)
        return sequenceOf(logs.stderr, logs.stdout)
            .flatMap { text -> text.lineSequence() }
            .map { line -> line.trim() }
            .filter(String::isNotBlank)
            .lastOrNull()
            ?.take(500)
    }
}
