package com.siftalpha.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStorageCoreTest {
    @Test
    fun cleanupPolicyOnlySelectsInactiveReproducibleCleanableEntries() {
        val safe = RuntimeStorageEntry(
            id = "safe",
            label = "safe",
            kind = RuntimeStorageKind.ORPHAN_PROJECT_DATA,
            sizeBytes = 100,
            reproducible = true,
            cleanable = true,
        )
        val active = RuntimeStorageEntry(
            id = "active",
            label = "active",
            kind = RuntimeStorageKind.PROJECT_ENVIRONMENT,
            sizeBytes = 200,
            active = true,
            reproducible = true,
            cleanable = false,
        )
        val state = RuntimeStorageEntry(
            id = "state",
            label = "state",
            kind = RuntimeStorageKind.PLATFORM_RUNTIME_STATE,
            sizeBytes = 300,
            reproducible = false,
            cleanable = false,
        )
        val snapshot = RuntimeStorageSnapshot(listOf(safe, active, state))

        assertTrue(RuntimeStorageCleanupPolicy.canClean(safe))
        assertFalse(RuntimeStorageCleanupPolicy.canClean(active))
        assertEquals(listOf("safe"), RuntimeStorageCleanupPolicy.safeEntries(snapshot).map { it.id })
        assertEquals(600L, snapshot.totalBytes)
        assertEquals(100L, snapshot.cleanableBytes)
        assertEquals(100L, snapshot.orphanBytes)
    }
}
