package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEnvironmentPlanTest {

    private val allCapabilities = ProjectEnvironmentCapabilities(
        embeddedCpythonAvailable = true,
        internalAlpineAvailable = true,
        externalProviderAvailable = true,
    )

    @Test
    fun purePythonPlanPrefersEmbeddedCpython() {
        val detection = detect(
            paths = listOf("pyproject.toml", "src/app.py"),
            pyproject = """
                [project]
                name = "demo"
                requires-python = ">=3.14"
                dependencies = ["requests>=2"]
            """.trimIndent(),
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertTrue(plan.readyToPrepare)
        assertEquals(RuntimeKind.PYTHON, detection.primaryRuntime)
        assertEquals(EnvironmentDependencySource.PYPROJECT_TOML, detection.dependencySource)
        assertEquals(1, detection.directDependencyCount)
        assertEquals(EnvironmentBackend.EMBEDDED_CPYTHON, plan.preferredBackend)
        assertTrue(plan.supports(EnvironmentBackend.INTERNAL_ALPINE))
        assertTrue(plan.supports(EnvironmentBackend.EXTERNAL_PROVIDER))
        assertTrue(EnvironmentBuildStep.PYTHON_INSTALL in plan.buildSteps)
    }

    @Test
    fun pythonWithViteUsesNodeBeforePythonAndExcludesEmbeddedCpython() {
        val detection = detect(
            paths = listOf(
                "pyproject.toml",
                "src/app.py",
                "web-ui/package.json",
                "web-ui/package-lock.json",
                "web-ui/vite.config.ts",
            ),
            pyproject = """
                [project]
                name = "demo"
                dependencies = ["fastapi"]
            """.trimIndent(),
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertTrue(plan.readyToPrepare)
        assertFalse(detection.embeddedCpythonEligible)
        assertEquals(EnvironmentBackend.INTERNAL_ALPINE, plan.preferredBackend)
        val nodeInstall = plan.buildSteps.indexOf(EnvironmentBuildStep.NODE_INSTALL)
        val nodeBuild = plan.buildSteps.indexOf(EnvironmentBuildStep.NODE_BUILD)
        val pythonInstall = plan.buildSteps.indexOf(EnvironmentBuildStep.PYTHON_INSTALL)
        assertTrue(nodeInstall in 0 until nodeBuild)
        assertTrue(nodeBuild in 0 until pythonInstall)
    }

    @Test
    fun malformedPyprojectBlocksPlan() {
        val detection = detect(
            paths = listOf("pyproject.toml", "main.py"),
            pyproject = "[project\nname = broken",
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertFalse(plan.readyToPrepare)
        assertTrue(
            plan.detection.blockingIssues.any {
                it.kind == EnvironmentIssueKind.PYPROJECT_INVALID
            },
        )
    }

    @Test
    fun missingDeclaredReadmeBlocksBeforePrepare() {
        val detection = detect(
            paths = listOf("pyproject.toml", "src/app.py"),
            pyproject = """
                [project]
                name = "demo"
                readme = "README.md"
            """.trimIndent(),
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertFalse(plan.readyToPrepare)
        assertTrue(
            plan.detection.blockingIssues.any {
                it.kind == EnvironmentIssueKind.PROJECT_SOURCE_INCOMPLETE &&
                    "README.md" in it.detail
            },
        )
    }

    @Test
    fun generatedHatchForceIncludeIsNotTreatedAsStaticMissingInput() {
        val detection = detect(
            paths = listOf(
                "pyproject.toml",
                "README.md",
                "web-ui/package.json",
                "web-ui/package-lock.json",
                "web-ui/vite.config.ts",
            ),
            pyproject = """
                [project]
                name = "demo"
                readme = "README.md"

                [tool.hatch.build.targets.wheel.force-include]
                "web-ui/dist" = "demo/web"
            """.trimIndent(),
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertTrue(plan.readyToPrepare)
        assertTrue(plan.detection.blockingIssues.isEmpty())
        assertEquals(1, detection.viteComponentCount)
    }

    @Test
    fun nestedPnpmViteComponentIsRejectedBeforePrepare() {
        val detection = detect(
            paths = listOf(
                "pyproject.toml",
                "web-ui/package.json",
                "web-ui/pnpm-lock.yaml",
                "web-ui/vite.config.ts",
            ),
            pyproject = """
                [project]
                name = "demo"
            """.trimIndent(),
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertFalse(plan.readyToPrepare)
        assertTrue(
            plan.detection.blockingIssues.any {
                it.kind == EnvironmentIssueKind.NODE_PACKAGE_MANAGER_UNSUPPORTED
            },
        )
    }

    @Test
    fun incompatiblePythonVersionFallsBackWithoutBlockingWholePlan() {
        val detection = detect(
            paths = listOf("pyproject.toml", "main.py"),
            pyproject = """
                [project]
                name = "demo"
                requires-python = "<3.14"
            """.trimIndent(),
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertTrue(plan.readyToPrepare)
        assertFalse(detection.embeddedCpythonEligible)
        assertEquals(EnvironmentBackend.INTERNAL_ALPINE, plan.preferredBackend)
        assertTrue(
            plan.detection.issues.any {
                it.kind == EnvironmentIssueKind.EMBEDDED_CPYTHON_INCOMPATIBLE && !it.blocking
            },
        )
    }

    @Test
    fun emptyRequirementsFallsThroughToPyproject() {
        val detection = ProjectEnvironmentDetector.detect(
            ProjectEnvironmentDetectionInput(
                relativePaths = listOf("requirements.txt", "pyproject.toml", "main.py"),
                requirementsText = "\n# intentionally empty\n",
                pyprojectText = """
                    [project]
                    name = "demo"
                    dependencies = ["httpx"]
                """.trimIndent(),
            ),
        )

        assertEquals(EnvironmentDependencySource.PYPROJECT_TOML, detection.dependencySource)
        assertEquals(1, detection.directDependencyCount)
    }

    @Test
    fun setupOnlyProjectIsNotPretendedReady() {
        val detection = detect(
            paths = listOf("setup.py", "main.py"),
            pyproject = null,
        )
        val plan = ProjectEnvironmentPlanner.plan(detection, allCapabilities)

        assertFalse(plan.readyToPrepare)
        assertTrue(
            plan.detection.blockingIssues.any {
                it.kind == EnvironmentIssueKind.PYTHON_DEPENDENCY_MANIFEST_UNSUPPORTED
            },
        )
    }

    @Test
    fun planIdentityIsDeterministicAndTracksSnapshotInputs() {
        val first = ProjectEnvironmentPlanner.plan(
            detect(
                paths = listOf("pyproject.toml", "main.py"),
                pyproject = "[project]\nname = \"demo\"\n",
            ),
            allCapabilities,
        )
        val second = ProjectEnvironmentPlanner.plan(
            detect(
                paths = listOf("pyproject.toml", "main.py"),
                pyproject = "[project]\nname = \"demo\"\n",
            ),
            allCapabilities,
        )
        val changed = ProjectEnvironmentPlanner.plan(
            detect(
                paths = listOf("pyproject.toml", "main.py", "README.md"),
                pyproject = "[project]\nname = \"demo\"\n",
            ),
            allCapabilities,
        )

        assertEquals(first.planId, second.planId)
        assertNotEquals(first.planId, changed.planId)
    }

    @Test
    fun diagnosticsExposeMachineReadableContract() {
        val plan = ProjectEnvironmentPlanner.plan(
            detect(
                paths = listOf("pyproject.toml", "main.py"),
                pyproject = "[project]\nname = \"demo\"\n",
            ),
            allCapabilities,
        )
        val lines = plan.diagnosticLines()

        assertTrue(lines.any { it.startsWith("SIFTALPHA_ENV_PLAN_ID=sha256:") })
        assertTrue(lines.contains("SIFTALPHA_ENV_PRIMARY_RUNTIME=python"))
        assertTrue(lines.contains("SIFTALPHA_ENV_PLAN_READY=1"))
    }

    private fun detect(
        paths: List<String>,
        pyproject: String?,
    ): ProjectEnvironmentDetection = ProjectEnvironmentDetector.detect(
        ProjectEnvironmentDetectionInput(
            relativePaths = paths,
            requirementsText = null,
            pyprojectText = pyproject,
        ),
    )
}
