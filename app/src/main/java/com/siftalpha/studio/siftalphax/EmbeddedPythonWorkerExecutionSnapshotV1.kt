package com.siftalpha.studio.siftalphax

import android.os.Parcel
import android.os.Parcelable

/**
 * Immutable, typed cross-process status value for the current Worker execution slot.
 *
 * This is deliberately a value object rather than a live view of Worker state. A Binder caller
 * can retain it while the Worker moves to a later execution without the earlier observation being
 * mutated underneath it.
 */
data class EmbeddedPythonWorkerExecutionSnapshotV1(
    val workerInstanceId: String,
    val workerPid: Int,
    val workerLifecycleState: Int,
    val bindingState: Int,
    val processBindingId: String,
    val executionState: Int,
    val executionSessionId: String,
    val executionGeneration: Long,
    val projectIdentity: String,
    val executionRoot: String,
    val entrypoint: String,
    val workingDirectory: String,
    val stopRequested: Boolean,
    val hasExitCode: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(workerInstanceId)
        parcel.writeInt(workerPid)
        parcel.writeInt(workerLifecycleState)
        parcel.writeInt(bindingState)
        parcel.writeString(processBindingId)
        parcel.writeInt(executionState)
        parcel.writeString(executionSessionId)
        parcel.writeLong(executionGeneration)
        parcel.writeString(projectIdentity)
        parcel.writeString(executionRoot)
        parcel.writeString(entrypoint)
        parcel.writeString(workingDirectory)
        parcel.writeInt(if (stopRequested) 1 else 0)
        parcel.writeInt(if (hasExitCode) 1 else 0)
        parcel.writeInt(exitCode)
        parcel.writeString(stdout)
        parcel.writeString(stderr)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EmbeddedPythonWorkerExecutionSnapshotV1> =
            object : Parcelable.Creator<EmbeddedPythonWorkerExecutionSnapshotV1> {
                override fun createFromParcel(parcel: Parcel): EmbeddedPythonWorkerExecutionSnapshotV1 =
                    EmbeddedPythonWorkerExecutionSnapshotV1(
                        workerInstanceId = parcel.readString().orEmpty(),
                        workerPid = parcel.readInt(),
                        workerLifecycleState = parcel.readInt(),
                        bindingState = parcel.readInt(),
                        processBindingId = parcel.readString().orEmpty(),
                        executionState = parcel.readInt(),
                        executionSessionId = parcel.readString().orEmpty(),
                        executionGeneration = parcel.readLong(),
                        projectIdentity = parcel.readString().orEmpty(),
                        executionRoot = parcel.readString().orEmpty(),
                        entrypoint = parcel.readString().orEmpty(),
                        workingDirectory = parcel.readString().orEmpty(),
                        stopRequested = parcel.readInt() != 0,
                        hasExitCode = parcel.readInt() != 0,
                        exitCode = parcel.readInt(),
                        stdout = parcel.readString().orEmpty(),
                        stderr = parcel.readString().orEmpty(),
                    )

                override fun newArray(size: Int): Array<EmbeddedPythonWorkerExecutionSnapshotV1?> =
                    arrayOfNulls(size)
            }
    }
}

/** Maps two independently captured immutable state records without issuing scalar Binder reads. */
object EmbeddedPythonWorkerExecutionSnapshotV1Mapper {
    fun map(
        process: EmbeddedPythonWorkerProcessSnapshot,
        execution: EmbeddedPythonWorkerProjectExecutionSnapshot,
        workerPid: Int,
    ): EmbeddedPythonWorkerExecutionSnapshotV1 = EmbeddedPythonWorkerExecutionSnapshotV1(
        workerInstanceId = process.workerInstanceId,
        workerPid = workerPid,
        workerLifecycleState = process.workerLifecycleState,
        bindingState = process.bindingState,
        processBindingId = process.processBindingId,
        executionState = execution.state,
        executionSessionId = execution.sessionId,
        executionGeneration = execution.generation,
        projectIdentity = execution.projectIdentity,
        executionRoot = execution.executionRoot,
        entrypoint = execution.entrypoint,
        workingDirectory = execution.workingDirectory,
        stopRequested = execution.stopRequested,
        hasExitCode = execution.hasExitCode,
        exitCode = execution.exitCode,
        stdout = execution.stdout,
        stderr = execution.stderr,
    )
}
