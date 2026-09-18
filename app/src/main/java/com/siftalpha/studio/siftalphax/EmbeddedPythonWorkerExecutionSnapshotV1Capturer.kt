package com.siftalpha.studio.siftalphax

/**
 * Captures a process/execution status value without holding two state monitors at once.
 *
 * Process state is monotonic for the lifetime of a Worker process: it can bind once and can
 * enter TERMINATING once, but it cannot reset. Therefore equal before/after process observations
 * stabilize the execution capture against a cross-domain epoch change.
 */
object EmbeddedPythonWorkerExecutionSnapshotV1Capturer {
    fun capture(
        processSnapshot: () -> EmbeddedPythonWorkerProcessSnapshot,
        executionSnapshot: () -> EmbeddedPythonWorkerProjectExecutionSnapshot,
        workerPid: Int,
    ): EmbeddedPythonWorkerExecutionSnapshotV1 {
        while (true) {
            val processBefore = processSnapshot()
            val execution = executionSnapshot()
            val processAfter = processSnapshot()
            if (processBefore == processAfter) {
                return EmbeddedPythonWorkerExecutionSnapshotV1Mapper.map(
                    process = processAfter,
                    execution = execution,
                    workerPid = workerPid,
                )
            }
        }
    }
}
