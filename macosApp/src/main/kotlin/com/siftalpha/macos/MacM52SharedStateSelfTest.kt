package com.siftalpha.macos

import com.siftalpha.core.lifecycle.ProjectLifecycleState
import com.siftalpha.core.process.ProjectProcessState
import java.io.File
import java.nio.file.Files

data class MacM52SharedStateSelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM52SharedStateSelfTest {
    fun run(): MacM52SharedStateSelfTestResult {
        val managed = MacManagedPythonRuntime.locate()
            ?: return MacM52SharedStateSelfTestResult(false, listOf("managed_python=UNAVAILABLE"))
        val temp = Files.createTempDirectory("siftalpha-m52-").toFile()

        return try {
            val projectRootA = createWebProject(temp, "shared-state-a", "m52-a")
            val projectRootB = createWebProject(temp, "shared-state-b", "m52-b")
            val controller = MacProductController(
                discovery = MacHostRuntimeDiscovery().discoverAll(),
                managedPython = managed,
                dataRoot = File(temp, "data"),
            )

            val projectA = controller.importProject(projectRootA)
            val projectB = controller.importProject(projectRootB)
            val prepareA = controller.prepare(projectA.projectId)
            val prepareB = controller.prepare(projectB.projectId)
            val preparedDeveloperA = controller.developerView(projectA.projectId)
                ?: return MacM52SharedStateSelfTestResult(false, listOf("prepared_a=NO_VIEW"))
            val environmentGeneration = preparedDeveloperA.environment?.generation

            val startA = controller.start(projectA.projectId)
            val startB = controller.start(projectB.projectId)
            val resultA = if (startA) controller.waitForResult(projectA.projectId) else null
            val resultB = if (startB) controller.waitForResult(projectB.projectId) else null

            val normalViewA = controller.view(projectA.projectId)
                ?: return MacM52SharedStateSelfTestResult(false, listOf("normal_a=NO_VIEW"))
            val normalPresentationA = MacNormalProjectPresentationPolicy.resolve(normalViewA)
            val developerViewA = controller.developerView(projectA.projectId)
                ?: return MacM52SharedStateSelfTestResult(false, listOf("developer_a=NO_VIEW"))
            val pidsBeforePresentationSwitch = developerViewA.ownedPids

            controller.view(projectA.projectId)
            controller.developerView(projectA.projectId)
            controller.view(projectA.projectId)
            val afterPresentationSwitch = controller.developerView(projectA.projectId)
                ?: return MacM52SharedStateSelfTestResult(false, listOf("switch=NO_VIEW"))

            val sameEnvironment =
                environmentGeneration != null &&
                    developerViewA.environment?.generation == environmentGeneration
            val sameResult =
                !resultA.isNullOrBlank() &&
                    normalViewA.resultUrl == developerViewA.resultUrl &&
                    developerViewA.resultUrl == resultA
            val sameLogs =
                developerViewA.combinedLogs == controller.logs(projectA.projectId)
            val presentationSwitchStable =
                pidsBeforePresentationSwitch.isNotEmpty() &&
                    pidsBeforePresentationSwitch == afterPresentationSwitch.ownedPids &&
                    afterPresentationSwitch.workflow.lifecycle == ProjectLifecycleState.RUNNING

            val developerStopA = controller.stop(projectA.projectId)
            val normalAfterDeveloperStop = controller.view(projectA.projectId)
                ?.let(MacNormalProjectPresentationPolicy::resolve)
            val projectBAfterAStop = controller.developerView(projectB.projectId)

            val normalRunA = controller.start(projectA.projectId)
            val resultAfterNormalRun =
                if (normalRunA) controller.waitForResult(projectA.projectId) else null
            val developerAfterNormalRun = controller.developerView(projectA.projectId)

            val finalStopA = controller.stop(projectA.projectId)
            val finalStopB = controller.stop(projectB.projectId)

            val passed =
                prepareA.success &&
                    prepareB.success &&
                    startA &&
                    startB &&
                    !resultA.isNullOrBlank() &&
                    !resultB.isNullOrBlank() &&
                    normalPresentationA.primaryAction == MacNormalPrimaryAction.OPEN_RESULT &&
                    developerViewA.workflow.lifecycle == ProjectLifecycleState.RUNNING &&
                    developerViewA.workflow.processState == ProjectProcessState.RUNNING &&
                    developerViewA.runtimeVersion?.startsWith("Python 3.14.7") == true &&
                    developerViewA.entrypoint == "main.py" &&
                    sameEnvironment &&
                    sameResult &&
                    sameLogs &&
                    presentationSwitchStable &&
                    developerStopA &&
                    normalAfterDeveloperStop?.primaryAction == MacNormalPrimaryAction.RUN &&
                    projectBAfterAStop?.workflow?.processState == ProjectProcessState.RUNNING &&
                    normalRunA &&
                    !resultAfterNormalRun.isNullOrBlank() &&
                    developerAfterNormalRun?.workflow?.lifecycle == ProjectLifecycleState.RUNNING &&
                    developerAfterNormalRun.environment?.generation == environmentGeneration &&
                    developerAfterNormalRun.resultUrl == resultAfterNormalRun &&
                    finalStopA &&
                    finalStopB

            MacM52SharedStateSelfTestResult(
                passed = passed,
                lines = listOf(
                    "prepare_a=" + prepareA.success,
                    "prepare_b=" + prepareB.success,
                    "normal_running=" + normalViewA.workflow.lifecycle,
                    "developer_running=" + developerViewA.workflow.lifecycle,
                    "runtime_version=" + developerViewA.runtimeVersion,
                    "entrypoint=" + developerViewA.entrypoint,
                    "environment_generation_same=" + sameEnvironment,
                    "result_url_same=" + sameResult,
                    "raw_logs_same=" + sameLogs,
                    "presentation_switch_pid_stable=" + presentationSwitchStable,
                    "developer_stop=" + developerStopA,
                    "normal_after_developer_stop=" + normalAfterDeveloperStop?.primaryAction,
                    "project_b_after_a_stop=" + projectBAfterAStop?.workflow?.processState,
                    "normal_run=" + normalRunA,
                    "developer_after_normal_run=" + developerAfterNormalRun?.workflow?.lifecycle,
                    "environment_reused_after_normal_run=" +
                        (developerAfterNormalRun?.environment?.generation == environmentGeneration),
                    "final_stop_a=" + finalStopA,
                    "final_stop_b=" + finalStopB,
                ),
            )
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun createWebProject(parent: File, name: String, marker: String): File =
        File(parent, name).apply {
            mkdirs()
            resolve("main.py").writeText(
                """
                from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

                class Handler(BaseHTTPRequestHandler):
                    def do_GET(self):
                        body = b"$marker"
                        self.send_response(200)
                        self.send_header("Content-Type", "text/plain")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)

                    def log_message(self, format, *args):
                        pass

                server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
                print("SIFTALPHA_WEB_URL=http://127.0.0.1:%d" % server.server_port, flush=True)
                server.serve_forever()
                """.trimIndent() + "\n",
            )
        }
}
