package com.siftalpha.macos

import com.siftalpha.studio.runtime.RuntimeKind
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacM42WorkflowPolicyTest {
    @Test
    fun managedPythonCanSatisfyMissingHostPythonPlan() {
        val root = Files.createTempDirectory("siftalpha-m42-plan-").toFile()
        try {
            root.resolve("main.py").writeText("print('ok')\n")
            root.resolve("requirements.txt").writeText("idna==3.10\n")
            val fs = MacProjectFilesystem()
            val project = fs.importDirectory(root)
            val snapshot = MacProjectSnapshotBuilder(fs).build(project)
            val plan = MacProjectWorkflowPlanner.plan(
                snapshot = snapshot,
                hostTools = emptyList(),
                managedPythonExecutable = "/Applications/SiftAlpha X.app/managed/python3",
            )
            assertEquals(RuntimeKind.PYTHON, plan.needs?.primaryRuntime)
            assertEquals(MacProjectPlanStatus.READY_TO_PREPARE, plan.status)
            assertTrue(plan.runtimeExecutables[RuntimeKind.PYTHON]!!.contains("python3"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unsafeRequirementIsRejectedBeforePip() {
        val root = Files.createTempDirectory("siftalpha-m42-unsafe-").toFile()
        try {
            root.resolve("main.py").writeText("print('ok')\n")
            root.resolve("requirements.txt").writeText("-e ../other\n")
            val fs = MacProjectFilesystem()
            val project = fs.importDirectory(root)
            val snapshot = MacProjectSnapshotBuilder(fs).build(project)
            val plan = MacProjectWorkflowPlanner.plan(
                snapshot,
                emptyList(),
                managedPythonExecutable = "/tmp/python3",
            )
            assertEquals(MacProjectPlanStatus.READY_TO_PREPARE, plan.status)
        } finally {
            root.deleteRecursively()
        }
    }
}
