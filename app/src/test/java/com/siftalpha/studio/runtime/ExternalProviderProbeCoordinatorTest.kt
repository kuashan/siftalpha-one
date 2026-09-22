package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExternalProviderProbeCoordinatorTest {
    @Test
    fun coordinatorRequiresBridgeThenRuntimeCapabilityBeforeReady() {
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
        assertEquals(ExternalProviderProbeStage.BRIDGE, store.read().probeStage)
        assertNotNull(store.read().lastProbeExecutionId)

        val bridgeExecution = bridge.lastExecutionId
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = bridgeExecution,
                stdout = "SIFTALPHA_TERMUX_BRIDGE_OK\nTERMUX_PREFIX=/data/data/com.termux/files/usr\naarch64",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(ExternalProviderReadiness.BRIDGE_CHECKING, coordinator.current().readiness)
        assertEquals(TermuxBackend.RUNTIME_CAPABILITY_TEST, bridge.lastCommand)
        assertEquals(ExternalProviderProbeStage.RUNTIME_CAPABILITY, store.read().probeStage)

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = bridge.lastExecutionId,
                stdout = "SIFTALPHA_EXTERNAL_RUNTIME_OK\n",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(ExternalProviderReadiness.READY, coordinator.current().readiness)
        assertEquals(ExternalProviderProbeResult.PASS, store.read().lastProbeResult)
        assertEquals(ExternalProviderProbeStage.RUNTIME_CAPABILITY, store.read().probeStage)
    }

    @Test
    fun probeTimesOutAfterThreeSecondsAndBecomesRecoverable() {
        val store = MemoryReadinessStore()
        val bridge = FakeBridge()
        val scheduler = FakeTimeoutScheduler()
        val coordinator = ExternalProviderProbeCoordinator(
            bridge = bridge,
            store = store,
            nowEpochMs = { 4_000L },
            timeoutScheduler = scheduler,
        )

        assertEquals(
            ExternalProviderReadiness.BRIDGE_CHECKING,
            coordinator.ensureReady().readiness,
        )

        scheduler.runLatest()

        assertEquals(
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            coordinator.current().readiness,
        )
        assertEquals(
            ExternalProviderProbeCoordinator.PROBE_TIMEOUT_DETAIL,
            store.read().detail,
        )
    }

    @Test
    fun lateResultAfterProbeTimeoutIsIgnoredAndRetryCanSucceed() {
        val store = MemoryReadinessStore()
        val bridge = FakeBridge()
        val scheduler = FakeTimeoutScheduler()
        var now = 5_000L
        val coordinator = ExternalProviderProbeCoordinator(
            bridge = bridge,
            store = store,
            nowEpochMs = { now },
            timeoutScheduler = scheduler,
        )

        coordinator.ensureReady()
        val timedOutExecution = bridge.lastExecutionId
        scheduler.runLatest()
        assertEquals(
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            coordinator.current().readiness,
        )

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = timedOutExecution,
                stdout = "SIFTALPHA_TERMUX_BRIDGE_OK",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )
        assertEquals(
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            coordinator.current().readiness,
        )

        now = 6_000L
        assertEquals(
            ExternalProviderReadiness.BRIDGE_CHECKING,
            coordinator.probe().readiness,
        )
        val retryExecution = bridge.lastExecutionId
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = retryExecution,
                stdout = "SIFTALPHA_TERMUX_BRIDGE_OK",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )
        assertEquals(ExternalProviderReadiness.BRIDGE_CHECKING, coordinator.current().readiness)
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = bridge.lastExecutionId,
                stdout = "SIFTALPHA_EXTERNAL_RUNTIME_OK",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(ExternalProviderReadiness.READY, coordinator.current().readiness)
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
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 6102,
                stdout = "SIFTALPHA_EXTERNAL_RUNTIME_OK",
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
        private var nextExecutionIdValue = 6101
        val nextExecutionId: Int
            get() = lastExecutionId
        var lastExecutionId: Int = 6100
            private set
        var lastCommand: RuntimeCommand? = null

        override fun isTermuxInstalled(): Boolean = true

        override fun hasRunCommandPermission(): Boolean = true

        override fun execute(command: RuntimeCommand): Int {
            lastCommand = command
            lastExecutionId = nextExecutionIdValue++
            return lastExecutionId
        }
    }

    private class FakeTimeoutScheduler : ExternalProviderProbeTimeoutScheduler {
        private var latest: (() -> Unit)? = null

        override fun schedule(
            delayMs: Long,
            task: () -> Unit,
        ): ExternalProviderProbeTimeoutHandle {
            assertEquals(
                true,
                delayMs == ExternalProviderProbeCoordinator.DEFAULT_PROBE_RESPONSE_TIMEOUT_MS ||
                    delayMs ==
                    ExternalProviderProbeCoordinator.DEFAULT_CAPABILITY_PROBE_RESPONSE_TIMEOUT_MS,
            )
            latest = task
            var cancelled = false
            return ExternalProviderProbeTimeoutHandle {
                cancelled = true
                if (latest === task) latest = null
            }
        }

        fun runLatest() {
            val task = latest ?: error("No timeout scheduled")
            latest = null
            task()
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

        override fun markProbeDispatched(
            executionId: Int,
            stage: ExternalProviderProbeStage,
        ) {
            facts = facts.copy(
                bridgeState = ExternalProviderBridgeState.CHECKING,
                probeStage = stage,
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

        override fun recordProbeDispatchFailure(
            atEpochMs: Long,
            detail: String,
            stage: ExternalProviderProbeStage,
        ) {
            facts = facts.copy(
                bridgeState = ExternalProviderBridgeState.FAIL,
                probeStage = stage,
                lastProbeAtEpochMs = atEpochMs,
                lastProbeExecutionId = null,
                lastProbeResult = ExternalProviderProbeResult.FAIL,
                detail = detail,
            )
        }

        override fun recordProbeTimeout(executionId: Int, atEpochMs: Long, detail: String) {
            if (
                facts.bridgeState != ExternalProviderBridgeState.CHECKING ||
                facts.lastProbeExecutionId != executionId
            ) {
                return
            }
            facts = facts.copy(
                bridgeState = ExternalProviderBridgeState.UNRESPONSIVE,
                lastProbeAtEpochMs = atEpochMs,
                lastProbeExecutionId = executionId,
                lastProbeResult = null,
                detail = detail,
            )
        }
    }
}
