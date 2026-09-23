package com.siftalpha.studio.runtime

/**
 * Developer Workspace（开发者工作区） return-to-app retry policy for External Provider readiness.
 *
 * Normal Mode（普通模式） already retries these recoverable provider states from onResume.
 * Developer Mode uses the same readiness facts, but retries only when it still owns a deferred
 * PREPARE / RUN continuation.
 */
object ExternalProviderResumePolicy {
    fun shouldRetry(
        readiness: ExternalProviderReadiness,
        hasDeferredAction: Boolean,
    ): Boolean {
        if (!hasDeferredAction) return false
        return when (readiness) {
            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            -> true

            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED,
            ExternalProviderReadiness.BRIDGE_CHECKING,
            ExternalProviderReadiness.READY,
            ExternalProviderReadiness.UNAVAILABLE,
            -> false
        }
    }
}
