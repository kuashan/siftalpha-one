package com.siftalpha.studio.runtime

/**
 * Chooses the single user-visible project status summary source.
 *
 * Runtime guidance is based on observed facts and takes precedence over the generic action-policy
 * summary. The action-policy summary remains the fallback when no guidance exists.
 */
object ProjectStatusPresentationPolicy {
    enum class SummarySource {
        GUIDANCE,
        ACTION_POLICY,
    }

    fun summarySource(
        guidance: ProjectStatusGuidancePolicy.Message?,
    ): SummarySource = if (guidance == null) {
        SummarySource.ACTION_POLICY
    } else {
        SummarySource.GUIDANCE
    }
}
