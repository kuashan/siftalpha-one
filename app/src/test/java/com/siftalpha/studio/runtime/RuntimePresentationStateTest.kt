package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimePresentationStateTest {

    @Test
    fun webReadinessCannotRewriteRunningProcess() {
        assertEquals(
            RuntimeState.RUNNING,
            RuntimePresentationState.resolve(runtimeState = RuntimeState.RUNNING),
        )
    }

    @Test
    fun verifiedWebProcessKeepsRunningState() {
        assertEquals(
            RuntimeState.RUNNING,
            RuntimePresentationState.resolve(runtimeState = RuntimeState.RUNNING),
        )
    }

    @Test
    fun nonWebProcessKeepsRunningState() {
        assertEquals(
            RuntimeState.RUNNING,
            RuntimePresentationState.resolve(runtimeState = RuntimeState.RUNNING),
        )
    }

    @Test
    fun terminalStatesRemainTerminal() {
        assertEquals(
            RuntimeState.EXITED_ERROR,
            RuntimePresentationState.resolve(runtimeState = RuntimeState.EXITED_ERROR),
        )
    }

    @Test
    fun naturalCompletionAndUserStopKeepDistinctTerminalSemantics() {
        assertEquals(
            RuntimeState.EXITED_SUCCESS,
            RuntimePresentationState.terminalStateForLabel(RuntimeState.EXITED_SUCCESS),
        )
        assertEquals(
            RuntimeState.STOPPED_BY_USER,
            RuntimePresentationState.terminalStateForLabel(RuntimeState.STOPPED_BY_USER),
        )
        assertNotEquals(RuntimeState.EXITED_SUCCESS, RuntimeState.STOPPED_BY_USER)
        assertNull(RuntimePresentationState.terminalStateForLabel(RuntimeState.RUNNING))
    }
}
