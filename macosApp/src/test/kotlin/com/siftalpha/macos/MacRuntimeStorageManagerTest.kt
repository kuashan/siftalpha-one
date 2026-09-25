package com.siftalpha.macos

import com.siftalpha.core.storage.RuntimeStorageKind
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacRuntimeStorageManagerTest {
    @Test
    fun safeCleanupRemovesProvenOrphanAndOldToolchainButPreservesCurrentData() {
        val root = Files.createTempDirectory("siftalpha-storage-").toFile()
        val userHome = root.resolve("home").apply { mkdirs() }
        val dataRoot = root.resolve("data")
        val processControl = MacProjectProcessControl()
        val environmentManager = MacProjectEnvironmentManager(
            processControl = processControl,
            managedPython = null,
            dataRoot = dataRoot,
        )
        try {
            val knownId = "macos:known"
            val known = environmentManager.managedProjectDirectory(knownId).apply { mkdirs() }
            known.resolve("payload").writeText("keep")

            val orphan = dataRoot.resolve("projects/macos_orphan").apply { mkdirs() }
            orphan.resolve(".siftalpha-project-id").writeText("macos:orphan\n")
            orphan.resolve("payload").writeText("remove")

            val unknown = dataRoot.resolve("projects/legacy-unknown").apply { mkdirs() }
            unknown.resolve("payload").writeText("keep conservatively")

            val toolchainRoot = MacManagedContainerToolchain.root(userHome).apply { mkdirs() }
            val current = toolchainRoot.resolve("managed-current").apply { mkdirs() }
            current.resolve("bin").mkdirs()
            current.resolve("bin/docker").writeText("current")
            val old = toolchainRoot.resolve("managed-old").apply { mkdirs() }
            old.resolve("payload").writeText("old")
            toolchainRoot.resolve("current.txt").writeText("managed-current\n")

            val manager = MacRuntimeStorageManager(
                dataRoot = dataRoot,
                environmentManager = environmentManager,
                isProjectActive = { false },
                userHome = userHome,
            )
            val before = manager.snapshot(listOf(knownId), containerProjectActive = false)

            assertTrue(
                before.entries.any {
                    it.kind == RuntimeStorageKind.ORPHAN_PROJECT_DATA &&
                        it.projectId == "macos:orphan" &&
                        it.cleanable
                },
            )
            assertTrue(
                before.entries.any {
                    it.kind == RuntimeStorageKind.ORPHAN_PROJECT_DATA &&
                        it.projectId == null &&
                        !it.cleanable
                },
            )
            assertTrue(before.cleanableBytes > 0L)

            manager.cleanSafeEntries(listOf(knownId), containerProjectActive = false)

            assertTrue(known.isDirectory)
            assertFalse(orphan.exists())
            assertTrue(unknown.isDirectory)
            assertTrue(current.isDirectory)
            assertFalse(old.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun orphanWithComposeMarkerIsConservativelyPreserved() {
        val root = Files.createTempDirectory("siftalpha-storage-compose-").toFile()
        val dataRoot = root.resolve("data")
        val env = MacProjectEnvironmentManager(MacProjectProcessControl(), null, dataRoot)
        try {
            val orphan = dataRoot.resolve("projects/compose-orphan").apply { mkdirs() }
            orphan.resolve(".siftalpha-project-id").writeText("macos:compose-orphan\n")
            orphan.resolve("compose-prepared.ready").writeText("ready\n")

            val manager = MacRuntimeStorageManager(
                dataRoot = dataRoot,
                environmentManager = env,
                isProjectActive = { false },
                userHome = root.resolve("home").apply { mkdirs() },
            )
            val snapshot = manager.snapshot(emptyList(), containerProjectActive = false)

            assertTrue(snapshot.entries.any { it.projectId == "macos:compose-orphan" && !it.cleanable })
            manager.cleanSafeEntries(emptyList(), containerProjectActive = false)
            assertTrue(orphan.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
