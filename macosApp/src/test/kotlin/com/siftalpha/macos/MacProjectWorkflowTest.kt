package com.siftalpha.macos

import com.siftalpha.core.operation.ProjectOperationAction
import com.siftalpha.core.operation.ProjectOperationOwnership
import com.siftalpha.core.operation.ProjectOperationPhase
import com.siftalpha.core.storage.DurableProjectOperationRecord
import com.siftalpha.studio.runtime.RuntimeKind
import com.siftalpha.core.storage.StoredStateValue
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacProjectWorkflowTest {
    @Test
    fun recoveryPolicyOnlyRecoversStaleGenerationAndPreservesLiveOwnership() {
        val stale = DurableProjectOperationRecord(
            projectId = "macos:recovery",
            providerId = "container",
            action = ProjectOperationAction.PREPARE,
            phase = ProjectOperationPhase.ACTIVE,
            generation = 2L,
            startedAtEpochMs = 1000L,
        )
        val live = ProjectOperationOwnership(
            projectId = "macos:recovery",
            action = ProjectOperationAction.PREPARE,
            phase = ProjectOperationPhase.ACTIVE,
            generation = 3L,
        )
        val sameLive = stale.copy(generation = 3L)

        assertEquals(
            MacProjectRecoveryDecision.RECOVER_STALE,
            MacProjectRecoveryPolicy.decide(stale, null),
        )
        assertEquals(
            MacProjectRecoveryDecision.LIVE_OPERATION,
            MacProjectRecoveryPolicy.decide(sameLive, live),
        )
        assertEquals(
            MacProjectRecoveryDecision.NONE,
            MacProjectRecoveryPolicy.decide(stale.copy(phase = ProjectOperationPhase.SUCCESS), live),
        )
    }

    @Test
    fun fileStateStorageAndProjectCatalogSurviveNewInstances() {
        val root = Files.createTempDirectory("siftalpha-state-").toFile()
        try {
            val stateFile = root.resolve("state/platform-state.properties")
            val first = MacFileStateStorage(stateFile)
            first.mutate(
                com.siftalpha.core.storage.StateStorageMutation(
                    writes = mapOf("proof" to StoredStateValue.Text("persisted")),
                ),
            )
            val catalog = MacProjectCatalog(first)
            catalog.add("/tmp/Project A")
            catalog.add("/tmp/项目 B")

            val second = MacFileStateStorage(stateFile)
            assertEquals(StoredStateValue.Text("persisted"), second.read("proof"))
            assertEquals(listOf("/tmp/Project A", "/tmp/项目 B"), MacProjectCatalog(second).paths())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun productControllerRestoresImportedProjectsWithoutTouchingSource() {
        val root = Files.createTempDirectory("siftalpha-persist-").toFile()
        val project = root.resolve("demo").apply { mkdirs() }
        val dataRoot = root.resolve("data")
        try {
            project.resolve("main.py").writeText("print('ok')\n")
            val first = MacProductController(
                discovery = emptyList(),
                filesystem = MacProjectFilesystem(),
                managedPython = null,
                containerProviderSnapshotSource = { emptyList() },
                dataRoot = dataRoot,
                stateStorage = MacFileStateStorage(dataRoot.resolve("state/platform-state.properties")),
            )
            val imported = first.importProject(project)
            assertEquals(1, first.projects().size)

            val second = MacProductController(
                discovery = emptyList(),
                filesystem = MacProjectFilesystem(),
                managedPython = null,
                containerProviderSnapshotSource = { emptyList() },
                dataRoot = dataRoot,
                stateStorage = MacFileStateStorage(dataRoot.resolve("state/platform-state.properties")),
            )
            assertEquals(listOf(imported.projectId), second.projects().map { it.projectId })
            assertTrue(project.resolve("main.py").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun composePreparePolicySkipsPullForBuildableImageAndHandlesMixedProjects() {
        fun service(name: String, image: String?, build: String?) =
            com.siftalpha.studio.container.ComposeServicePlan(
                name = name,
                image = image,
                buildContext = build,
                dependsOn = emptyList(),
                ports = emptyList(),
            )
        fun plan(services: List<com.siftalpha.studio.container.ComposeServicePlan>) =
            com.siftalpha.studio.container.ComposeProjectPlan(
                status = com.siftalpha.studio.container.ComposeProjectPlanStatus.READY,
                manifestPath = "compose.yaml",
                services = services,
                containerAvailability = com.siftalpha.studio.platform.CapabilityAvailability.AVAILABLE,
                issues = emptyList(),
            )

        assertEquals(
            listOf(listOf("build")),
            MacComposePreparePolicy.operations(
                plan(listOf(service("easy-tdx", "easy-tdx:latest", "."))),
            ),
        )
        assertEquals(
            listOf(listOf("pull", "--ignore-buildable"), listOf("build")),
            MacComposePreparePolicy.operations(
                plan(
                    listOf(
                        service("redis", "redis:alpine", null),
                        service("easy-tdx", "easy-tdx:latest", "."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun clearingProjectEnvironmentDeletesOnlyManagedProjectData() {
        val root = Files.createTempDirectory("siftalpha-clean-").toFile()
        val source = root.resolve("source").apply { mkdirs() }
        val dataRoot = root.resolve("data")
        try {
            source.resolve("main.py").writeText("print('keep me')\n")
            val manager = MacProjectEnvironmentManager(
                processControl = MacProjectProcessControl(),
                managedPython = null,
                dataRoot = dataRoot,
            )
            val projectId = "macos:test-clean"
            val managedProjectRoot = dataRoot.resolve("projects/macos_test-clean")
            managedProjectRoot.resolve("environments/env-1").mkdirs()
            managedProjectRoot.resolve("environments/env-1/marker").writeText("generated")
            managedProjectRoot.resolve("compose-prepared.ready").writeText("ready")

            assertTrue(manager.clearProjectEnvironment(projectId))
            assertTrue(!managedProjectRoot.exists())
            assertTrue(source.resolve("main.py").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

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
