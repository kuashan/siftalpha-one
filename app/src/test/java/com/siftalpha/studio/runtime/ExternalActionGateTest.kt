package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActionGateTest {
    @Test
    fun requestIsSavedBeforeEnsureAndImmediateReadyProceedsOnce() {
        var readiness = result(ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED)
        lateinit var gate: ExternalActionGate
        var observedPending: ExternalActionGate.Request? = null
        gate = ExternalActionGate(
            currentReadiness = { readiness },
            ensureReadiness = {
                observedPending = gate.pending("project-a")
                readiness = result(ExternalProviderReadiness.READY)
                readiness
            },
        )

        val decision = gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        )

        assertTrue(decision is ExternalActionGate.Decision.Proceed)
        assertEquals(ExternalActionGate.Action.RUN, observedPending?.action)
        assertEquals(1L, observedPending?.generation)
        assertNull(gate.pending("project-a"))
    }

    @Test
    fun asyncReadyCanBeClaimedOnlyOnce() {
        var readiness = result(ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED)
        val gate = ExternalActionGate(
            currentReadiness = { readiness },
            ensureReadiness = {
                readiness = result(ExternalProviderReadiness.BRIDGE_CHECKING)
                readiness
            },
        )

        val waiting = gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.PREPARE,
            origin = ExternalActionGate.Origin.DEVELOPER_MODE,
        ) as ExternalActionGate.Decision.Awaiting

        readiness = result(ExternalProviderReadiness.READY)
        val first = gate.claimReady(
            projectId = "project-a",
            origin = ExternalActionGate.Origin.DEVELOPER_MODE,
        )
        val second = gate.claimReady(
            projectId = "project-a",
            origin = ExternalActionGate.Origin.DEVELOPER_MODE,
        )

        assertEquals(waiting.request.generation, first?.generation)
        assertNull(second)
    }

    @Test
    fun duplicateSameActionKeepsOneGenerationAndOneContinuation() {
        var readiness = result(ExternalProviderReadiness.BRIDGE_CHECKING)
        val gate = ExternalActionGate(
            currentReadiness = { readiness },
            ensureReadiness = { readiness },
        )

        val first = gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        ) as ExternalActionGate.Decision.Awaiting
        val second = gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        ) as ExternalActionGate.Decision.Awaiting

        assertEquals(first.request.generation, second.request.generation)

        readiness = result(ExternalProviderReadiness.READY)
        assertEquals(
            first.request.generation,
            gate.claimReady("project-a", ExternalActionGate.Origin.NORMAL_MODE)?.generation,
        )
        assertNull(gate.claimReady("project-a", ExternalActionGate.Origin.NORMAL_MODE))
    }

    @Test
    fun stopCancelsOnlyThatProjectsDeferredActionAndFencesItsGeneration() {
        var readiness = result(ExternalProviderReadiness.BRIDGE_CHECKING)
        val gate = ExternalActionGate(
            currentReadiness = { readiness },
            ensureReadiness = { readiness },
        )

        val a = (gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        ) as ExternalActionGate.Decision.Awaiting).request
        val b = (gate.request(
            projectId = "project-b",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        ) as ExternalActionGate.Decision.Awaiting).request

        gate.cancel("project-a")
        readiness = result(ExternalProviderReadiness.READY)

        assertNull(gate.claimReady("project-a", ExternalActionGate.Origin.NORMAL_MODE))
        assertEquals(
            b.generation,
            gate.claimReady("project-b", ExternalActionGate.Origin.NORMAL_MODE)?.generation,
        )

        readiness = result(ExternalProviderReadiness.BRIDGE_CHECKING)
        val a2 = (gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        ) as ExternalActionGate.Decision.Awaiting).request
        assertTrue(a2.generation > a.generation)
    }

    @Test
    fun recoverableProviderFailureKeepsDeveloperRunUntilReadyAndResumesOnce() {
        var readiness = result(ExternalProviderReadiness.BRIDGE_CHECKING)
        val gate = ExternalActionGate(
            currentReadiness = { readiness },
            ensureReadiness = { readiness },
        )

        val waiting = gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.DEVELOPER_MODE,
        ) as ExternalActionGate.Decision.Awaiting

        readiness = result(ExternalProviderReadiness.BRIDGE_UNRESPONSIVE)
        gate.handlePreflightResult(readiness)
        assertEquals(waiting.request.generation, gate.pending("project-a")?.generation)

        readiness = result(ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED)
        gate.handlePreflightResult(readiness)
        assertEquals(waiting.request.generation, gate.pending("project-a")?.generation)

        readiness = result(ExternalProviderReadiness.READY)
        assertEquals(
            waiting.request.generation,
            gate.claimReady("project-a", ExternalActionGate.Origin.DEVELOPER_MODE)?.generation,
        )
        assertNull(gate.claimReady("project-a", ExternalActionGate.Origin.DEVELOPER_MODE))
    }

    @Test
    fun terminalUnavailableStillClearsDeferredRequestsWithoutStarting() {
        var readiness = result(ExternalProviderReadiness.BRIDGE_CHECKING)
        val gate = ExternalActionGate(
            currentReadiness = { readiness },
            ensureReadiness = { readiness },
        )

        gate.request(
            projectId = "project-a",
            action = ExternalActionGate.Action.RUN,
            origin = ExternalActionGate.Origin.DEVELOPER_MODE,
        )
        readiness = result(ExternalProviderReadiness.UNAVAILABLE)
        gate.handlePreflightResult(readiness)

        assertNull(gate.pending("project-a"))
    }

    private fun result(readiness: ExternalProviderReadiness) = ExternalProviderPreflightResult(
        readiness = readiness,
        termuxInstalled = readiness != ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
        runCommandPermissionGranted =
            readiness != ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED,
        allowExternalApps = if (readiness == ExternalProviderReadiness.READY) true else null,
        bridgeResponsive = if (readiness == ExternalProviderReadiness.READY) true else null,
        lastProbeAtEpochMs = if (readiness == ExternalProviderReadiness.READY) 1_000L else null,
    )
}
