package com.siftalpha.studio.siftalphax

/** Small process-local fence for the native singleton session shared by smoke and project runs. */
class EmbeddedPythonWorkerRuntimeUseGate {
    enum class Owner {
        CPYTHON_SMOKE,
        PROJECT_EXECUTION,
    }

    enum class AcquireResult {
        ACQUIRED,
        SAME_OWNER_BUSY,
        OTHER_OWNER_BUSY,
    }

    private var owner: Owner? = null

    @Synchronized
    fun tryAcquire(requestedOwner: Owner): AcquireResult = when (owner) {
        null -> {
            owner = requestedOwner
            AcquireResult.ACQUIRED
        }

        requestedOwner -> AcquireResult.SAME_OWNER_BUSY
        else -> AcquireResult.OTHER_OWNER_BUSY
    }

    @Synchronized
    fun release(releasingOwner: Owner) {
        if (owner == releasingOwner) {
            owner = null
        }
    }

    @Synchronized
    fun currentOwner(): Owner? = owner
}
