package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalProviderResumePolicyTest {
    @Test
    fun developerReturnRetriesRecoverableProviderStatesOnlyWhenActionIsDeferred() {
        listOf(
            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
        ).forEach { readiness ->
            assertTrue(
                ExternalProviderResumePolicy.shouldRetry(
                    readiness = readiness,
                    hasDeferredAction = true,
                ),
            )
            assertFalse(
                ExternalProviderResumePolicy.shouldRetry(
                    readiness = readiness,
                    hasDeferredAction = false,
                ),
            )
        }
    }

    @Test
    fun developerReturnDoesNotRedispatchWhileCheckingReadyOrTerminal() {
        listOf(
            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED,
            ExternalProviderReadiness.BRIDGE_CHECKING,
            ExternalProviderReadiness.READY,
            ExternalProviderReadiness.UNAVAILABLE,
        ).forEach { readiness ->
            assertFalse(
                ExternalProviderResumePolicy.shouldRetry(
                    readiness = readiness,
                    hasDeferredAction = true,
                ),
            )
        }
    }
}
