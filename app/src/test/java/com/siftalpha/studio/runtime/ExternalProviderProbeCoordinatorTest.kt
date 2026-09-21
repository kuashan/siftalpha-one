package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExternalProviderProbeCoordinatorTest {
    @Test
    fun coordinatorDispatchesTheExistingConnectionTestAndUpdatesSharedStore() {
        val store = MemoryReadinessStore()
        val bridge = FakeBridge()
        val coordinator = ExternalProviderProbeCoordinator(
            bridge = bridge,
            store = store,
            nowEpochMs = { 1_000L },
        )

        val checking = coordinator.ensureReady()

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECKING, checking.readiness)
        assertEquals(TermuxBackend.CONNECTION_TEST, bridge.lastCommand)
        assertNotNull(store.read().lastProbeExecutionId)

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = bridge.nextExecutionId,
                stdout = "SIFTALPHA_TERMUX_BRIDGE_OK\nTERMUX_PREFIX=/data/data/com.termux/files/usr\naarch64",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(ExternalProviderReadiness.READY, coordinator.current().readiness)
        assertEquals(ExternalProviderProbeResult.PASS, store.read().lastProbeResult)
    }

    @Test
    fun normalAndDeveloperSurfacesReadTheSameSharedReadinessFacts() {
        val store = MemoryReadinessStore()
        val normal = ExternalProviderProbeCoordinator(
            bridge = FakeBridge(),
            store = store,
            nowEpochMs = { 2_000L },
        )
        val developer = ExternalProviderProbeCoordinator(
            bridge = FakeBridge(),
            store = store,
            nowEpochMs = { 2_000L },
        )

        assertEquals(normal.current(), developer.current())
        normal.ensureReady()
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 6101,
                stdout = "SIFTALPHA_TERMUX_BRIDGE_OK",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(ExternalProviderReadiness.READY, normal.current().readiness)
        assertEquals(normal.current(), developer.current())
    }

    private class FakeBridge : ExternalProviderBridge {
        val nextExecutionId = 6101
        var lastCommand: RuntimeCommand? = null

        override fun isTermuxInstalled(): Boolean = true

        override fun hasRunCommandPermission(): Boolean = true

        override fun execute(command: RuntimeCommand): Int {
            lastCommand = command
            return nextExecutionId
        }
    }

    private class MemoryReadinessStore : ExternalProviderReadinessStateStore {
        private var facts = ExternalProviderFacts(
            termuxInstalled = false,
            runCommandPermissionGranted = false,
            bridgeState = ExternalProviderBridgeState.UNKNOWN,
        )

        override fun read(): ExternalProviderFacts = facts

        override fun recordHostFacts(termuxInstalled: Boolean, runCommandPermissionGranted: Boolean) {
            facts = facts.copy(
                termuxInstalled = termuxInstalled,
                runCommandPermissionGranted = runCommandPermissionGranted,
            )
        }

        override fun markProbeDispatched(executionId: Int) {
            facts = facts.copy(
                bridgeState = ExternalProviderBridgeState.CHECKING,
                lastProbeExecutionId = executionId,
            )
        }

        override fun recordProbe(
            executionId: Int,
            result: ExternalProviderProbeResult,
            atEpochMs: Long,
            detail: String?,
        ) {
            facts = facts.copy(
                bridgeState = if (result == ExternalProviderProbeResult.PASS) {
                    ExternalProviderBridgeState.PASS
                } else {
                    ExternalProviderBridgeState.FAIL
                },
                lastProbeExecutionId = executionId,
                lastProbeAtEpochMs = atEpochMs,
                lastProbeResult = result,
                detail = detail,
            )
        }

        override fun recordProbeDispatchFailure(atEpochMs: Long, detail: String) {
            facts = facts.copy(
                bridgeState = ExternalProviderBridgeState.FAIL,
                lastProbeAtEpochMs = atEpochMs,
                lastProbeResult = ExternalProviderProbeResult.FAIL,
                detail = detail,
            )
        }
    }
}
