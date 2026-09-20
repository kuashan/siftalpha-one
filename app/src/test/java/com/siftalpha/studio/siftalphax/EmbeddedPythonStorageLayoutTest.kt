package com.siftalpha.studio.siftalphax

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.siftalpha.studio.runtime.InterruptibleProjectTreeDelete
import com.siftalpha.studio.storage.SiftAlphaStorage
import org.junit.Test

class EmbeddedPythonStorageLayoutTest {
    @Test
    fun ownedStorageDomainsAreIndependentSiblings() {
        val filesDir = Files.createTempDirectory("siftalpha-storage").toFile()
        try {
            val runtime = EmbeddedPythonFiles.runtimeHome(filesDir)
            val staging = EmbeddedPythonFiles.projectStagingRoot(filesDir)
            val environment = EmbeddedPythonFiles.projectEnvironmentRoot(filesDir, "project-a")
            val cache = EmbeddedPythonFiles.dependencyCacheRoot(filesDir)
            val session = EmbeddedPythonFiles.sessionWorkspaceRoot(filesDir, "session-a")
            val root = filesDir.resolve("SiftAlphaX").canonicalFile
            assertEquals(root.resolve("runtimes").canonicalFile, runtime.parentFile.canonicalFile)
            assertEquals(root, staging.parentFile.canonicalFile)
            assertEquals(root.resolve("environments/cpython").canonicalFile, environment.parentFile.canonicalFile)
            assertEquals(root.resolve("wheelhouse").canonicalFile, cache)
            assertEquals(root.resolve("sessions/cpython").canonicalFile, session.parentFile.canonicalFile)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun identitiesCannotEscapeOwnedRoots() {
        val filesDir = Files.createTempDirectory("siftalpha-storage").toFile()
        try {
            val first = EmbeddedPythonFiles.projectEnvironmentRoot(filesDir, "../project")
            val second = EmbeddedPythonFiles.projectEnvironmentRoot(filesDir, "project")
            val session = EmbeddedPythonFiles.sessionWorkspaceRoot(filesDir, "../../session")
            assertNotEquals(first, second)
            assertTrue(first.name.matches(Regex("[0-9a-f]{64}")))
            assertTrue(second.name.matches(Regex("[0-9a-f]{64}")))
            assertTrue(session.name.matches(Regex("[0-9a-f]{64}")))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun runtimeRepairTargetDoesNotDeleteCallerProjectStaging() {
        val filesDir = Files.createTempDirectory("siftalpha-runtime-repair").toFile()
        try {
            val privateRoot = SiftAlphaStorage.runtimesRoot(filesDir).apply { mkdirs() }
            val runtime = EmbeddedPythonFiles.runtimeHome(filesDir).apply {
                mkdirs()
                resolve("partial.txt").writeText("partial")
            }
            val stagedProject = EmbeddedPythonFiles.projectStagingRoot(filesDir).resolve("caller-project").apply {
                mkdirs()
                resolve("main.py").writeText("print('keep')")
            }

            InterruptibleProjectTreeDelete.delete(runtime, privateRoot)

            assertFalse(runtime.exists())
            assertTrue(stagedProject.resolve("main.py").isFile)
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
