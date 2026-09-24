package com.siftalpha.macos

import java.io.File
import java.nio.file.Files

data class MacM51NormalModeSelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM51NormalModeSelfTest {
    fun run(): MacM51NormalModeSelfTestResult {
        val managed = MacManagedPythonRuntime.locate()
            ?: return MacM51NormalModeSelfTestResult(false, listOf("managed_python=UNAVAILABLE"))
        val temp = Files.createTempDirectory("siftalpha-m51-").toFile()

        return try {
            val projectRoot = File(temp, "normal-ui-project").apply { mkdirs() }
            projectRoot.resolve("requirements.txt").writeText("idna==3.10\n")
            projectRoot.resolve("main.py").writeText(
                """
                import idna
                from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

                class Handler(BaseHTTPRequestHandler):
                    def do_GET(self):
                        body = ("m5-normal:" + idna.__version__).encode("utf-8")
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

            val controller = MacProductController(
                discovery = MacHostRuntimeDiscovery().discoverAll(),
                managedPython = managed,
                dataRoot = File(temp, "data"),
            )
            val project = controller.importProject(projectRoot)
            val initial = controller.view(project.projectId)
                ?.let(MacNormalProjectPresentationPolicy::resolve)
                ?: return MacM51NormalModeSelfTestResult(false, listOf("initial=NO_VIEW"))

            val prepare = controller.prepare(project.projectId)
            val afterPrepare = controller.view(project.projectId)
                ?.let(MacNormalProjectPresentationPolicy::resolve)
                ?: return MacM51NormalModeSelfTestResult(false, listOf("after_prepare=NO_VIEW"))

            val started = controller.start(project.projectId)
            val resultUrl = if (started) controller.waitForResult(project.projectId) else null
            val running = controller.view(project.projectId)
                ?.let(MacNormalProjectPresentationPolicy::resolve)
                ?: return MacM51NormalModeSelfTestResult(false, listOf("running=NO_VIEW"))

            val stopped = controller.stop(project.projectId)
            val afterStop = controller.view(project.projectId)
                ?.let(MacNormalProjectPresentationPolicy::resolve)
                ?: return MacM51NormalModeSelfTestResult(false, listOf("after_stop=NO_VIEW"))

            val passed =
                initial.primaryAction == MacNormalPrimaryAction.PREPARE &&
                prepare.success &&
                afterPrepare.primaryAction == MacNormalPrimaryAction.RUN &&
                started &&
                !resultUrl.isNullOrBlank() &&
                running.primaryAction == MacNormalPrimaryAction.OPEN_RESULT &&
                running.showSecondaryStop &&
                stopped &&
                afterStop.primaryAction == MacNormalPrimaryAction.RUN

            MacM51NormalModeSelfTestResult(
                passed = passed,
                lines = listOf(
                    "import=PASS",
                    "initial_action=" + initial.primaryAction,
                    "prepare=" + prepare.success,
                    "after_prepare_action=" + afterPrepare.primaryAction,
                    "run=" + started,
                    "result_url=" + resultUrl,
                    "running_action=" + running.primaryAction,
                    "running_secondary_stop=" + running.showSecondaryStop,
                    "stop=" + stopped,
                    "after_stop_action=" + afterStop.primaryAction,
                ),
            )
        } finally {
            temp.deleteRecursively()
        }
    }
}
