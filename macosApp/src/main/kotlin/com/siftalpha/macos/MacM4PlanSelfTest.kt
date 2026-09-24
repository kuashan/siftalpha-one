package com.siftalpha.macos

import java.nio.file.Files

data class MacM4PlanSelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM4PlanSelfTest {
    fun run(): MacM4PlanSelfTestResult {
        val temp = Files.createTempDirectory("siftalpha-m4-plan-").toFile()
        return try {
            val python = temp.resolve("python-demo").apply { mkdirs() }
            python.resolve("main.py").writeText("print('m4-python')\n")
            python.resolve("requirements.txt").writeText("requests==2.32.5\n")

            val node = temp.resolve("node-demo").apply { mkdirs() }
            node.resolve("package.json").writeText(
                """{"name":"node-demo","scripts":{"start":"node index.js"}}""",
            )
            node.resolve("package-lock.json").writeText("""{"lockfileVersion":3}""")
            node.resolve("index.js").writeText("console.log('m4-node')\n")

            val fs = MacProjectFilesystem()
            val builder = MacProjectSnapshotBuilder(fs)
            val tools = MacHostRuntimeDiscovery().discoverAll()
            val pythonPlan = MacProjectWorkflowPlanner.plan(builder.build(fs.importDirectory(python)), tools)
            val nodePlan = MacProjectWorkflowPlanner.plan(builder.build(fs.importDirectory(node)), tools)

            val pythonPass = pythonPlan.needs?.primaryRuntime?.id == "python" &&
                pythonPlan.status == MacProjectPlanStatus.READY_TO_PREPARE
            val nodeAvailable = tools.any {
                it.kind == MacHostToolKind.NODE_JS && it.availability == MacHostToolAvailability.AVAILABLE
            }
            val expectedNode = if (nodeAvailable) {
                MacProjectPlanStatus.READY_TO_PREPARE
            } else {
                MacProjectPlanStatus.RUNTIME_MISSING
            }
            val nodePass = nodePlan.needs?.primaryRuntime?.id == "nodejs" &&
                nodePlan.status == expectedNode

            val lines = listOf(
                "python_import_detect_plan=" + pythonPlan.status,
                "node_import_detect_plan=" + nodePlan.status,
                "python_primary=" + pythonPlan.needs?.primaryRuntime?.id,
                "node_primary=" + nodePlan.needs?.primaryRuntime?.id,
            )
            MacM4PlanSelfTestResult(pythonPass && nodePass, lines)
        } finally {
            temp.deleteRecursively()
        }
    }
}
