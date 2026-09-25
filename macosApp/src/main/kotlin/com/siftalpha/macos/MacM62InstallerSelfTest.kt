package com.siftalpha.macos

import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.platform.CapabilityAvailability
import java.nio.file.Files

data class MacM62InstallerSelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM62InstallerSelfTest {
    private class FakeInstaller(
        private val onInstalled: () -> Unit,
    ) : MacContainerEnvironmentInstaller {
        var calls: Int = 0
            private set

        override fun install(
            plan: MacContainerInstallPlan,
            progress: (MacContainerInstallPhase, String) -> Unit,
            log: (String) -> Unit,
        ): MacContainerInstallResult {
            calls += 1
            progress(MacContainerInstallPhase.DOWNLOADING, "fake download")
            log("FAKE_INSTALL_PLAN=" + plan.id)
            progress(MacContainerInstallPhase.INSTALLING, "fake install")
            onInstalled()
            progress(MacContainerInstallPhase.VERIFYING, "fake verify")
            return MacContainerInstallResult(true)
        }
    }

    private class FakeComposeProvider(
        override val snapshot: MacContainerProviderSnapshot,
    ) : MacComposeContainerProvider {
        override fun prepare(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
            cancelled: () -> Boolean,
            log: (String) -> Unit,
        ): MacContainerOperationResult {
            if (cancelled()) return MacContainerOperationResult(false, cancelled = true)
            log("FAKE_MANAGED_PREPARE=PASS")
            return MacContainerOperationResult(true)
        }

        override fun start(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacContainerOperationResult = MacContainerOperationResult(true)

        override fun status(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacComposeRuntimeStatus =
            MacComposeRuntimeStatus(
                projectName = projectName(project.projectId),
                services = plan.services.map {
                    MacComposeServiceStatus(it.name, MacComposeServiceState.STOPPED)
                },
            )

        override fun logs(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
            maxBytes: Int,
        ): String = ""

        override fun stop(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacContainerOperationResult = MacContainerOperationResult(true)

        override fun clean(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): MacContainerOperationResult {
            runningClean(project)
            return MacContainerOperationResult(true, output = "fake compose clean")
        }

        private fun runningClean(project: MacImportedProject) {
            // no runtime state in installer self-test
        }

        override fun publishedPorts(
            project: MacImportedProject,
            plan: ComposeProjectPlan,
        ): List<Int> = emptyList()
    }

    fun run(): MacM62InstallerSelfTestResult {
        val temp = Files.createTempDirectory("siftalpha-m62-installer-").toFile()
        return try {
            val projectRoot = temp.resolve("compose-install-demo").apply { mkdirs() }
            projectRoot.resolve("compose.yaml").writeText(
                """
                services:
                  web:
                    image: nginx:alpine
                    ports:
                      - "18080:80"
                  redis:
                    image: redis:alpine
                """.trimIndent() + "\n",
            )

            var providers = listOf(
                MacContainerProviderSnapshot(
                    kind = MacContainerProviderKind.DOCKER,
                    availability = CapabilityAvailability.UNAVAILABLE,
                ),
                MacContainerProviderSnapshot(
                    kind = MacContainerProviderKind.PODMAN,
                    availability = CapabilityAvailability.UNAVAILABLE,
                ),
            )
            val readyDocker = MacContainerProviderSnapshot(
                kind = MacContainerProviderKind.DOCKER,
                availability = CapabilityAvailability.AVAILABLE,
                executablePath = "/fake/managed/docker",
                version = "Docker fake",
                composeAvailable = true,
                composeVersion = "Docker Compose fake",
            )
            val fakeInstaller = FakeInstaller {
                providers = listOf(
                    readyDocker,
                    MacContainerProviderSnapshot(
                        kind = MacContainerProviderKind.PODMAN,
                        availability = CapabilityAvailability.UNAVAILABLE,
                    ),
                )
            }
            val fakeProvider = FakeComposeProvider(readyDocker)
            val controller = MacProductController(
                discovery = emptyList(),
                managedPython = null,
                containerProviderSnapshotSource = { providers },
                composeProviderFactory = { snapshots ->
                    if (snapshots.any {
                            it.kind == MacContainerProviderKind.DOCKER &&
                                it.availability == CapabilityAvailability.AVAILABLE &&
                                it.composeAvailable
                        }
                    ) {
                        fakeProvider
                    } else {
                        null
                    }
                },
                systemFacts = MacSystemFacts(
                    osVersion = "15.0",
                    osMajor = 15,
                    architecture = "arm64",
                    processorCount = 8,
                    physicalMemoryBytes = 16L * 1024L * 1024L * 1024L,
                    usableDiskBytes = 100L * 1024L * 1024L * 1024L,
                ),
                containerInstaller = fakeInstaller,
                dataRoot = temp.resolve("data"),
            )

            val project = controller.importProject(projectRoot)
            val before = controller.view(project.projectId)!!
            val beforePresentation = MacNormalProjectPresentationPolicy.resolve(before)

            val prepared = controller.prepare(project.projectId)
            val after = controller.view(project.projectId)!!
            val afterPresentation = MacNormalProjectPresentationPolicy.resolve(after)
            val developerAfter = controller.developerView(project.projectId)!!

            val passed =
                project.isCompose &&
                    before.containerAdvice?.state != MacContainerAdviceState.READY &&
                    before.containerInstallPlan?.kind == MacContainerInstallPlanKind.INSTALL_MANAGED_DOCKER &&
                    beforePresentation.primaryAction == MacNormalPrimaryAction.PREPARE &&
                    beforePresentation.primaryLabel == "准备环境" &&
                    beforePresentation.primaryEnabled &&
                    fakeInstaller.calls == 1 &&
                    prepared.success &&
                    after.containerAdvice?.state == MacContainerAdviceState.READY &&
                    after.workflow.environmentReady &&
                    after.containerInstallProgress?.phase == MacContainerInstallPhase.COMPLETE &&
                    afterPresentation.primaryAction == MacNormalPrimaryAction.RUN &&
                    developerAfter.containerInstallProgress?.phase == MacContainerInstallPhase.COMPLETE

            MacM62InstallerSelfTestResult(
                passed = passed,
                lines = listOf(
                    "compose_detect=" + project.isCompose,
                    "before_advice=" + before.containerAdvice?.state,
                    "before_install_plan=" + before.containerInstallPlan?.kind,
                    "before_primary=" + beforePresentation.primaryAction,
                    "before_primary_is_prepare=" +
                        (beforePresentation.primaryAction == MacNormalPrimaryAction.PREPARE),
                    "installer_calls=" + fakeInstaller.calls,
                    "prepare_and_provision=" + prepared.success,
                    "after_advice=" + after.containerAdvice?.state,
                    "after_environment_ready=" + after.workflow.environmentReady,
                    "after_install_phase=" + after.containerInstallProgress?.phase,
                    "developer_install_phase=" + developerAfter.containerInstallProgress?.phase,
                    "after_primary=" + afterPresentation.primaryAction,
                ),
            )
        } finally {
            temp.deleteRecursively()
        }
    }
}
