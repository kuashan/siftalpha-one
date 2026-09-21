package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalProviderPreflightTest {
    @Test
    fun termuxNotInstalledIsReportedBeforeAnyPermissionOrBridgeFact() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(termuxInstalled = false),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.TERMUX_NOT_INSTALLED, result.readiness)
        assertNull(result.allowExternalApps)
    }

    @Test
    fun permissionMissingIsNotConvertedIntoRuntimeFailure() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(runCommandPermissionGranted = false),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED, result.readiness)
        assertEquals(false, result.ready)
    }

    @Test
    fun grantedPermissionWithoutProbeRequiresTheSharedBridgeCheck() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED, result.readiness)
        assertNull(result.bridgeResponsive)
    }

    @Test
    fun checkingStateIsSharedAsChecking() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(bridgeState = ExternalProviderBridgeState.CHECKING),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECKING, result.readiness)
    }

    @Test
    fun successfulFreshProbeProducesReadyAndProvesExternalAppsAccess() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.PASS,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.PASS,
            ),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.READY, result.readiness)
        assertTrue(result.ready)
        assertEquals(true, result.allowExternalApps)
        assertEquals(true, result.bridgeResponsive)
    }

    @Test
    fun failedProbeRequiresExternalAppsSetupAndDoesNotGuessFromAFile() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.FAIL,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.FAIL,
            ),
            nowEpochMs = 1_000L,
        )

        assertEquals(
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            result.readiness,
        )
        assertEquals(false, result.allowExternalApps)
        assertEquals(false, result.bridgeResponsive)
    }

    @Test
    fun staleReadyFactMustBeProbedAgain() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.PASS,
                lastProbeAtEpochMs = 1L,
                lastProbeResult = ExternalProviderProbeResult.PASS,
            ),
            nowEpochMs = 100_000L,
        )

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED, result.readiness)
    }

    private fun facts(
        termuxInstalled: Boolean = true,
        runCommandPermissionGranted: Boolean = true,
        bridgeState: ExternalProviderBridgeState = ExternalProviderBridgeState.UNKNOWN,
        lastProbeAtEpochMs: Long? = null,
        lastProbeResult: ExternalProviderProbeResult? = null,
    ) = ExternalProviderFacts(
        termuxInstalled = termuxInstalled,
        runCommandPermissionGranted = runCommandPermissionGranted,
        bridgeState = bridgeState,
        lastProbeAtEpochMs = lastProbeAtEpochMs,
        lastProbeResult = lastProbeResult,
    )
}
