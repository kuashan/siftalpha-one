package com.siftalpha.studio.siftalphax

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException

/**
 * Explicit R-only owner of one logical connection to the dedicated Embedded Python Worker.
 *
 * This class deliberately does not start projects, bind RuntimeLoadBinding, poll in the
 * background, or reconnect after loss. It only provides connection lifetime and authoritative
 * Worker snapshot foundations for a later caller.
 */
class EmbeddedPythonWorkerSupervisor(context: Context) {
    private val applicationContext = context.applicationContext ?: context
    private val state = EmbeddedPythonWorkerSupervisorState()
    private val monitor = Any()

    private var activeEpoch: Long? = null
    private var activeConnection: EpochServiceConnection? = null
    private var pendingBinder: IBinder? = null
    private var remoteBinder: IBinder? = null
    private var remoteWorker: IEmbeddedPythonWorker? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var deathLinked = false

    fun snapshot(): EmbeddedPythonWorkerSupervisorSnapshot = state.snapshot()

    /** Starts one explicit bind attempt. Android/Binder work is performed outside [monitor]. */
    fun connect(): Int {
        val connection: EpochServiceConnection
        val attempt: EmbeddedPythonWorkerSupervisorConnectAttempt
        synchronized(monitor) {
            attempt = state.beginConnect()
            if (attempt.result != EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED) {
                return attempt.result
            }
            connection = EpochServiceConnection(attempt.epoch)
            activeEpoch = attempt.epoch
            activeConnection = connection
            pendingBinder = null
            remoteBinder = null
            remoteWorker = null
            deathRecipient = null
            deathLinked = false
        }

        val bound = applicationContext.bindService(
            Intent(applicationContext, EmbeddedPythonWorkerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) {
            val cleanup = failBinding(attempt.epoch, connection)
            performCleanup(cleanup)
            return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_BIND_FAILED
        }

        // disconnect() may have invalidated this attempt while bindService was in progress.
        val stillOwned = synchronized(monitor) {
            activeEpoch == attempt.epoch &&
                activeConnection === connection &&
                state.snapshot().connectionEpoch == attempt.epoch
        }
        if (!stillOwned) {
            runCatching { applicationContext.unbindService(connection) }
        }
        return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECT_ACCEPTED
    }

    /**
     * Explicitly relinquishes this Supervisor's binding. It never terminates the Worker process.
     */
    fun disconnect() {
        val cleanup: Cleanup?
        synchronized(monitor) {
            state.disconnect()
            cleanup = detachLocked()
        }
        performCleanup(cleanup)
    }

    /**
     * Performs one explicit authoritative Worker snapshot read. No background polling is used.
     */
    fun refreshSnapshot(): Int {
        val handle = synchronized(monitor) {
            val current = state.snapshot()
            val connection = activeConnection
            val binder = remoteBinder
            val worker = remoteWorker
            if (
                current.connectionState !=
                    EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_CONNECTED ||
                connection == null || binder == null || worker == null
            ) {
                null
            } else {
                RefreshHandle(
                    epoch = current.connectionEpoch,
                    connection = connection,
                    binder = binder,
                    worker = worker,
                )
            }
        } ?: return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_NOT_CONNECTED

        val workerSnapshot = try {
            checkNotNull(handle.worker.getWorkerExecutionSnapshotV1()) {
                "Worker returned a null execution snapshot"
            }
        } catch (_: RemoteException) {
            val cleanup = loseConnection(handle.epoch, handle.connection, handle.binder)
            performCleanup(cleanup)
            return if (cleanup != null) {
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_CONNECTION_LOST
            } else {
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_NOT_CONNECTED
            }
        } catch (_: IllegalStateException) {
            val cleanup = loseConnection(handle.epoch, handle.connection, handle.binder)
            performCleanup(cleanup)
            return if (cleanup != null) {
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_PROTOCOL_MISMATCH
            } else {
                EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_NOT_CONNECTED
            }
        }

        val result: Int
        val cleanup: Cleanup?
        synchronized(monitor) {
            if (
                activeEpoch != handle.epoch ||
                activeConnection !== handle.connection ||
                remoteBinder !== handle.binder
            ) {
                return EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_CONNECTION_LOST
            }
            result = state.applyRefresh(handle.epoch, workerSnapshot)
            cleanup = if (
                result == EmbeddedPythonWorkerSupervisorProtocol.SUPERVISOR_REFRESH_PROTOCOL_MISMATCH
            ) {
                detachLocked()
            } else {
                null
            }
        }
        performCleanup(cleanup)
        return result
    }

    private fun handleServiceConnected(
        epoch: Long,
        connection: EpochServiceConnection,
        binder: IBinder,
    ) {
        val acceptedCallback = synchronized(monitor) {
            if (
                activeEpoch != epoch ||
                activeConnection !== connection ||
                !state.isCurrentAttempt(epoch)
            ) {
                false
            } else {
                pendingBinder = binder
                true
            }
        }
        if (!acceptedCallback) return

        val worker = IEmbeddedPythonWorker.Stub.asInterface(binder)
        val recipient = IBinder.DeathRecipient {
            handleBinderDeath(epoch, connection, binder)
        }
        var linked = false
        try {
            binder.linkToDeath(recipient, 0)
            linked = true
            ensureCurrentAttempt(epoch, connection, binder)
            val workerSnapshot = checkNotNull(worker.getWorkerExecutionSnapshotV1()) {
                "Worker returned a null execution snapshot"
            }
            ensureCurrentAttempt(epoch, connection, binder)
            val committed = synchronized(monitor) {
                if (
                    activeEpoch == epoch &&
                    activeConnection === connection &&
                    pendingBinder === binder &&
                    state.isCurrentAttempt(epoch)
                ) {
                    val didCommit = state.completeConnected(epoch, workerSnapshot)
                    if (didCommit) {
                        pendingBinder = null
                        remoteBinder = binder
                        remoteWorker = worker
                        deathRecipient = recipient
                        deathLinked = true
                    }
                    didCommit
                } else {
                    false
                }
            }
            if (committed) return
        } catch (_: RemoteException) {
            // Handshake failure is handled below as a failed connection attempt.
        } catch (_: IllegalStateException) {
            // The Binder can disappear between linkToDeath and the typed snapshot read.
        }

        if (linked) {
            runCatching { binder.unlinkToDeath(recipient, 0) }
        }
        val cleanup = failBinding(epoch, connection)
        performCleanup(cleanup)
    }

    private fun ensureCurrentAttempt(epoch: Long, connection: EpochServiceConnection, binder: IBinder) {
        val current = synchronized(monitor) {
            activeEpoch == epoch &&
                activeConnection === connection &&
                pendingBinder === binder &&
                state.isCurrentAttempt(epoch)
        }
        check(current) { "stale Worker connection attempt" }
    }

    private fun handleBinderDeath(
        epoch: Long,
        connection: EpochServiceConnection,
        binder: IBinder,
    ) {
        val cleanup = loseConnection(epoch, connection, binder)
        performCleanup(cleanup)
    }

    private fun handleServiceDisconnected(epoch: Long, connection: EpochServiceConnection) {
        val cleanup = loseConnection(epoch, connection, null)
        performCleanup(cleanup)
    }

    private fun handleBindingDied(epoch: Long, connection: EpochServiceConnection) {
        val cleanup = loseConnection(epoch, connection, null)
        performCleanup(cleanup)
    }

    private fun handleNullBinding(epoch: Long, connection: EpochServiceConnection) {
        val cleanup: Cleanup?
        synchronized(monitor) {
            if (activeEpoch != epoch || activeConnection !== connection) {
                return
            }
            state.failBind(epoch)
            cleanup = detachLocked()
        }
        performCleanup(cleanup)
    }

    private fun failBinding(epoch: Long, connection: EpochServiceConnection): Cleanup? {
        synchronized(monitor) {
            if (activeEpoch != epoch || activeConnection !== connection) {
                return null
            }
            state.failBind(epoch)
            return detachLocked()
        }
    }

    private fun loseConnection(
        epoch: Long,
        connection: EpochServiceConnection,
        binder: IBinder?,
    ): Cleanup? {
        synchronized(monitor) {
            if (activeEpoch != epoch || activeConnection !== connection) {
                return null
            }
            if (binder != null && pendingBinder !== binder && remoteBinder !== binder) {
                return null
            }
            state.markConnectionLost(epoch)
            return detachLocked()
        }
    }

    private fun detachLocked(): Cleanup? {
        val connection = activeConnection ?: return null
        val cleanup = Cleanup(
            connection = connection,
            binder = remoteBinder,
            deathRecipient = deathRecipient,
            deathLinked = deathLinked,
        )
        activeEpoch = null
        activeConnection = null
        pendingBinder = null
        remoteBinder = null
        remoteWorker = null
        deathRecipient = null
        deathLinked = false
        return cleanup
    }

    private fun performCleanup(cleanup: Cleanup?) {
        if (cleanup == null) return
        if (cleanup.deathLinked && cleanup.binder != null && cleanup.deathRecipient != null) {
            runCatching { cleanup.binder.unlinkToDeath(cleanup.deathRecipient, 0) }
        }
        runCatching { applicationContext.unbindService(cleanup.connection) }
    }

    private inner class EpochServiceConnection(
        private val epoch: Long,
    ) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            handleServiceConnected(epoch, this, service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handleServiceDisconnected(epoch, this)
        }

        override fun onBindingDied(name: ComponentName) {
            handleBindingDied(epoch, this)
        }

        override fun onNullBinding(name: ComponentName) {
            handleNullBinding(epoch, this)
        }
    }

    private data class RefreshHandle(
        val epoch: Long,
        val connection: EpochServiceConnection,
        val binder: IBinder,
        val worker: IEmbeddedPythonWorker,
    )

    private data class Cleanup(
        val connection: EpochServiceConnection,
        val binder: IBinder?,
        val deathRecipient: IBinder.DeathRecipient?,
        val deathLinked: Boolean,
    )
}
