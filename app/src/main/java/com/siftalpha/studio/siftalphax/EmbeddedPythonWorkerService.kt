package com.siftalpha.studio.siftalphax

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log

/**
 * R-only typed IPC endpoint for the future Embedded Python worker.
 *
 * Slice 2 deliberately does not load CPython, JNI, or the existing execution bridge. The
 * service proves only the dedicated-process and RuntimeLoadBindingV1 handshake boundary.
 */
class EmbeddedPythonWorkerService : Service() {
    private val binder = object : IEmbeddedPythonWorker.Stub() {
        override fun getWorkerInstanceId(): String = processState.workerInstanceId

        override fun getWorkerPid(): Int = Process.myPid()

        override fun getBindingState(): Int = processState.bindingState()

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
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(
            TAG,
            "created pid=${Process.myPid()} workerInstanceId=${processState.workerInstanceId}",
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    companion object {
        private const val TAG = "SiftAlphaRWorker"

        // Companion object state is process-local. Service recreation in this process cannot
        // reset the binding; a fresh OS worker process creates a fresh state object instead.
        private val processState = EmbeddedPythonWorkerProcessState()
    }
}
