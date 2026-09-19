package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeLifecycleResolverTest {
    @Test
    fun mapsEnvironmentAndOperationStates() {
        assertEquals(
            RuntimeLifecycleState.ENVIRONMENT_NOT_PREPARED,
            RuntimeLifecycleResolver.resolve(false, RuntimeState.UNKNOWN),
        )
        assertEquals(
            RuntimeLifecycleState.PREPARING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = false,
                runtimeState = RuntimeState.UNKNOWN,
                operation = RuntimeLifecycleOperation.PREPARE,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.STARTING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = true,
                runtimeState = RuntimeState.STARTING,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.PREPARING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = null,
                runtimeState = RuntimeState.PREPARING,
                operation = RuntimeLifecycleOperation.NONE,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.CHECKING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = true,
                runtimeState = RuntimeState.UNKNOWN,
                operation = RuntimeLifecycleOperation.STATUS,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.STOPPING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = true,
                runtimeState = RuntimeState.RUNNING,
                operation = RuntimeLifecycleOperation.STOP,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.CLEANING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = true,
                runtimeState = RuntimeState.UNKNOWN,
                operation = RuntimeLifecycleOperation.CLEAN,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.RECOVERING,
            RuntimeLifecycleResolver.resolve(
                environmentReady = true,
                runtimeState = RuntimeState.RUNNING,
                recoveryInProgress = true,
            ),
        )
    }

    @Test
    fun mapsConfigurationAndTerminalStates() {
        assertEquals(
            RuntimeLifecycleState.NEEDS_CONFIGURATION,
            RuntimeLifecycleResolver.resolve(
                environmentReady = true,
                runtimeState = RuntimeState.EXITED_ERROR,
                configurationRequired = true,
            ),
        )
        assertEquals(
            RuntimeLifecycleState.READY_TO_RUN,
            RuntimeLifecycleResolver.resolve(true, RuntimeState.UNKNOWN),
        )
        assertEquals(
            RuntimeLifecycleState.RUNNING,
            RuntimeLifecycleResolver.resolve(true, RuntimeState.RUNNING),
        )
        assertEquals(
            RuntimeLifecycleState.STOPPED,
            RuntimeLifecycleResolver.resolve(true, RuntimeState.STOPPED_BY_USER),
        )
        assertEquals(
            RuntimeLifecycleState.RUN_FAILED,
            RuntimeLifecycleResolver.resolve(true, RuntimeState.EXITED_ERROR),
        )
    }
}
