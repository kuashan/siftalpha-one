package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedPythonWorkerRuntimeUseGateTest {
    @Test
    fun oneNativeOwnerBlocksOtherOwnerUntilReleased() {
        val gate = EmbeddedPythonWorkerRuntimeUseGate()

        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.ACQUIRED,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION),
        )
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.SAME_OWNER_BUSY,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION),
        )
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OTHER_OWNER_BUSY,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE),
        )

        gate.release(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION)
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.ACQUIRED,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE),
        )
    }
}
