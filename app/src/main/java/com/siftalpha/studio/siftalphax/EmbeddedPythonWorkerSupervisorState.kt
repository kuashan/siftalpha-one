package com.siftalpha.studio.siftalphax

/** Stable values for the R-only Worker Supervisor connection contract. */
object EmbeddedPythonWorkerSupervisorProtocol {
    const val SUPERVISOR_DISCONNECTED = 0
    const val SUPERVISOR_BINDING = 1
    const val SUPERVISOR_CONNECTED = 2
    const val SUPERVISOR_CONNECTION_LOST = 3
    const val SUPERVISOR_BIND_FAILED = 4

    const val SUPERVISOR_CONNECT_ACCEPTED = 0
    const val SUPERVISOR_CONNECT_ALREADY_BINDING = 1
    const val SUPERVISOR_CONNECT_ALREADY_CONNECTED = 2
    const val SUPERVISOR_CONNECT_BIND_FAILED = 3

    const val SUPERVISOR_REFRESH_OK = 0
    const val SUPERVISOR_REFRESH_NOT_CONNECTED = 1
    const val SUPERVISOR_REFRESH_CONNECTION_LOST = 2
    const val SUPERVISOR_REFRESH_PROTOCOL_MISMATCH = 3
}

/** Immutable status returned by the Supervisor itself, not a synthetic Worker status. */
data class EmbeddedPythonWorkerSupervisorSnapshot(
    val connectionState: Int,
    val connectionEpoch: Long,
    val workerSnapshot: EmbeddedPythonWorkerExecutionSnapshotV1?,
    val lastWorkerInstanceId: String,
)

data class EmbeddedPythonWorkerSupervisorConnectAttempt(
    val result: Int,
    val epoch: Long,
)

/**
 * Android-independent connection state owner for the Worker Supervisor.
 *
 * The epoch is advanced whenever an attempt is invalidated. A completion, death notification, or
 * refresh result from an older epoch therefore cannot resurrect or overwrite a newer connection.
 * No Android or Binder operation belongs in this class.
 */
class EmbeddedPythonWorkerSupervisorState {
    private var connectionState = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_DISCONNECTED
    private var connectionEpoch = 0L
    private var workerSnapshot: EmbeddedPythonWorkerExecutionSnapshotV1? = null
    private var lastWorkerInstanceId = ""

    @Synchronized
    fun snapshot(): EmbeddedPythonWorkerSupervisorSnapshot =
        EmbeddedPythonWorkerSupervisorSnapshot(
            connectionState = connectionState,
            connectionEpoch = connectionEpoch,
            workerSnapshot = workerSnapshot,
            lastWorkerInstanceId = lastWorkerInstanceId,
        )

    @Synchronized
    fun beginConnect(): EmbeddedPythonWorkerSupervisorConnectAttempt {
        return when (connectionState) {
            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BINDING ->
                EmbeddedPythonWorkerSupervisorConnectAttempt(
                    result = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ALREADY_BINDING,
                    epoch = connectionEpoch,
                )

            EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED ->
                EmbeddedPythonWorkerSupervisorConnectAttempt(
                    result = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ALREADY_CONNECTED,
                    epoch = connectionEpoch,
                )

            else -> {
                connectionEpoch = nextEpoch(connectionEpoch)
                connectionState = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BINDING
                workerSnapshot = null
                EmbeddedPythonWorkerSupervisorConnectAttempt(
                    result = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED,
                    epoch = connectionEpoch,
                )
            }
        }
    }

    @Synchronized
    fun isCurrentAttempt(epoch: Long): Boolean =
        epoch == connectionEpoch &&
            connectionState == EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BINDING

    @Synchronized
    fun isCurrentConnection(epoch: Long): Boolean =
        epoch == connectionEpoch &&
            connectionState == EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED

    @Synchronized
    fun completeConnected(
        epoch: Long,
        snapshot: EmbeddedPythonWorkerExecutionSnapshotV1,
    ): Boolean {
        if (!isCurrentAttemptLocked(epoch) || snapshot.workerInstanceId.isBlank()) {
            return false
        }
        connectionState = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED
        workerSnapshot = snapshot
        lastWorkerInstanceId = snapshot.workerInstanceId
        return true
    }

    @Synchronized
    fun failBind(epoch: Long): Boolean {
        if (!isCurrentAttemptLocked(epoch)) {
            return false
        }
        invalidateLocked(EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BIND_FAILED)
        return true
    }

    @Synchronized
    fun markConnectionLost(epoch: Long): Boolean {
        if (
            epoch != connectionEpoch ||
            connectionState != EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BINDING &&
                connectionState != EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED
        ) {
            return false
        }
        invalidateLocked(EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST)
        return true
    }

    @Synchronized
    fun disconnect(): Long {
        connectionEpoch = nextEpoch(connectionEpoch)
        connectionState = EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_DISCONNECTED
        workerSnapshot = null
        return connectionEpoch
    }

    /**
     * Commits a refresh only for the currently connected epoch and Worker process. A different
     * WorkerInstanceId is a protocol failure, not a new Worker to accept on the same Binder.
     */
    @Synchronized
    fun applyRefresh(
        epoch: Long,
        snapshot: EmbeddedPythonWorkerExecutionSnapshotV1,
    ): Int {
        if (!isCurrentConnectionLocked(epoch)) {
            return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_NOT_CONNECTED
        }
        val current = workerSnapshot
        if (current == null || snapshot.workerInstanceId.isBlank()) {
            invalidateLocked(EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST)
            return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_PROTOCOL_MISMATCH
        }
        if (snapshot.workerInstanceId != current.workerInstanceId) {
            invalidateLocked(EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTION_LOST)
            return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_PROTOCOL_MISMATCH
        }
        workerSnapshot = snapshot
        lastWorkerInstanceId = snapshot.workerInstanceId
        return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_OK
    }

    private fun isCurrentAttemptLocked(epoch: Long): Boolean =
        epoch == connectionEpoch &&
            connectionState == EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_BINDING

    private fun isCurrentConnectionLocked(epoch: Long): Boolean =
        epoch == connectionEpoch &&
            connectionState == EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED

    private fun invalidateLocked(nextState: Int) {
        connectionEpoch = nextEpoch(connectionEpoch)
        connectionState = nextState
        workerSnapshot = null
    }

    private companion object {
        private fun nextEpoch(current: Long): Long =
            if (current == Long.MAX_VALUE) 1L else current + 1L
    }
}
