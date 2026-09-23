package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeForegroundRecoveryGateTest {
    @Test
    fun firstActivityStartRunsPersistedRecovery() {
        val gate = RuntimeForegroundRecoveryGate()

        assertTrue(gate.consumeInitialRecovery())
    }

    @Test
    fun normalBackgroundForegroundDoesNotReenterPersistedRecovery() {
        val gate = RuntimeForegroundRecoveryGate()

        assertTrue(gate.consumeInitialRecovery())
        assertFalse(gate.consumeInitialRecovery())
        assertFalse(gate.consumeInitialRecovery())
    }

    @Test
    fun recreatedActivityGetsItsOwnSingleRecoveryPass() {
        val firstActivity = RuntimeForegroundRecoveryGate()
        assertTrue(firstActivity.consumeInitialRecovery())
        assertFalse(firstActivity.consumeInitialRecovery())

        val recreatedActivity = RuntimeForegroundRecoveryGate()
        assertTrue(recreatedActivity.consumeInitialRecovery())
        assertFalse(recreatedActivity.consumeInitialRecovery())
    }
}
