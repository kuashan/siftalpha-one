package com.siftalpha.studio.project

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonProjectStagerTest {
    @Test
    fun copiesNestedProjectFilesAndPreservesRelativePaths() {
        val base = Files.createTempDirectory("siftalpha-stager").toFile()
        val destination = File(base, "stage")
        val nodes = listOf(
            node("pkg", directory = true),
            node("main.py"),
            node("pkg/helper.py"),
        )
        val contents = mapOf(
            "main.py" to "from pkg.helper import VALUE\nprint(VALUE)\n",
            "pkg/helper.py" to "VALUE = 'PROJECT_HELPER'\n",
        )
        try {
            val staged = EmbeddedPythonProjectStager.stageNodes(
                destination = destination,
                nodes = nodes,
                entrypoint = "main.py",
                limits = EmbeddedPythonStagingLimits(
                    maxNodes = 8,
                    maxFiles = 4,
                    maxFileBytes = 256,
                    maxTotalBytes = 1024,
                ),
            ) { node, _ -> contents.getValue(node.relativePath).toByteArray() }

            assertEquals("from pkg.helper import VALUE\nprint(VALUE)\n", File(staged, "main.py").readText())
            assertEquals("VALUE = 'PROJECT_HELPER'\n", File(staged, "pkg/helper.py").readText())
            assertTrue(File(staged, "pkg").isDirectory)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsafeRelativePathBeforeCreatingStagingRoot() {
        val base = Files.createTempDirectory("siftalpha-stager-unsafe").toFile()
        val destination = File(base, "stage")
        try {
            assertFails {
                EmbeddedPythonProjectStager.stageNodes(
                    destination = destination,
                    nodes = listOf(node("main.py"), node("../outside.py")),
                    entrypoint = "main.py",
                ) { _, _ -> ByteArray(0) }
            }
            assertFalse(destination.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun enforcesFileCountAndByteLimits() {
        val base = Files.createTempDirectory("siftalpha-stager-limits").toFile()
        try {
            val countDestination = File(base, "count")
            assertFails {
                EmbeddedPythonProjectStager.stageNodes(
                    destination = countDestination,
                    nodes = listOf(node("main.py"), node("helper.py")),
                    entrypoint = "main.py",
                    limits = EmbeddedPythonStagingLimits(
                        maxNodes = 4,
                        maxFiles = 1,
                        maxFileBytes = 32,
                        maxTotalBytes = 64,
                    ),
                ) { _, _ -> "x".toByteArray() }
            }
            assertFalse(countDestination.exists())

            val fileDestination = File(base, "file")
            assertFails {
                EmbeddedPythonProjectStager.stageNodes(
                    destination = fileDestination,
                    nodes = listOf(node("main.py")),
                    entrypoint = "main.py",
                    limits = EmbeddedPythonStagingLimits(
                        maxNodes = 2,
                        maxFiles = 1,
                        maxFileBytes = 3,
                        maxTotalBytes = 32,
                    ),
                ) { _, _ -> "four".toByteArray() }
            }
            assertFalse(fileDestination.exists())

            val totalDestination = File(base, "total")
            assertFails {
                EmbeddedPythonProjectStager.stageNodes(
                    destination = totalDestination,
                    nodes = listOf(node("main.py"), node("helper.py")),
                    entrypoint = "main.py",
                    limits = EmbeddedPythonStagingLimits(
                        maxNodes = 4,
                        maxFiles = 2,
                        maxFileBytes = 32,
                        maxTotalBytes = 3,
                    ),
                ) { _, _ -> "xx".toByteArray() }
            }
            assertFalse(totalDestination.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun removesPartialStagingWhenSourceReadFails() {
        val base = Files.createTempDirectory("siftalpha-stager-failure").toFile()
        val destination = File(base, "stage")
        try {
            assertFails {
                EmbeddedPythonProjectStager.stageNodes(
                    destination = destination,
                    nodes = listOf(node("main.py"), node("helper.py")),
                    entrypoint = "main.py",
                ) { node, _ ->
                    if (node.relativePath == "helper.py") error("simulated SAF read failure")
                    "print('partial')".toByteArray()
                }
            }
            assertFalse(destination.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    private fun node(path: String, directory: Boolean = false): ProjectStore.FileNode =
        ProjectStore.FileNode(
            name = path.substringAfterLast('/'),
            relativePath = path,
            documentId = path,
            mimeType = if (directory) "vnd.android.document/directory" else "text/plain",
            depth = path.count { it == '/' },
            isDirectory = directory,
        )

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected staging failure")
        } catch (_: IllegalArgumentException) {
            // expected
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
