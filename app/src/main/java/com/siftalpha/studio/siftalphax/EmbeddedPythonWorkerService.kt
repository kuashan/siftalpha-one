package com.siftalpha.studio.siftalphax

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File

/**
 * R-only typed IPC endpoint for the Embedded Python worker foundation.
 *
 * The dedicated process exposes the Slice 4 CPython smoke and Slice 5/6 file-backed execution
 * foundation, including cooperative long-running STOP and same-process re-entry. These contracts
 * are intentionally not connected to the Management layer or the normal-user project flow.
 */
class EmbeddedPythonWorkerService : Service() {
    private val binder = object : IEmbeddedPythonWorker.Stub() {
        override fun getWorkerInstanceId(): String = processState.workerInstanceId

        override fun getWorkerPid(): Int = Process.myPid()

        override fun getBindingState(): Int = processState.bindingState()

        override fun getWorkerLifecycleState(): Int = processState.workerLifecycleState()

        override fun getBoundProcessBindingId(): String = processState.boundProcessBindingId()

        override fun bindRuntimeLoadV1(
            projectIdentity: String?,
            projectSourceGeneration: String?,
            runtimeProvenanceDigest: String?,
            dependencyLayerBinding: String?,
            expectedProcessBindingId: String?,
        ): Int {
            if (Binder.getCallingUid() != applicationInfo.uid) {
                return EmbeddedPythonWorkerProtocol.REJECTED_INVALID_REQUEST
            }

            val result = processState.bind(
                EmbeddedPythonWorkerBindRequest(
                    projectIdentity = projectIdentity,
                    projectSourceGeneration = projectSourceGeneration,
                    runtimeProvenanceDigest = runtimeProvenanceDigest,
                    dependencyLayerBinding = dependencyLayerBinding,
                    expectedProcessBindingId = expectedProcessBindingId,
                ),
            )
            Log.i(
                TAG,
                "bind result=$result pid=${Process.myPid()} " +
                    "workerInstanceId=${processState.workerInstanceId} " +
                    "state=${processState.bindingState()} " +
                    "bindingId=${processState.boundProcessBindingId()}",
            )
            return result
        }

        override fun requestWorkerExitForRebind(
            expectedWorkerInstanceId: String?,
            expectedProcessBindingId: String?,
        ): Int {
            if (Binder.getCallingUid() != applicationInfo.uid) {
                return EmbeddedPythonWorkerProtocol.EXIT_REJECTED_INVALID_REQUEST
            }
            if (Binder.getCallingPid() == Process.myPid() || !isDedicatedWorkerProcess()) {
                return EmbeddedPythonWorkerProtocol.EXIT_REJECTED_WRONG_PROCESS
            }

            val result = processState.requestWorkerExitForRebind(
                expectedWorkerInstanceId = expectedWorkerInstanceId,
                expectedProcessBindingId = expectedProcessBindingId,
            )
            Log.i(
                TAG,
                "exit result=$result pid=${Process.myPid()} " +
                    "workerInstanceId=${processState.workerInstanceId} " +
                    "state=${processState.workerLifecycleState()}",
            )
            if (result == EmbeddedPythonWorkerProtocol.EXIT_ACCEPTED) {
                scheduleWorkerProcessExit()
            }
            return result
        }

        override fun startCpythonSmokeV1(
            expectedWorkerInstanceId: String?,
            expectedProcessBindingId: String?,
        ): Int {
            if (Binder.getCallingUid() != applicationInfo.uid) {
                return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_INVALID_REQUEST
            }
            if (Binder.getCallingPid() == Process.myPid() || !isDedicatedWorkerProcess()) {
                return EmbeddedPythonWorkerProtocol.CPYTHON_SMOKE_START_REJECTED_WRONG_PROCESS
            }

            val result = smokeController.start(
                context = applicationContext,
                expectedWorkerInstanceId = expectedWorkerInstanceId,
                expectedProcessBindingId = expectedProcessBindingId,
            )
            Log.i(
                TAG,
                "cp python smoke start result=$result pid=${Process.myPid()} " +
                    "workerInstanceId=${processState.workerInstanceId} " +
                    "bindingState=${processState.bindingState()}",
            )
            return result
        }

        override fun getCpythonSmokeState(): Int = smokeController.snapshot().state

        override fun hasCpythonSmokeExitCode(): Boolean =
            smokeController.snapshot().hasExitCode

        override fun getCpythonSmokeExitCode(): Int = smokeController.snapshot().exitCode

        override fun getCpythonSmokeStdout(): String = smokeController.snapshot().stdout

        override fun getCpythonSmokeStderr(): String = smokeController.snapshot().stderr

        override fun getCpythonSmokePythonPid(): Int = smokeController.snapshot().pythonPid

        override fun getCpythonSmokeSessionId(): String = smokeController.snapshot().sessionId

        override fun startProjectExecutionV1(
            expectedWorkerInstanceId: String?,
            expectedProcessBindingId: String?,
            executionRoot: String?,
            entrypoint: String?,
            workingDirectory: String?,
        ): Int {
            if (Binder.getCallingUid() != applicationInfo.uid) {
                return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST
            }
            if (Binder.getCallingPid() == Process.myPid() || !isDedicatedWorkerProcess()) {
                return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_START_REJECTED_WRONG_PROCESS
            }

            val result = projectController.start(
                context = applicationContext,
                expectedWorkerInstanceId = expectedWorkerInstanceId,
                expectedProcessBindingId = expectedProcessBindingId,
                executionRoot = executionRoot,
                entrypoint = entrypoint,
                workingDirectory = workingDirectory,
            )
            Log.i(
                TAG,
                "project execution start result=$result pid=${Process.myPid()} " +
                    "workerInstanceId=${processState.workerInstanceId} " +
                    "bindingState=${processState.bindingState()}",
            )
            return result
        }

        override fun requestProjectExecutionStopV1(
            expectedWorkerInstanceId: String?,
            expectedProcessBindingId: String?,
            expectedExecutionSessionId: String?,
            expectedExecutionGeneration: Long,
        ): Int {
            if (Binder.getCallingUid() != applicationInfo.uid) {
                return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_INVALID_REQUEST
            }
            if (Binder.getCallingPid() == Process.myPid() || !isDedicatedWorkerProcess()) {
                return EmbeddedPythonWorkerProtocol.PROJECT_EXECUTION_STOP_REJECTED_WRONG_PROCESS
            }

            val result = projectController.requestStop(
                expectedWorkerInstanceId = expectedWorkerInstanceId,
                expectedProcessBindingId = expectedProcessBindingId,
                expectedExecutionSessionId = expectedExecutionSessionId,
                expectedExecutionGeneration = expectedExecutionGeneration,
            )
            Log.i(
                TAG,
                "project execution stop result=$result pid=${Process.myPid()} " +
                    "workerInstanceId=${processState.workerInstanceId}",
            )
            return result
        }

        override fun getProjectExecutionState(): Int = projectController.snapshot().state

        override fun getProjectExecutionSessionId(): String =
            projectController.snapshot().sessionId

        override fun getProjectExecutionGeneration(): Long =
            projectController.snapshot().generation

        override fun getProjectExecutionProjectIdentity(): String =
            projectController.snapshot().projectIdentity

        override fun getProjectExecutionRoot(): String = projectController.snapshot().executionRoot

        override fun getProjectExecutionEntrypoint(): String =
            projectController.snapshot().entrypoint

        override fun getProjectExecutionWorkingDirectory(): String =
            projectController.snapshot().workingDirectory

        override fun hasProjectExecutionExitCode(): Boolean =
            projectController.snapshot().hasExitCode

        override fun getProjectExecutionExitCode(): Int = projectController.snapshot().exitCode

        override fun getProjectExecutionStdout(): String = projectController.snapshot().stdout

        override fun getProjectExecutionStderr(): String = projectController.snapshot().stderr
    }

