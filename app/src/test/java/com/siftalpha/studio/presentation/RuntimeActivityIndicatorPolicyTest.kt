package com.siftalpha.studio.presentation

import com.siftalpha.studio.runtime.RuntimeLifecycleState
import com.siftalpha.studio.runtime.RuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeActivityIndicatorPolicyTest {

    @Test
    fun activePrepareAnimates() {
        assertTrue(
            RuntimeActivityIndicatorPolicy.shouldAnimate(
                RuntimeActivityIndicatorPolicy.Input(
                    lifecycleState = RuntimeLifecycleState.PREPARING,
                    runtimeState = RuntimeState.PREPARING,
                    operationActive = true,
                ),
            ),
        )
    }

    @Test
    fun staleStartingWithoutOperationDoesNotPretendWorkIsActive() {
        assertFalse(
            RuntimeActivityIndicatorPolicy.shouldAnimate(
                RuntimeActivityIndicatorPolicy.Input(
                    lifecycleState = RuntimeLifecycleState.STARTING,
                    runtimeState = RuntimeState.STARTING,
                    operationActive = false,
                ),
            ),
        )
    }

    @Test
    fun confirmedRunningKeepsActivityAnimation() {
        assertTrue(
            RuntimeActivityIndicatorPolicy.shouldAnimate(
                RuntimeActivityIndicatorPolicy.Input(
                    lifecycleState = RuntimeLifecycleState.RUNNING,
                    runtimeState = RuntimeState.RUNNING,
                    operationActive = false,
                ),
            ),
        )
    }

    @Test
    fun terminalStateDoesNotAnimate() {
        assertFalse(
            RuntimeActivityIndicatorPolicy.shouldAnimate(
                RuntimeActivityIndicatorPolicy.Input(
                    lifecycleState = RuntimeLifecycleState.STOPPED,
                    runtimeState = RuntimeState.STOPPED_BY_USER,
                    operationActive = false,
                ),
            ),
        )
    }
}
