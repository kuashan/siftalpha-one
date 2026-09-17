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
 * R-only typed IPC endpoint for the future Embedded Python worker.
 *
 * Slice 3 extends the dedicated-process and RuntimeLoadBindingV1 handshake with a fenced
 * termination primitive; no runtime execution is performed here.
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
    }
}
