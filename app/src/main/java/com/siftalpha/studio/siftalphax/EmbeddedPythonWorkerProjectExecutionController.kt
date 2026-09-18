package com.siftalpha.studio.siftalphax

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Process-scoped finite file-backed execution owner for the dedicated R Worker.
 *
 * The caller owns and stages executionRoot. This controller never deletes that directory and
 * never reads SAF or Management-layer project state.
 */
class EmbeddedPythonWorkerProjectExecutionController(
    private val processState: EmbeddedPythonWorkerProcessState,
    private val runtimeUseGate: EmbeddedPythonWorkerRuntimeUseGate,
) {
    private val executionState = EmbeddedPythonWorkerProjectExecutionState()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SiftAlphaWorkerProjectExecution").apply { isDaemon = true }
    }
    private var activeExecutionLease: EmbeddedPythonWorkerRuntimeUseGate.Lease? = null

    @Synchronized
    fun start(
        context: Context,
        expectedWorkerInstanceId: String?,
        expectedProcessBindingId: String?,
        executionRoot: String?,
        entrypoint: String?,
        workingDirectory: String?,
    ): Int {
        val request = try {
            EmbeddedPythonWorkerProjectExecutionRequest(
                executionRoot = requireNotNull(executionRoot),
                entrypoint = requireNotNull(entrypoint),
                workingDirectory = requireNotNull(workingDirectory),
            )
        } catch (_: IllegalArgumentException) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST
        }

        if (executionState.snapshot().state in ACTIVE_STATES) {
            return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_BUSY
        }

        // A terminal result can become visible just before the previous task reaches finally.
        // Reclaim only that execution's lease; its later finally release is then harmless.
        activeExecutionLease?.let { previousLease ->
            activeExecutionLease = null
            runtimeUseGate.release(previousLease)
        }

        val executionLease = when (
            val acquire = runtimeUseGate.tryAcquire(
                EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION,
            )
        ) {
            is EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired -> acquire.lease

            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.SameOwnerBusy ->
                return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_BUSY

            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OtherOwnerBusy ->
                return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_RUNTIME_MODE_CONFLICT
        }

        val result = executionState.start(
            expectedWorkerInstanceId = expectedWorkerInstanceId,
            expectedProcessBindingId = expectedProcessBindingId,
            actualWorkerInstanceId = processState.workerInstanceId,
            boundRuntimeLoadBinding = processState.boundRuntimeLoadBinding(),
            workerLifecycleState = processState.workerLifecycleState(),
            request = request,
        )
        if (result != EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_ACCEPTED) {
            runtimeUseGate.release(executionLease)
            return result
        }

        activeExecutionLease = executionLease
        try {
            executor.execute { runExecution(context.applicationContext, executionLease) }
        } catch (error: RuntimeException) {
            executionState.completeInternalError(
                "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=executor:${error.message.orEmpty()}",
            )
            releaseExecutionLease(executionLease)
        }
        return result
    }

    fun snapshot(): EmbeddedPythonWorkerProjectExecutionSnapshot = executionState.snapshot()

    private fun runExecution(
        context: Context,
        executionLease: EmbeddedPythonWorkerRuntimeUseGate.Lease,
    ) {
        try {
            val inputs = executionState.inputs()
                ?: error("project execution inputs disappeared")

            // prepare() is intentionally first. Its repair path only replaces python/ and
            // must preserve the caller-owned projects/ subtree.
            val home = EmbeddedPythonFiles.prepare(context)
            val spec = EmbeddedPythonExecutionSpec(
                projectIdentity = inputs.projectIdentity,
                executionRoot = File(inputs.executionRoot),
                entrypoint = inputs.entrypoint,
                workingDirectory = inputs.workingDirectory,
                runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
                sessionId = inputs.sessionId,
                generation = inputs.generation,
            )
            val invalid = spec.nativeValidationErrors()
            if (invalid.isNotEmpty()) {
                executionState.completeInternalError(
                    "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=spec:${invalid.joinToString(",")}",
                )
                return
            }

            executionState.markStarting()
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
                executionState.completeInternalError(
                    "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=nativeStart_rejected",
                )
                return
            }

            executionState.markRunning()
            pollNativeResult()
        } catch (error: Exception) {
            executionState.completeInternalError(
                "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=prepare_or_adapter:" +
                    "${error::class.java.simpleName}:${error.message.orEmpty()}",
            )
        } finally {
            releaseExecutionLease(executionLease)
        }
    }

    @Synchronized
    private fun releaseExecutionLease(lease: EmbeddedPythonWorkerRuntimeUseGate.Lease) {
        if (activeExecutionLease === lease) {
            activeExecutionLease = null
        }
        runtimeUseGate.release(lease)
    }

    private fun pollNativeResult() {
        val deadline = System.currentTimeMillis() + EXECUTION_TIMEOUT_MS
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
                executionState.completeInternalError(
                    "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=snapshot_parse:" +
                        "${error::class.java.simpleName}:${error.message.orEmpty()}",
                )
                return
            }
            val mappedState = executionState.applyNativeSnapshot(snapshot)
            if (mappedState in TERMINAL_STATES) return
            try {
                Thread.sleep(POLL_MS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                executionState.completeInternalError(
                    "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=interrupted",
                    snapshot,
                )
                return
            }
        }

        executionState.completeInternalError(
            "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=timeout",
        )
    }

    private fun failNativeBoundary(operation: String, error: Throwable) {
        Log.e(
            TAG,
            "$operation failed in worker pid=${Process.myPid()} " +
                "type=${error::class.java.simpleName}",
        )
        executionState.completeInternalError(
            "SIFTALPHA_WORKER_PROJECT_EXECUTION_FAILURE=$operation:" +
                error::class.java.simpleName,
        )
    }

    private companion object {
        private const val TAG = "SiftAlphaRWorker"
        private const val POLL_MS = 50L
        private const val EXECUTION_TIMEOUT_MS = 30_000L

        private val ACTIVE_STATES = setOf(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_PREPARING,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STARTING,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_RUNNING,
        )
        private val TERMINAL_STATES = setOf(
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_SUCCEEDED,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_FAILED,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOPPED,
            EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_INTERNAL_ERROR,
        )
    }
}
