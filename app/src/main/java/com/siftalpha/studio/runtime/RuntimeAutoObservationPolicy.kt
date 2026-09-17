package com.siftalpha.studio.runtime

/**
 * Small, runtime-neutral policy for observing one External Provider project.
 *
 * The Activity owns scheduling and lifecycle; this policy only decides the next control operation.
 * Embedded R never enters this policy because its Session owns its own snapshot polling.
 */
enum class RuntimeObservationStep {
    WAIT_FOR_PENDING,
    REQUEST_STATUS,
    REQUEST_FINAL_LOGS,
    STOP,
}

object RuntimeAutoObservationPolicy {
    data class Input(
        val state: RuntimeState,
        val hasPendingOperation: Boolean,
        val finalLogsCompleted: Boolean,
    )

    fun decide(input: Input): RuntimeObservationStep {
        if (input.hasPendingOperation) return RuntimeObservationStep.WAIT_FOR_PENDING

        return when (input.state) {
            RuntimeState.PREPARING,
            RuntimeState.STARTING,
            RuntimeState.RUNNING,
            RuntimeState.UNKNOWN -> RuntimeObservationStep.REQUEST_STATUS

            RuntimeState.EXITED_SUCCESS,
            RuntimeState.EXITED_ERROR -> if (input.finalLogsCompleted) {
                RuntimeObservationStep.STOP
            } else {
                RuntimeObservationStep.REQUEST_FINAL_LOGS
            }

            RuntimeState.STOPPED_BY_USER,
            RuntimeState.ENVIRONMENT_ERROR -> RuntimeObservationStep.STOP
        }
    }
}
