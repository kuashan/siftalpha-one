package com.siftalpha.core.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeLifecycleCoreTest {
    @Test
    fun operationHasPriorityOverPersistedFacts() {
        assertEquals(
            ProjectLifecycleState.PREPARING,
            RuntimeLifecyclePolicy.resolve(
                environmentReady = false,
                runtimeState = RuntimeExecutionState.UNKNOWN,
                operation = ProjectLifecycleOperation.PREPARE,
            ),
        )
        assertEquals(
            ProjectLifecycleState.STOPPING,
            RuntimeLifecyclePolicy.resolve(
                environmentReady = true,
                runtimeState = RuntimeExecutionState.RUNNING,
                operation = ProjectLifecycleOperation.STOP,
            ),
        )
    }

    @Test
    fun recoveryAppliesOnlyWithoutCurrentOperation() {
        assertEquals(
            ProjectLifecycleState.RECOVERING,
            RuntimeLifecyclePolicy.resolve(
                environmentReady = true,
                runtimeState = RuntimeExecutionState.RUNNING,
                recoveryInProgress = true,
            ),
        )
        assertEquals(
            ProjectLifecycleState.CHECKING,
            RuntimeLifecyclePolicy.resolve(
                environmentReady = true,
                runtimeState = RuntimeExecutionState.RUNNING,
                operation = ProjectLifecycleOperation.STATUS,
                recoveryInProgress = true,
            ),
        )
    }

    @Test
    fun environmentConfigurationAndTerminalRulesRemainStable() {
        assertEquals(
            ProjectLifecycleState.ENVIRONMENT_NOT_PREPARED,
            RuntimeLifecyclePolicy.resolve(false, RuntimeExecutionState.UNKNOWN),
        )
        assertEquals(
            ProjectLifecycleState.NEEDS_CONFIGURATION,
            RuntimeLifecyclePolicy.resolve(
                environmentReady = true,
                runtimeState = RuntimeExecutionState.EXITED_ERROR,
                configurationRequired = true,
            ),
        )
        assertEquals(
            ProjectLifecycleState.RUN_FAILED,
            RuntimeLifecyclePolicy.resolve(true, RuntimeExecutionState.EXITED_ERROR),
        )
        assertEquals(
            ProjectLifecycleState.STOPPED,
            RuntimeLifecyclePolicy.resolve(true, RuntimeExecutionState.EXITED_SUCCESS),
        )
        assertEquals(
            ProjectLifecycleState.READY_TO_RUN,
            RuntimeLifecyclePolicy.resolve(true, RuntimeExecutionState.UNKNOWN),
        )
    }

    @Test
    fun currentExitedGuestStateWinsOverStaleRunningEvidence() {
        val output = """
            SIFTALPHA_STATUS=RUNNING
            STATE=EXITED
            EXIT_CODE=7
            SIFTALPHA_PROCESS_EXIT=7
        """.trimIndent()

        assertEquals(
            RuntimeExecutionState.EXITED_ERROR,
            RuntimeOutputStateParser.fromOutput(output),
        )
        assertEquals(7, RuntimeOutputStateParser.extractExitCode(output))
    }

    @Test
    fun currentRunningGuestStateWinsOverHistoricalExitedText() {
        val output = """
            SIFTALPHA_STATUS=RUNNING
            STATE=RUNNING
            previous diagnostic text
            STATE=EXITED
            EXIT_CODE=7
        """.trimIndent()

        assertEquals(
            RuntimeExecutionState.RUNNING,
            RuntimeOutputStateParser.fromOutput(output),
        )
    }

    @Test
    fun explicitReconciledStateIsAuthoritative() {
        val output = """
            SIFTALPHA_STATUS=RUNNING
            STATE=RUNNING
            SIFTALPHA_RUNTIME_STATE=EXITED_ERROR
        """.trimIndent()

        assertEquals(
            RuntimeExecutionState.EXITED_ERROR,
            RuntimeOutputStateParser.fromOutput(output),
        )
    }
}
