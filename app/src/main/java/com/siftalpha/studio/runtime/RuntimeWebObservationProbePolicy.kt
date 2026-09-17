package com.siftalpha.studio.runtime

/**
 * Bounded Web LOGS discovery policy.
 *
 * A candidate URL is only evidence. Discovery stops only when an endpoint has been verified
 * reachable, or when the bounded LOGS budget is exhausted.
 */
object RuntimeWebObservationProbePolicy {

    data class Input(
        val webLogDiscoveryAllowed: Boolean,
        val probeCount: Int,
        val maxProbeCount: Int,
        val candidateExists: Boolean,
        val endpointVerified: Boolean,
    )

    fun shouldProbe(input: Input): Boolean {
        require(input.probeCount >= 0) { "probeCount must not be negative" }
        require(input.maxProbeCount > 0) { "maxProbeCount must be positive" }

        if (!input.webLogDiscoveryAllowed) return false
        if (input.probeCount >= input.maxProbeCount) return false
        if (input.endpointVerified) return false

        // Keep candidateExists in the input to make the distinction explicit: both the no-candidate
        // case and an unreachable candidate permit another bounded LOGS probe.
        return !input.candidateExists || !input.endpointVerified
    }
}
