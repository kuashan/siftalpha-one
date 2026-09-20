package com.siftalpha.studio.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiftAlphaStorageTest {
    @Test
    fun firstR47InitializationResetsOnlyKnownAppPrivateLegacyRoots() {
        val sandbox = Files.createTempDirectory("siftalpha-r47-storage").toFile()
        try {
            val filesDir = File(sandbox, "app-files").apply { mkdirs() }
            val cacheDir = File(sandbox, "app-cache").apply { mkdirs() }
            val userSource = File(sandbox, "AcodeProjects/keep/main.py").apply {
                parentFile!!.mkdirs()
                writeText("print('user-owned')")
            }

            File(filesDir, "siftalphax/environments/old").apply {
                parentFile!!.mkdirs()
                writeText("legacy")
            }
            File(filesDir, "siftalpha-results/old.html").apply {
                parentFile!!.mkdirs()
                writeText("legacy")
            }
            File(cacheDir, "siftalpha-proot-tmp/old").apply {
                parentFile!!.mkdirs()
                writeText("legacy")
            }
            File(cacheDir, "siftalpha-proot-pids/old.pid").apply {
                parentFile!!.mkdirs()
                writeText("1")
            }

            var resets = 0
            SiftAlphaStorage.initialize(filesDir, cacheDir) { resets++ }

            assertEquals(1, resets)
            assertFalse(File(filesDir, "siftalphax").exists())
            assertFalse(File(filesDir, "siftalpha-results").exists())
            assertFalse(File(cacheDir, "siftalpha-proot-tmp").exists())
            assertFalse(File(cacheDir, "siftalpha-proot-pids").exists())
            assertTrue(userSource.isFile)

            val root = SiftAlphaStorage.root(filesDir)
            assertEquals(SiftAlphaStorage.layoutText(), File(root, SiftAlphaStorage.LAYOUT_FILE).readText())
            assertTrue(SiftAlphaStorage.cpythonRuntimeRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.alpineRuntimeRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.cpythonEnvironmentsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.alpineEnvironmentsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.projectsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.cpythonSessionsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.alpineSessionsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.logsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.resultsRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.wheelhouseRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.stateRoot(filesDir).isDirectory)
            assertTrue(SiftAlphaStorage.tempRoot(filesDir).isDirectory)

            val sentinel = File(SiftAlphaStorage.resultsRoot(filesDir), "keep.txt").apply {
                writeText("do-not-reset-again")
            }
            SiftAlphaStorage.initialize(filesDir, cacheDir) { resets++ }

            assertEquals(1, resets)
            assertTrue(sentinel.isFile)
            assertEquals("do-not-reset-again", sentinel.readText())
        } finally {
            sandbox.deleteRecursively()
        }
    }
}