    private val terminationHandler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(
            TAG,
            "created pid=${Process.myPid()} workerInstanceId=${processState.workerInstanceId}",
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun scheduleWorkerProcessExit() {
        terminationHandler.postDelayed({
            if (
                processState.workerLifecycleState() ==
                    EmbeddedPythonWorkerProtocol.WORKER_LIFECYCLE_TERMINATING &&
                isDedicatedWorkerProcess()
            ) {
                Process.killProcess(Process.myPid())
            } else {
                Log.e(TAG, "refusing worker termination outside the dedicated process")
            }
        }, PROCESS_EXIT_DELAY_MS)
    }

    private fun isDedicatedWorkerProcess(): Boolean {
        val expectedProcessName = "${applicationInfo.packageName}$WORKER_PROCESS_SUFFIX"
        val currentProcessName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            readSelfProcessName()
        }
        return currentProcessName == expectedProcessName
    }

    private fun readSelfProcessName(): String? = try {
        val bytes = File("/proc/self/cmdline").readBytes()
        val end = bytes.indexOf(0.toByte()).let { if (it < 0) bytes.size else it }
        bytes.copyOf(end).toString(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "SiftAlphaRWorker"
        private const val WORKER_PROCESS_SUFFIX = ":siftalpha_r_worker"
        private const val PROCESS_EXIT_DELAY_MS = 150L

        // Companion object state is process-local. Service recreation in this process cannot
        // reset the binding; a fresh OS worker process creates a fresh state object instead.
        private val processState = EmbeddedPythonWorkerProcessState()
        private val runtimeUseGate = EmbeddedPythonWorkerRuntimeUseGate()
        private val smokeController = EmbeddedPythonWorkerCpythonSmokeController(
            processState = processState,
            runtimeUseGate = runtimeUseGate,
        )
        private val projectController = EmbeddedPythonWorkerProjectExecutionController(
            processState = processState,
            runtimeUseGate = runtimeUseGate,
        )
    }
}
