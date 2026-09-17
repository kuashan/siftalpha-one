package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeAutoObservationPolicyTest {

    @Test
    fun activeExternalRuntimeRequestsStatusWhenIdle() {
        assertEquals(
            RuntimeObservationStep.REQUEST_STATUS,
            RuntimeAutoObservationPolicy.decide(
                RuntimeAutoObservationPolicy.Input(
                    state = RuntimeState.RUNNING,
                    hasPendingOperation = false,
                    finalLogsCompleted = false,
                ),
            ),
        )
    }

    @Test
    fun pendingOperationDelaysAutomaticObservation() {
        assertEquals(
            RuntimeObservationStep.WAIT_FOR_PENDING,
            RuntimeAutoObservationPolicy.decide(
                RuntimeAutoObservationPolicy.Input(
                    state = RuntimeState.RUNNING,
                    hasPendingOperation = true,
                    finalLogsCompleted = false,
                ),
            ),
        )
    }

    @Test
    fun terminalExternalRuntimeRequestsOneFinalLogRead() {
        assertEquals(
            RuntimeObservationStep.REQUEST_FINAL_LOGS,
            RuntimeAutoObservationPolicy.decide(
                RuntimeAutoObservationPolicy.Input(
                    state = RuntimeState.EXITED_SUCCESS,
                    hasPendingOperation = false,
                    finalLogsCompleted = false,
                ),
            ),
        )
        assertEquals(
            RuntimeObservationStep.STOP,
            RuntimeAutoObservationPolicy.decide(
                RuntimeAutoObservationPolicy.Input(
                    state = RuntimeState.EXITED_SUCCESS,
                    hasPendingOperation = false,
                    finalLogsCompleted = true,
                ),
            ),
        )
    }

    @Test
    fun userStoppedOrEnvironmentFailureStopsObservation() {
        listOf(RuntimeState.STOPPED_BY_USER, RuntimeState.ENVIRONMENT_ERROR).forEach { state ->
            assertEquals(
                RuntimeObservationStep.STOP,
                RuntimeAutoObservationPolicy.decide(
                    RuntimeAutoObservationPolicy.Input(
                        state = state,
                        hasPendingOperation = false,
                        finalLogsCompleted = false,
                    ),
                ),
            )
        }
    }
}
