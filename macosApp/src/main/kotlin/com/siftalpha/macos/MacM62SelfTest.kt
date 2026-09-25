package com.siftalpha.macos

import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.platform.CapabilityAvailability
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class MacM62SelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM62SelfTest {
    private class FakeComposeProvider(
        private val webPort: Int,
    ) : MacComposeContainerProvider {
        override val snapshot = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.DOCKER,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/fake/docker",
            version = "Docker fake",
            composeAvailable = true,
            composeVersion = "Docker Compose fake",
        )

        private val running = ConcurrentHashMap<String, Boolean>()

        override fun prepare(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
            cancelled: () -> Boolean,
            log: (String) -> Unit,
        ): MacContainerOperationResult {
            if (cancelled()) {
                return MacContainerOperationResult(false, cancelled = true, detail = "cancelled")
            }
            log("FAKE_COMPOSE_PREPARE=" + projectName(project.projectId))
            return MacContainerOperationResult(true)
        }

        override fun start(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacContainerOperationResult {
            running[projectName(project.projectId)] = true
            return MacContainerOperationResult(true, output = "fake compose up")
        }

        override fun status(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacComposeRuntimeStatus {
            val name = projectName(project.projectId)
            val active = running[name] == true
            return MacComposeRuntimeStatus(
                projectName = name,
                services = plan.services.map { service ->
                    MacComposeServiceStatus(
                        service.name,
                        if (active) MacComposeServiceState.RUNNING else MacComposeServiceState.STOPPED,
                    )
                },
            )
        }

        override fun logs(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
            maxBytes: Int,
        ): String =
            if (running[projectName(project.projectId)] == true) {
                "web | ready\n" +
                    "db | ready\n"
            } else {
                ""
            }

        override fun stop(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacContainerOperationResult {
            running[projectName(project.projectId)] = false
            return MacContainerOperationResult(true, output = "fake compose down")
        }

        override fun clean(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacContainerOperationResult {
            runningClean(project)
            return MacContainerOperationResult(true, output = "fake compose clean")
        }

        private fun runningClean(project: MacImportedProject) {
            running[projectName(project.projectId)] = false
        }

        override fun publishedPorts(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): List<Int> =
            if (running[projectName(project.projectId)] == true) listOf(webPort) else emptyList()
    }

    fun run(): MacM62SelfTestResult {
        val temp = Files.createTempDirectory("siftalpha-m62-").toFile()
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val accepting = AtomicBoolean(true)
        val acceptThread = Thread {
            while (accepting.get()) {
                runCatching {
                    server.accept().use { }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        return try {
            val port = server.localPort
            fun project(name: String) = temp.resolve(name).apply {
                mkdirs()
                resolve("compose.yaml").writeText(
                    """
                    services:
                      web:
                        image: nginx:1.27
                        ports:
                          - "18080:80"
                        depends_on:
                          - db
                      db:
                        image: postgres:16
                    """.trimIndent() + "\n",
                )
            }

            val fake = FakeComposeProvider(port)
            val snapshotSource = { listOf(fake.snapshot) }
            val controller = MacProductController(
                discovery = emptyList(),
                managedPython = null,
                containerProviderSnapshotSource = snapshotSource,
                composeProviderFactory = { fake },
                systemFacts = MacSystemFacts(
                    osVersion = "13.7.8",
                    osMajor = 13,
                    architecture = "x86_64",
                    processorCount = 8,
                    physicalMemoryBytes = 16L * 1024L * 1024L * 1024L,
                    usableDiskBytes = 100L * 1024L * 1024L * 1024L,
                ),
                dataRoot = temp.resolve("data"),
            )

            val a = controller.importProject(project("project-a"))
            val b = controller.importProject(project("project-b"))

            val aInitial = MacNormalProjectPresentationPolicy.resolve(controller.view(a.projectId)!!)
            val adviceReady = controller.view(a.projectId)?.containerAdvice?.state ==
                MacContainerAdviceState.READY

            val prepareA = controller.prepare(a.projectId)
            val prepareB = controller.prepare(b.projectId)
            val aPrepared = MacNormalProjectPresentationPolicy.resolve(controller.view(a.projectId)!!)

            val startA = controller.start(a.projectId)
            val startB = controller.start(b.projectId)
            val aRunning = controller.view(a.projectId)!!
            val bRunning = controller.view(b.projectId)!!
            val resultUrl = controller.waitForResult(a.projectId, attempts = 20, delayMs = 25)
            val developerA = controller.developerView(a.projectId)!!

            val stopA = controller.stop(a.projectId)
            val aAfterStop = controller.view(a.projectId)!!
            val bAfterAStop = controller.view(b.projectId)!!
            val restartA = controller.restart(a.projectId)
            val aAfterRestart = controller.view(a.projectId)!!
            val finalStopA = controller.stop(a.projectId)
            val finalStopB = controller.stop(b.projectId)

            val aName = MacComposeProjectIdentity.forProject(a.projectId)
            val bName = MacComposeProjectIdentity.forProject(b.projectId)
            val passed =
                a.isCompose &&
                    b.isCompose &&
                    aName != bName &&
                    aInitial.primaryAction == MacNormalPrimaryAction.PREPARE &&
                    adviceReady &&
                    prepareA.success &&
                    prepareB.success &&
                    aPrepared.primaryAction == MacNormalPrimaryAction.RUN &&
                    startA &&
                    startB &&
                    aRunning.workflow.processState.name == "RUNNING" &&
                    bRunning.workflow.processState.name == "RUNNING" &&
                    resultUrl == "http://127.0.0.1:" + port &&
                    developerA.containerServices.size == 2 &&
                    developerA.containerServices.all { it.state == MacComposeServiceState.RUNNING } &&
                    developerA.webSource == "CONTAINER_PORT" &&
                    stopA &&
                    aAfterStop.workflow.processState.name == "STOPPED" &&
                    bAfterAStop.workflow.processState.name == "RUNNING" &&
                    restartA &&
                    aAfterRestart.workflow.processState.name == "RUNNING" &&
                    finalStopA &&
                    finalStopB

            MacM62SelfTestResult(
                passed = passed,
                lines = listOf(
                    "compose_detect_a=" + a.isCompose,
                    "compose_detect_b=" + b.isCompose,
                    "project_identity_distinct=" + (aName != bName),
                    "initial_action=" + aInitial.primaryAction,
                    "advisor_ready=" + adviceReady,
                    "prepare_a=" + prepareA.success,
                    "prepare_b=" + prepareB.success,
                    "prepared_action=" + aPrepared.primaryAction,
                    "start_a=" + startA,
                    "start_b=" + startB,
                    "a_running=" + aRunning.workflow.processState,
                    "b_running=" + bRunning.workflow.processState,
                    "web_url=" + resultUrl,
                    "service_states=" + developerA.containerServices.joinToString(",") {
                        it.name + ":" + it.state
                    },
                    "web_source=" + developerA.webSource,
                    "stop_a=" + stopA,
                    "a_after_stop=" + aAfterStop.workflow.processState,
                    "b_after_a_stop=" + bAfterAStop.workflow.processState,
                    "restart_a=" + restartA,
                    "a_after_restart=" + aAfterRestart.workflow.processState,
                    "final_stop_a=" + finalStopA,
                    "final_stop_b=" + finalStopB,
                ),
            )
        } finally {
            accepting.set(false)
            runCatching { server.close() }
            runCatching { acceptThread.join(500) }
            temp.deleteRecursively()
        }
    }
}
