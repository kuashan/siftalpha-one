package com.siftalpha.studio.runtime

/**
 * Pure timing policy for Web discovery and endpoint verification.
 *
 * Startup gets a short bounded fast path; steady-state health checks remain intentionally slow.
 */
object RuntimeWebDetectionCadence {
    const val NORMAL_ENDPOINT_RECHECK_MS = 2_000L
    const val FAST_EXTERNAL_OBSERVATION_MS = 500L
    const val NORMAL_EXTERNAL_OBSERVATION_MS = 2_000L
    const val FAST_EXTERNAL_WEB_LOG_STATUS_INTERVAL = 1

    private val initialFailureRechecksMs = longArrayOf(150L, 300L, 600L)

    fun endpointRecheckDelay(
        everReachable: Boolean,
        consecutiveFailures: Int,
    ): Long {
        require(consecutiveFailures >= 0)
        if (everReachable || consecutiveFailures <= 0) return NORMAL_ENDPOINT_RECHECK_MS
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

    fun externalObservationDelay(webDiscoveryStillUseful: Boolean): Long =
        if (webDiscoveryStillUseful) FAST_EXTERNAL_OBSERVATION_MS
        else NORMAL_EXTERNAL_OBSERVATION_MS
}
