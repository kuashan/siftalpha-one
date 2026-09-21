package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalProviderPreflightPolicyTest {

    @Test
    fun `missing Termux wins over every later readiness stage`() {
        val snapshot = ExternalProviderPreflightPolicy.local(
            termuxInstalled = false,
            runCommandPermissionGranted = false,
        )

        assertEquals(
            ExternalProviderReadinessStatus.TERMUX_NOT_INSTALLED,
            snapshot.status,
        )
        assertFalse(snapshot.ready)
    }

    @Test
    fun `installed Termux without RUN_COMMAND permission requires permission`() {
        val snapshot = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = false,
        )

        assertEquals(
            ExternalProviderReadinessStatus.RUN_COMMAND_PERMISSION_REQUIRED,
            snapshot.status,
        )
    }

    @Test
    fun `installed and permitted provider requires a fresh bridge probe`() {
        val snapshot = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )

        assertEquals(
            ExternalProviderReadinessStatus.BRIDGE_PROBE_REQUIRED,
            snapshot.status,
        )
    }

    @Test
    fun `successful bridge probe becomes READY`() {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )
        val result = RuntimeResult(
            executionId = 1,
            stdout = "SIFTALPHA_TERMUX_ALLOW_EXTERNAL_APPS=YES\nSIFTALPHA_TERMUX_BRIDGE_OK\n",
            stderr = "",
            exitCode = 0,
            internalErrorCode = 0,
            internalErrorMessage = "",
        )

        val snapshot = ExternalProviderPreflightPolicy.fromProbe(
            local = local,
            result = result,
            checkedAtEpochMs = 10_000L,
        )

        assertEquals(ExternalProviderReadinessStatus.READY, snapshot.status)
        assertTrue(snapshot.ready)
        assertEquals(true, snapshot.allowExternalApps)
        assertEquals(10_000L, snapshot.checkedAtEpochMs)
    }

    @Test
    fun `bridge response with external apps disabled is configuration required`() {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )
        val result = RuntimeResult(
            executionId = 2,
            stdout = "SIFTALPHA_TERMUX_ALLOW_EXTERNAL_APPS=NO\nSIFTALPHA_TERMUX_BRIDGE_OK\n",
            stderr = "",
            exitCode = 0,
            internalErrorCode = 0,
            internalErrorMessage = "",
        )

        val snapshot = ExternalProviderPreflightPolicy.fromProbe(
            local = local,
            result = result,
            checkedAtEpochMs = 20_000L,
        )

        assertEquals(
            ExternalProviderReadinessStatus.TERMUX_CONFIGURATION_REQUIRED,
            snapshot.status,
        )
        assertFalse(snapshot.ready)
    }

    @Test
    fun `bridge timeout becomes unavailable shared readiness evidence`() {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )

        val snapshot = ExternalProviderPreflightPolicy.fromTimeout(
            local = local,
            checkedAtEpochMs = 22_000L,
        )

        assertEquals(
            ExternalProviderReadinessStatus.BRIDGE_UNAVAILABLE,
            snapshot.status,
        )
        assertFalse(snapshot.ready)
        assertEquals(22_000L, snapshot.checkedAtEpochMs)
    }

    @Test
    fun `bridge response without allow external apps evidence is not ready`() {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )
        val result = RuntimeResult(
            executionId = 30,
            stdout = "SIFTALPHA_TERMUX_BRIDGE_OK\n",
            stderr = "",
            exitCode = 0,
            internalErrorCode = 0,
            internalErrorMessage = "",
        )

        val snapshot = ExternalProviderPreflightPolicy.fromProbe(
            local = local,
            result = result,
            checkedAtEpochMs = 25_000L,
        )

        assertEquals(
            ExternalProviderReadinessStatus.BRIDGE_UNAVAILABLE,
            snapshot.status,
        )
        assertFalse(snapshot.ready)
    }

    @Test
    fun `probe transport failure is bridge unavailable`() {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )
        val result = RuntimeResult(
            executionId = 3,
            stdout = "",
            stderr = "service unavailable",
            exitCode = 1,
            internalErrorCode = -1,
            internalErrorMessage = "service unavailable",
        )

        val snapshot = ExternalProviderPreflightPolicy.fromProbe(
            local = local,
            result = result,
            checkedAtEpochMs = 30_000L,
        )

        assertEquals(
            ExternalProviderReadinessStatus.BRIDGE_UNAVAILABLE,
            snapshot.status,
        )
    }

    @Test
    fun `READY evidence expires after freshness window`() {
        val snapshot = ExternalProviderReadiness(
            status = ExternalProviderReadinessStatus.READY,
            termuxInstalled = true,
            runCommandPermissionGranted = true,
            bridgeVerified = true,
            allowExternalApps = true,
            checkedAtEpochMs = 1_000L,
        )

        assertTrue(
            ExternalProviderPreflightPolicy.isFresh(
                snapshot = snapshot,
                nowEpochMs = 31_000L,
                freshnessMs = 30_000L,
            ),
        )
        assertFalse(
            ExternalProviderPreflightPolicy.isFresh(
                snapshot = snapshot,
                nowEpochMs = 31_001L,
                freshnessMs = 30_000L,
            ),
        )
    }
}
