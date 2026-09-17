package com.siftalpha.studio.runtime

/**
 * M-owned scope policy for Runtime Web candidate discovery.
 *
 * PID/socket discovery is strong process evidence and remains usable even when static project
 * inspection does not classify a project as Web. Runtime log text is weak evidence and is allowed
 * only when M has confirmed Web capability.
 */
enum class RuntimeWebCandidateSource {
    PID_SOCKET,
    RUNTIME_LOG,
    EXPLICIT,
    UNKNOWN,
}

data class RuntimeWebCandidate(
    val url: String,
    val source: RuntimeWebCandidateSource,
)

object RuntimeWebDiscoveryScopePolicy {

    fun allowRuntimeLogDiscovery(webCapabilityEnabled: Boolean): Boolean = webCapabilityEnabled

    fun candidateFromOutput(
        output: String,
        webCapabilityEnabled: Boolean,
    ): RuntimeWebCandidate? {
        val socketEvidence = "SIFTALPHA_WEB_AUTODISCOVERY=PASS" in output
        if (!webCapabilityEnabled && !socketEvidence) return null
        val url = RuntimeWebUrl.extractLocalHttpUrl(output) ?: return null
        return RuntimeWebCandidate(
            url = url,
            source = if (socketEvidence) {
                RuntimeWebCandidateSource.PID_SOCKET
            } else {
                RuntimeWebCandidateSource.RUNTIME_LOG
            },
        )
    }

    fun shouldClearPersistedCandidate(
        webCapabilityEnabled: Boolean,
        source: RuntimeWebCandidateSource,
    ): Boolean = !webCapabilityEnabled && source != RuntimeWebCandidateSource.PID_SOCKET

    fun canUseCandidate(
        webCapabilityEnabled: Boolean,
        source: RuntimeWebCandidateSource,
    ): Boolean = webCapabilityEnabled || source == RuntimeWebCandidateSource.PID_SOCKET
}
