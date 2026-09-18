package com.siftalpha.studio.siftalphax

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Process-scoped owner for the one-shot Worker-side CPython smoke.
 *
 * The controller deliberately does not use EmbeddedPythonSession. Its only purpose is to prove
 * that the existing native CPython capability can be loaded and exercised inside the dedicated
 * Worker process. A fresh Worker process is the only retry boundary.
 */
class EmbeddedPythonWorkerCpythonSmokeController(
    private val processState: EmbeddedPythonWorkerProcessState,
    private val runtimeUseGate: EmbeddedPythonWorkerRuntimeUseGate,
) {
    private val smokeState = EmbeddedPythonWorkerCpythonSmokeState()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SiftAlphaWorkerCpythonSmoke").apply { isDaemon = true }
    }

    @Synchronized
    fun start(
        context: Context,
        expectedWorkerInstanceId: String?,
        expectedProcessBindingId: String?,
    ): Int {
        if (
            smokeState.snapshot().state !=
                EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_NOT_STARTED
        ) {
            return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ALREADY_STARTED
        }
        val smokeLease = when (
            val acquire = runtimeUseGate.tryAcquire(
                EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE,
            )
        ) {
            is EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired -> acquire.lease

            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.SameOwnerBusy,
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OtherOwnerBusy,
            -> return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_RUNTIME_BUSY
        }
        val result = smokeState.start(
            expectedWorkerInstanceId = expectedWorkerInstanceId,
            expectedProcessBindingId = expectedProcessBindingId,
            actualWorkerInstanceId = processState.workerInstanceId,
            actualProcessBindingId = processState.boundProcessBindingId(),
            bindingState = processState.bindingState(),
            workerLifecycleState = processState.workerLifecycleState(),
        )
        if (result != EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_ACCEPTED) {
            runtimeUseGate.release(smokeLease)
            return result
        }
        try {
            executor.execute { runSmoke(context.applicationContext, smokeLease) }
        } catch (error: RuntimeException) {
            smokeState.completeFailure(
                exitCode = null,
                stdout = "",
                stderr = failureText("executor", error),
            )
            runtimeUseGate.release(smokeLease)
        }
        return result
    }

    fun snapshot(): EmbeddedPythonWorkerCpythonSmokeSnapshot = smokeState.snapshot()

    private fun runSmoke(
        context: Context,
        smokeLease: EmbeddedPythonWorkerRuntimeUseGate.Lease,
    ) {
        var stagedRoot: File? = null
        try {
            // Runtime assets must exist before the fixture is staged. prepare() may rebuild the
            // private root when the generated assets are absent or incomplete.
            val home = EmbeddedPythonFiles.prepare(context)
            val sessionId = smokeState.snapshot().sessionId
            check(sessionId.isNotBlank()) { "worker smoke session was not created" }
            stagedRoot = EmbeddedPythonFiles.stageProjectFixture(
                context,
                EmbeddedPythonProjectFixture.PROJECT_G_WORKER_SMOKE,
                sessionId,
            )
            val fixture = EmbeddedPythonProjectFixture.PROJECT_G_WORKER_SMOKE
            val spec = EmbeddedPythonExecutionSpec(
                projectIdentity = fixture.projectIdentity,
                executionRoot = stagedRoot,
                entrypoint = fixture.entrypoint,
                workingDirectory = fixture.workingDirectory,
                runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
                sessionId = sessionId,
                generation = smokeState.snapshot().generation,
            )
            val invalid = spec.nativeValidationErrors()
            check(invalid.isEmpty()) {
                "invalid worker smoke execution specification: ${invalid.joinToString(",")}"
            }

            smokeState.markStarting()
            val accepted = try {
                EmbeddedPythonBridge.nativeStart(
                    home.absolutePath,
                    spec.projectIdentity,
                    spec.executionRoot.absolutePath,
                    spec.entrypointFile.absolutePath,
                    spec.workingDirectoryFile.absolutePath,
                    spec.sessionId,
                    spec.generation,
                )
            } catch (error: Throwable) {
                failNativeBoundary("nativeStart", error)
                return
            }
            if (!accepted) {
                smokeState.completeFailure(
                    exitCode = null,
                    stdout = "",
                    stderr = "SIFTALPHA_WORKER_CPYTHON_SMOKE_FAILURE=nativeStart_rejected",
                )
                return
            }

            smokeState.markRunning()
            pollNativeResult(sessionId, fixture.projectIdentity)
        } catch (error: Exception) {
            smokeState.completeFailure(
                exitCode = null,
                stdout = "",
                stderr = failureText("prepare_or_stage", error),
            )
        } finally {
            runCatching { stagedRoot?.deleteRecursively() }
            runtimeUseGate.release(smokeLease)
        }
    }

    private fun pollNativeResult(sessionId: String, projectIdentity: String) {
        val deadline = System.currentTimeMillis() + SMOKE_TIMEOUT_MS
        var latest: EmbeddedPythonSnapshot? = null
        while (System.currentTimeMillis() < deadline) {
            val raw = try {
                EmbeddedPythonBridge.nativeSnapshot()
            } catch (error: Throwable) {
                failNativeBoundary("nativeSnapshot", error)
                return
            }
            val snapshot = try {
                EmbeddedPythonSnapshotParser.parse(raw)
            } catch (error: Exception) {
                smokeState.completeFailure(
                    exitCode = null,
                    stdout = "",
                    stderr = failureText("snapshot_parse", error),
                )
                return
            }
            latest = snapshot
            if (!EmbeddedPythonStatePolicy.isTerminal(snapshot.state)) {
                try {
                    Thread.sleep(SMOKE_POLL_MS)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    smokeState.completeFailure(
                        exitCode = snapshot.exitCode,
                        stdout = snapshot.stdout,
                        stderr = snapshot.stderr +
                            "\nSIFTALPHA_WORKER_CPYTHON_SMOKE_FAILURE=interrupted",
                    )
                    return
                }
                continue
            }

            if (snapshot.state != EmbeddedPythonState.SUCCEEDED) {
                smokeState.completeFailure(snapshot.exitCode, snapshot.stdout, snapshot.stderr)
                return
            }

            val evidence = EmbeddedPythonWorkerCpythonSmokeEvidence.parse(snapshot.stdout)
            val evidenceError = when {
                snapshot.sessionId != sessionId -> "session_id_mismatch"
                snapshot.projectIdentity != projectIdentity -> "project_identity_mismatch"
                snapshot.exitCode != 0 -> "nonzero_exit_code"
                evidence == null -> "missing_or_invalid_fixture_evidence"
                evidence.pythonPid != Process.myPid() -> "python_pid_mismatch"
                else -> null
            }
            if (evidenceError != null) {
                smokeState.completeFailure(
                    exitCode = snapshot.exitCode,
                    stdout = snapshot.stdout,
                    stderr = snapshot.stderr +
                        "\nSIFTALPHA_WORKER_CPYTHON_SMOKE_FAILURE=$evidenceError",
                )
            } else {
                smokeState.completeSuccess(
                    exitCode = checkNotNull(snapshot.exitCode),
                    stdout = snapshot.stdout,
                    stderr = snapshot.stderr,
                    pythonPid = checkNotNull(evidence).pythonPid,
                )
            }
            return
        }

        smokeState.completeFailure(
            exitCode = latest?.exitCode,
            stdout = latest?.stdout.orEmpty(),
            stderr = latest?.stderr.orEmpty() +
                "\nSIFTALPHA_WORKER_CPYTHON_SMOKE_FAILURE=timeout",
        )
    }

    private fun failNativeBoundary(operation: String, error: Throwable) {
        Log.e(TAG, "$operation failed in worker pid=${Process.myPid()}", error)
        smokeState.completeFailure(
            exitCode = null,
            stdout = "",
            stderr = failureText(operation, error),
        )
    }

    private fun failureText(operation: String, error: Throwable): String =
        "SIFTALPHA_WORKER_CPYTHON_SMOKE_FAILURE=$operation:" +
            "${error::class.java.simpleName}:${error.message.orEmpty()}"

    private companion object {
        private const val TAG = "SiftAlphaRWorker"
        private const val SMOKE_POLL_MS = 50L
        private const val SMOKE_TIMEOUT_MS = 30_000L
    }
}
