package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWorkerRuntimeUseGateTest {
    @Test
    fun oneNativeOwnerBlocksOtherOwnerUntilReleased() {
        val gate = EmbeddedPythonWorkerRuntimeUseGate()

        val projectLease = acquiredLease(
            gate,
            EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION,
        )
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.SameOwnerBusy,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION),
        )
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OtherOwnerBusy,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE),
        )

        gate.release(projectLease)
        assertTrue(
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE) is
                EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired,
        )
    }

    @Test
    fun smokeOwnerBlocksProjectUntilReleased() {
        val gate = EmbeddedPythonWorkerRuntimeUseGate()
        val smokeLease = acquiredLease(
            gate,
            EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE,
        )

        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.SameOwnerBusy,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE),
        )
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OtherOwnerBusy,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION),
        )

        gate.release(smokeLease)
        assertTrue(
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION) is
                EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired,
        )
    }

    @Test
    fun staleLeaseReleaseCannotReleaseNewOwnerLease() {
        val gate = EmbeddedPythonWorkerRuntimeUseGate()
        val leaseA = acquiredLease(
            gate,
            EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION,
        )

        gate.release(leaseA)
        val leaseB = acquiredLease(
            gate,
            EmbeddedPythonWorkerRuntimeUseGate.Owner.PROJECT_EXECUTION,
        )

        gate.release(leaseA)
        assertEquals(
            EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.OtherOwnerBusy,
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE),
        )

        gate.release(leaseB)
        assertTrue(
            gate.tryAcquire(EmbeddedPythonWorkerRuntimeUseGate.Owner.CPYTHON_SMOKE) is
                EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired,
        )
    }

    private fun acquiredLease(
        gate: EmbeddedPythonWorkerRuntimeUseGate,
        owner: EmbeddedPythonWorkerRuntimeUseGate.Owner,
    ): EmbeddedPythonWorkerRuntimeUseGate.Lease {
        val result = gate.tryAcquire(owner)
        assertTrue(result is EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired)
        return (result as EmbeddedPythonWorkerRuntimeUseGate.AcquireResult.Acquired).lease
    }
}
