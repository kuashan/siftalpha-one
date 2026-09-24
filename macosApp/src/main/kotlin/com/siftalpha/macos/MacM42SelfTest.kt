package com.siftalpha.macos

import java.io.File
import java.nio.file.Files

data class MacM42SelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM42SelfTest {
    fun run(): MacM42SelfTestResult {
        val managed = MacManagedPythonRuntime.locate()
            ?: return MacM42SelfTestResult(false, listOf("managed_python=UNAVAILABLE"))
        val temp = Files.createTempDirectory("siftalpha-m42-").toFile()
        val dataRoot = File(temp, "data")
        System.setProperty("siftalpha.data.root", dataRoot.absolutePath)

        return try {
            val projectRoot = File(temp, "project").apply { mkdirs() }
            projectRoot.resolve("requirements.txt").writeText("idna==3.10\n")
            projectRoot.resolve("main.py").writeText(
                """
                import idna
                from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

                class Handler(BaseHTTPRequestHandler):
                    def do_GET(self):
                        body = ("siftalpha-m42:" + idna.__version__).encode("utf-8")
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

            val fs = MacProjectFilesystem()
            val project = fs.importDirectory(projectRoot)
            val snapshot = MacProjectSnapshotBuilder(fs).build(project)
            val plan = MacProjectWorkflowPlanner.plan(
                snapshot = snapshot,
                hostTools = MacHostRuntimeDiscovery().discoverAll(),
                managedPythonExecutable = managed.pythonExecutable.absolutePath,
            )
            if (plan.status != MacProjectPlanStatus.READY_TO_PREPARE) {
                return MacM42SelfTestResult(
                    false,
                    listOf("plan_status=" + plan.status, "issues=" + plan.issues.joinToString(";")),
                )
            }

            val processControl = MacProjectProcessControl()
            val environmentManager = MacProjectEnvironmentManager(
                processControl = processControl,
                managedPython = managed,
                dataRoot = dataRoot,
            )
            val coordinator = MacProjectWorkflowCoordinator(processControl, environmentManager)
            coordinator.attach(MacWorkflowContext(project, snapshot, plan))

            val prepare = coordinator.prepare(project.projectId)
            if (!prepare.success) {
                return MacM42SelfTestResult(
                    false,
                    listOf("prepare=FAIL", "detail=" + prepare.detail, coordinator.logs(project.projectId)),
                )
            }

            if (!coordinator.start(project.projectId)) {
                return MacM42SelfTestResult(false, listOf("start=FAIL", coordinator.logs(project.projectId)))
            }

            val endpoint = waitForEndpoint(coordinator, project.projectId)
                ?: return MacM42SelfTestResult(false, listOf("web=FAIL", coordinator.logs(project.projectId)))

            val stopped = coordinator.stop(project.projectId)
            val stoppedState = coordinator.status(project.projectId).processState
            val restarted = coordinator.restart(project.projectId)
            val endpointAfterRestart = if (restarted) {
                waitForEndpoint(coordinator, project.projectId)
            } else {
                null
            }
            val finalStop = coordinator.stop(project.projectId)

            val passed = prepare.success &&
                endpoint.url.startsWith("http://127.0.0.1:") &&
                stopped &&
                stoppedState.name == "STOPPED" &&
                restarted &&
                endpointAfterRestart != null &&
                finalStop

            MacM42SelfTestResult(
                passed = passed,
                lines = listOf(
                    "managed_python=" + managed.version(),
                    "prepare=PASS",
                    "run=PASS",
                    "web=" + endpoint.url,
                    "web_source=" + endpoint.source,
                    "stop=" + stopped,
                    "stopped_state=" + stoppedState,
                    "restart=" + restarted,
                    "web_after_restart=" + endpointAfterRestart?.url,
                    "final_stop=" + finalStop,
                ),
            )
        } finally {
            temp.deleteRecursively()
            System.clearProperty("siftalpha.data.root")
        }
    }

    private fun waitForEndpoint(
        coordinator: MacProjectWorkflowCoordinator,
        projectId: String,
    ): MacProjectWebEndpoint? {
        repeat(100) {
            coordinator.webEndpoint(projectId)?.let { return it }
            val state = coordinator.status(projectId).processState
            if (state.name.startsWith("EXITED")) return null
            Thread.sleep(100)
        }
        return null
    }
}
