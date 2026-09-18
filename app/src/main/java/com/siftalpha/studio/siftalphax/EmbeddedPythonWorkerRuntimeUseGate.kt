package com.siftalpha.studio.siftalphax

/** Small process-local fence for the native singleton session shared by smoke and project runs. */
class EmbeddedPythonWorkerRuntimeUseGate {
    enum class Owner {
        CPYTHON_SMOKE,
        PROJECT_EXECUTION,
    }

    sealed class AcquireResult {
        class Acquired internal constructor(
            val lease: Lease,
        ) : AcquireResult()

        object SameOwnerBusy : AcquireResult()

        object OtherOwnerBusy : AcquireResult()
    }

    /** An opaque ownership token; only the exact successful acquisition may release it. */
    class Lease internal constructor(
        val owner: Owner,
        internal val token: Long,
    )

    private var activeLease: Lease? = null
    private var nextToken = 0L

    @Synchronized
    fun tryAcquire(requestedOwner: Owner): AcquireResult = when (val current = activeLease) {
        null -> {
            val lease = Lease(requestedOwner, ++nextToken)
            activeLease = lease
            AcquireResult.Acquired(lease)
        }

        else -> if (current.owner == requestedOwner) {
            AcquireResult.SameOwnerBusy
        } else {
            AcquireResult.OtherOwnerBusy
        }
    }

    @Synchronized
    fun release(lease: Lease) {
        if (activeLease === lease && activeLease?.token == lease.token) {
            activeLease = null
        }
    }

    @Synchronized
    fun currentOwner(): Owner? = activeLease?.owner
}
