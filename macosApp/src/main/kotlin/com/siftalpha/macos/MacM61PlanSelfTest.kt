package com.siftalpha.macos

import com.siftalpha.studio.container.ComposeProjectPlanStatus
import com.siftalpha.studio.container.ComposeProjectPlanner
import com.siftalpha.studio.platform.CapabilityAvailability
import com.siftalpha.studio.platform.StandardPlatformCapabilities
import java.nio.file.Files

data class MacM61PlanSelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM61PlanSelfTest {
    fun run(): MacM61PlanSelfTestResult {
        val temp = Files.createTempDirectory("siftalpha-m61-plan-").toFile()
        return try {
            val project = temp.resolve("compose-demo").apply { mkdirs() }
            project.resolve("compose.yaml").writeText(
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

            val availableProvider = MacContainerProviderSnapshot(
                kind = MacContainerProviderKind.DOCKER,
                availability = CapabilityAvailability.AVAILABLE,
                executablePath = "/usr/local/bin/docker",
                version = "Docker version test",
                composeAvailable = true,
                composeVersion = "Docker Compose version v2.39.0",
            )
            val controller = MacProductController(
                discovery = emptyList(),
                managedPython = null,
                containerProviderSnapshotSource = { listOf(availableProvider) },
                dataRoot = temp.resolve("data"),
            )
            val imported = controller.importProject(project)
            val compose = imported.composePlan
            val web = compose?.services?.singleOrNull { it.name == "web" }

            val unavailable = ComposeProjectPlanner.plan(
                imported.snapshot.relativePaths,
                imported.snapshot.composeText,
                CapabilityAvailability.UNAVAILABLE,
            )
            val unknown = ComposeProjectPlanner.plan(
                imported.snapshot.relativePaths,
                imported.snapshot.composeText,
                CapabilityAvailability.UNKNOWN,
            )

            val actualProviders = MacContainerRuntimeDiscovery().discoverAll()
            val actualAvailability = MacContainerRuntimeDiscovery.capabilityAvailability(actualProviders)
            val published = MacPlatformCapabilities
                .snapshot(actualAvailability)
                .availabilityOf(StandardPlatformCapabilities.CONTAINER_RUNTIME)

            val passed =
                imported.snapshot.composeFileName == "compose.yaml" &&
                    compose?.status == ComposeProjectPlanStatus.READY &&
                    compose?.services?.map { it.name } == listOf("db", "web") &&
                    web?.dependsOn == listOf("db") &&
                    web?.ports?.singleOrNull()?.published == 18080 &&
                    web?.ports?.singleOrNull()?.target == 80 &&
                    unavailable.status == ComposeProjectPlanStatus.CAPABILITY_UNAVAILABLE &&
                    unknown.status == ComposeProjectPlanStatus.CAPABILITY_UNKNOWN &&
                    published == actualAvailability

            MacM61PlanSelfTestResult(
                passed = passed,
                lines = listOf(
                    "compose_manifest=" + imported.snapshot.composeFileName,
                    "compose_services=" + compose?.services?.joinToString(",") { it.name },
                    "web_published_port=" + web?.ports?.singleOrNull()?.published,
                    "web_target_port=" + web?.ports?.singleOrNull()?.target,
                    "web_depends_on=" + web?.dependsOn?.joinToString(","),
                    "available_plan=" + compose?.status,
                    "unavailable_plan=" + unavailable.status,
                    "unknown_plan=" + unknown.status,
                    "host_container_capability=" + actualAvailability,
                    "published_container_capability=" + published,
                    "host_provider_facts=" + actualProviders.joinToString(",") {
                        it.kind.id + ":" + it.availability + ":compose=" + it.composeAvailable
                    },
                ),
            )
        } finally {
            temp.deleteRecursively()
        }
    }
}
