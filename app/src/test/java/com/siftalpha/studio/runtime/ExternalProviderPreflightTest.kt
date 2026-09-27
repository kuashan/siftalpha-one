package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertNull(result.bridgeResponsive)
        assertEquals(
            ExternalProviderRecoveryAction.INSTALL_PROVIDER,
            result.recoveryAction,
        )
        assertEquals(TermuxContract.OFFICIAL_INSTALL_URL, result.provider.installUrl)
    }

    @Test
    fun staleReadyProofIsSuppressedWhenTermuxIsNoLongerInstalled() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                termuxInstalled = false,
                runCommandPermissionGranted = false,
                bridgeState = ExternalProviderBridgeState.PASS,
                probeStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.PASS,
            ),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.TERMUX_NOT_INSTALLED, result.readiness)
        assertNull(result.allowExternalApps)
        assertNull(result.bridgeResponsive)
        assertEquals(ExternalProviderRecoveryAction.INSTALL_PROVIDER, result.recoveryAction)
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
                probeStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY,
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
    fun legacyBridgeOnlyPassMustBeProbedForRuntimeCapability() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.PASS,
                probeStage = ExternalProviderProbeStage.BRIDGE,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.PASS,
            ),
            nowEpochMs = 1_000L,
        )

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED, result.readiness)
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
    fun setupGuideRoutesRuntimeCapabilityFailureToProotStep() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.FAIL,
                probeStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.FAIL,
                detail = "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=PROOT_DISTRO",
            ),
            nowEpochMs = 1_000L,
        )

        val setup = ExternalRuntimeSetupGuide.evaluate(result)

        assertEquals(ExternalRuntimeSetupStage.PROOT_DISTRO, setup.stage)
        assertEquals(true, setup.allowExternalAppsReady)
        assertEquals(false, setup.prootDistroReady)
        assertNull(setup.ubuntuReady)
    }

    @Test
    fun setupGuideRoutesUbuntuFailureAfterProot() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.FAIL,
                probeStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.FAIL,
                detail = "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU",
            ),
            nowEpochMs = 1_000L,
        )

        val setup = ExternalRuntimeSetupGuide.evaluate(result)

        assertEquals(ExternalRuntimeSetupStage.UBUNTU, setup.stage)
        assertEquals(true, setup.allowExternalAppsReady)
        assertEquals(true, setup.prootDistroReady)
        assertEquals(false, setup.ubuntuReady)
    }

    @Test
    fun setupGuideReportsReadyOnlyAfterRuntimeCapabilityProof() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.PASS,
                probeStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY,
                lastProbeAtEpochMs = 950L,
                lastProbeResult = ExternalProviderProbeResult.PASS,
            ),
            nowEpochMs = 1_000L,
        )

        val setup = ExternalRuntimeSetupGuide.evaluate(result)

        assertEquals(ExternalRuntimeSetupStage.READY, setup.stage)
        assertTrue(setup.ready)
        assertEquals(true, setup.prootDistroReady)
        assertEquals(true, setup.ubuntuReady)
    }

    @Test
    fun setupCommandsAreCopyableAndCapabilityProbeHasExplicitUbuntuMarker() {
        assertTrue(
            TermuxBackend.FIRST_RUN_SETUP_COMMAND.contains(
                "printf '\\nallow-external-apps = true\\n'",
            ),
        )
        assertFalse(
            TermuxBackend.FIRST_RUN_SETUP_COMMAND.contains(
                "printf '\\\\nallow-external-apps = true\\\\n'",
            ),
        )
        assertTrue(TermuxBackend.FIRST_RUN_SETUP_COMMAND.contains("allow-external-apps = true"))
        assertEquals("pkg install -y proot-distro", TermuxBackend.PROOT_DISTRO_INSTALL_COMMAND)
        assertEquals("proot-distro install ubuntu", TermuxBackend.UBUNTU_INSTALL_COMMAND)
        assertTrue(
            TermuxBackend.RUNTIME_CAPABILITY_TEST.shellScript.contains(
                "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU",
            ),
        )
    }

    @Test
    fun staleRuntimeProofRequiresHealthRecheckButKeepsSetupComplete() {
        val result = ExternalProviderPreflight.evaluate(
            facts = facts(
                bridgeState = ExternalProviderBridgeState.PASS,
                probeStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY,
                lastProbeAtEpochMs = 1L,
                lastProbeResult = ExternalProviderProbeResult.PASS,
            ),
            nowEpochMs = 100_000L,
        )

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED, result.readiness)
        assertFalse(result.ready)
        assertTrue(result.setupComplete)

        val setup = ExternalRuntimeSetupGuide.evaluate(result)
        assertEquals(ExternalRuntimeSetupStage.READY, setup.stage)
        assertTrue(setup.ready)
    }

    private fun facts(
        termuxInstalled: Boolean = true,
        runCommandPermissionGranted: Boolean = true,
        bridgeState: ExternalProviderBridgeState = ExternalProviderBridgeState.UNKNOWN,
        probeStage: ExternalProviderProbeStage? = null,
        lastProbeAtEpochMs: Long? = null,
        lastProbeResult: ExternalProviderProbeResult? = null,
        detail: String? = null,
    ) = ExternalProviderFacts(
        termuxInstalled = termuxInstalled,
        runCommandPermissionGranted = runCommandPermissionGranted,
        bridgeState = bridgeState,
        probeStage = probeStage,
        lastProbeAtEpochMs = lastProbeAtEpochMs,
        lastProbeResult = lastProbeResult,
        detail = detail,
    )
}
