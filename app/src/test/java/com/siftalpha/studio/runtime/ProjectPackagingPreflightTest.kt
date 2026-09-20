package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPackagingPreflightTest {

    @Test
    fun validPyprojectReadmeAndLicenseInputsPass() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml", "README.md", "LICENSE"),
            pyprojectToml = """
                [project]
                name = "sample"
                version = "1.0.0"
                readme = "README.md"
                license = { file = "LICENSE" }
            """.trimIndent(),
            requirementsText = null,
        )

        assertTrue(result.ready)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun missingDeclaredReadmeFailsBeforePackaging() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml", "src/sample/__init__.py"),
            pyprojectToml = """
                [project]
                name = "sample"
                version = "1.0.0"
                readme = "README.md"
            """.trimIndent(),
            requirementsText = null,
        )

        assertFalse(result.ready)
        assertEquals(
            ProjectPackagingPreflight.FailureKind.PROJECT_PACKAGING_INPUT_MISSING,
            result.failures.single().kind,
        )
        assertEquals("README.md", result.failures.single().reference)
    }

    @Test
    fun inlineReadmeFileTableIsValidated() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml"),
            pyprojectToml = """
                [project]
                name = "sample"
                version = "1.0.0"
                readme = { file = "docs/README.rst", content-type = "text/x-rst" }
            """.trimIndent(),
            requirementsText = null,
        )

        assertFalse(result.ready)
        assertEquals("docs/README.rst", result.failures.single().reference)
    }

    @Test
    fun malformedPyprojectIsReportedWithoutRunningBuildBackend() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml"),
            pyprojectToml = "[project\nname = 'broken'",
            requirementsText = null,
        )

        assertFalse(result.ready)
        assertEquals(
            ProjectPackagingPreflight.FailureKind.PYPROJECT_TOML_INVALID,
            result.failures.single().kind,
        )
    }

    @Test
    fun requirementsNestedLocalFilesAreValidated() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("requirements.txt", "constraints/base.txt"),
            pyprojectToml = null,
            requirementsText = """
                -r requirements-extra.txt
                --constraint constraints/base.txt
                requests>=2
            """.trimIndent(),
        )

        assertFalse(result.ready)
        assertEquals(1, result.failures.size)
        assertEquals("requirements-extra.txt", result.failures.single().reference)
    }

    @Test
    fun networkNestedRequirementsAreNotMistakenForMissingSnapshotFiles() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("requirements.txt"),
            pyprojectToml = null,
            requirementsText = "-r https://example.invalid/requirements.txt",
        )

        assertTrue(result.ready)
    }

    @Test
    fun pathEscapeInPackagingReferenceFailsClosed() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml"),
            pyprojectToml = """
                [project]
                name = "sample"
                version = "1.0.0"
                readme = "../README.md"
            """.trimIndent(),
            requirementsText = null,
        )

        assertFalse(result.ready)
        assertEquals(
            ProjectPackagingPreflight.FailureKind.PROJECT_PACKAGING_REFERENCE_UNSAFE,
            result.failures.single().kind,
        )
    }

    @Test
    fun generatedHatchForceIncludeTargetIsDeliberatelyNotAPreflightHardFailure() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml", "web-ui/package.json", "web-ui/vite.config.ts"),
            pyprojectToml = """
                [project]
                name = "sample"
                version = "1.0.0"

                [tool.hatch.build.targets.wheel.force-include]
                "web-ui/dist" = "sample/web"
            """.trimIndent(),
            requirementsText = null,
        )

        assertTrue(result.ready)
    }

    @Test
    fun diagnosticsExposeStablePrepareFailureMarkers() {
        val result = ProjectPackagingPreflight.inspect(
            relativePaths = listOf("pyproject.toml"),
            pyprojectToml = """
                [project]
                name = "sample"
                version = "1.0.0"
                readme = "README.md"
            """.trimIndent(),
            requirementsText = null,
        )
        val lines = ProjectPackagingPreflight.diagnosticLines(result)

        assertTrue(lines.contains("SIFTALPHA_ERROR=PROJECT_PACKAGING_PREFLIGHT_FAILED"))
        assertTrue(lines.contains("SIFTALPHA_ENV=NOT_READY"))
        assertTrue(lines.any { it == "SIFTALPHA_DIAG=PROJECT_PACKAGING_INPUT_MISSING" })
        assertTrue(lines.any { it.contains("README.md") })
    }
}
