package com.siftalpha.studio.container

import org.junit.Assert.assertEquals
import org.junit.Test

class PortConflictResolutionTest {
    private val binding = RequestedPortBinding(18080, 80)

    @Test fun freePortKeepsRequestedBinding() = assertEquals(
        PortResolutionPlan.KeepRequestedPort,
        PortConflictResolutionPolicy.resolve(binding, PortBindingOwnership.FREE, PortRemapCapability.SUPPORTED),
    )

    @Test fun externalConflictRemapsWhenRuntimeSupportsIt() = assertEquals(
        PortResolutionPlan.RemapAutomatically(18080, PortBindingOwnership.EXTERNAL_PROCESS),
        PortConflictResolutionPolicy.resolve(binding, PortBindingOwnership.EXTERNAL_PROCESS, PortRemapCapability.SUPPORTED),
    )

    @Test fun fixedConflictBlocksSafely() = assertEquals(
        PortResolutionPlan.BlockSafely(18080, PortBindingOwnership.EXTERNAL_PROCESS),
        PortConflictResolutionPolicy.resolve(binding, PortBindingOwnership.EXTERNAL_PROCESS, PortRemapCapability.UNSUPPORTED),
    )
}
