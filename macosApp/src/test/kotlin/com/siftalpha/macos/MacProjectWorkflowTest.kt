package com.siftalpha.macos

import com.siftalpha.studio.runtime.RuntimeKind
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacProjectWorkflowTest {
    @Test
    fun importsPythonProjectAndBuildsProviderNeutralPlan() {
        val root = Files.createTempDirectory("siftalpha-m4-python-").toFile()
        try {
            root.resolve("main.py").writeText("print('ok')\n")
            root.resolve("requirements.txt").writeText("requests==2.32.5\nhttpx==0.28.1\n")

            val fs = MacProjectFilesystem()
            val plan = MacProjectWorkflowPlanner.plan(
                MacProjectSnapshotBuilder(fs).build(fs.importDirectory(root)),
                listOf(
                    MacHostToolSnapshot(
                        MacHostToolKind.PYTHON,
                        MacHostToolAvailability.AVAILABLE,
                        "/usr/local/bin/python3",
                        "Python 3.14.7",
                    ),
                ),
            )

            assertEquals(RuntimeKind.PYTHON, plan.needs?.primaryRuntime)
            assertEquals(2, plan.needs?.directDependencyCount)
            assertEquals(MacProjectPlanStatus.READY_TO_PREPARE, plan.status)
            assertTrue(plan.runtimeExecutables[RuntimeKind.PYTHON]!!.endsWith("python3"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingRequiredHostRuntimeIsExplicit() {
        val root = Files.createTempDirectory("siftalpha-m4-node-").toFile()
        try {
            root.resolve("package.json").writeText("""{"name":"demo"}""")
            root.resolve("index.js").writeText("console.log('ok')\n")

            val fs = MacProjectFilesystem()
            val plan = MacProjectWorkflowPlanner.plan(
                MacProjectSnapshotBuilder(fs).build(fs.importDirectory(root)),
                emptyList(),
            )

            assertEquals(RuntimeKind.NODE_JS, plan.needs?.primaryRuntime)
            assertEquals(MacProjectPlanStatus.RUNTIME_MISSING, plan.status)
            assertTrue(plan.issues.any { "nodejs" in it })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun conflictingRootRuntimesFailClosed() {
        val root = Files.createTempDirectory("siftalpha-m4-ambiguous-").toFile()
        try {
            root.resolve("requirements.txt").writeText("requests\n")
            root.resolve("main.py").writeText("print('x')\n")
            root.resolve("package.json").writeText("""{"name":"demo"}""")

            val fs = MacProjectFilesystem()
            val plan = MacProjectWorkflowPlanner.plan(
                MacProjectSnapshotBuilder(fs).build(fs.importDirectory(root)),
                emptyList(),
            )

            assertEquals(MacProjectPlanStatus.BLOCKED, plan.status)
            assertTrue(plan.issues.any { "ambiguous" in it })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun symlinkEscapeIsNotImportedIntoSnapshot() {
        val root = Files.createTempDirectory("siftalpha-m4-root-").toFile()
        val outside = Files.createTempDirectory("siftalpha-m4-outside-").toFile()
        try {
            outside.resolve("secret.txt").writeText("secret")
            runCatching { Files.createSymbolicLink(root.toPath().resolve("escape"), outside.toPath()) }

            val fs = MacProjectFilesystem()
            val snapshot = MacProjectSnapshotBuilder(fs).build(fs.importDirectory(root))
            assertTrue("escape" !in snapshot.relativePaths)
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
