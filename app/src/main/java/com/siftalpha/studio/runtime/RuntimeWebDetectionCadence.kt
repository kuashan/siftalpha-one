package com.siftalpha.studio.runtime

/**
 * Pure timing policy for Web discovery and endpoint verification.
 *
 * Startup gets bounded fast paths; steady-state discovery and health checks remain intentionally
 * slow so Web observation never becomes a second execution loop.
 */
object RuntimeWebDetectionCadence {
    const val NORMAL_ENDPOINT_RECHECK_MS = 2_000L
    const val FAST_EXTERNAL_OBSERVATION_MS = 500L
    const val NORMAL_EXTERNAL_OBSERVATION_MS = 2_000L
    const val FAST_EXTERNAL_WEB_LOG_STATUS_INTERVAL = 1
    const val VERIFIED_FAILURES_TO_UNAVAILABLE = 2

    private const val VERIFIED_FAILURE_RECHECK_MS = 500L
    private val initialFailureRechecksMs = longArrayOf(150L, 300L, 600L)
    private val internalDiscoveryMissRechecksMs = longArrayOf(150L, 300L, 600L, 1_000L)

    fun endpointRecheckDelay(
        everReachable: Boolean,
        consecutiveFailures: Int,
    ): Long {
        require(consecutiveFailures >= 0)
        if (consecutiveFailures <= 0) return NORMAL_ENDPOINT_RECHECK_MS
        if (verifiedFailurePending(everReachable, consecutiveFailures)) {
            return VERIFIED_FAILURE_RECHECK_MS
        }
        if (everReachable) return NORMAL_ENDPOINT_RECHECK_MS
        return initialFailureRechecksMs.getOrNull(consecutiveFailures - 1)
            ?: NORMAL_ENDPOINT_RECHECK_MS
    }

    fun initialVerificationPending(
        everReachable: Boolean,
        consecutiveFailures: Int,
    ): Boolean {
        require(consecutiveFailures >= 0)
        return !everReachable && consecutiveFailures in 1..initialFailureRechecksMs.size
    }

    fun verifiedFailurePending(
        everReachable: Boolean,
        consecutiveFailures: Int,
    ): Boolean {
        require(consecutiveFailures >= 0)
        return everReachable && consecutiveFailures in 1 until VERIFIED_FAILURES_TO_UNAVAILABLE
    }

    fun internalDiscoveryRetryDelay(missCount: Int): Long {
        require(missCount > 0)
        return internalDiscoveryMissRechecksMs.getOrNull(missCount - 1)
            ?: NORMAL_ENDPOINT_RECHECK_MS
    }

    fun externalObservationDelay(webDiscoveryStillUseful: Boolean): Long =
        if (webDiscoveryStillUseful) FAST_EXTERNAL_OBSERVATION_MS
        else NORMAL_EXTERNAL_OBSERVATION_MS
}
