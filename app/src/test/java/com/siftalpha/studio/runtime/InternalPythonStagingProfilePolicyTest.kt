package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class InternalPythonStagingProfilePolicyTest {

    @Test
    fun `vite build requires full project staging`() {
        assertEquals(
            InternalPythonStagingProfile.FULL_PROJECT,
            InternalPythonStagingProfilePolicy.select(
                requiresNodeVite = true,
                launchInvocation = null,
                pyprojectToml = null,
            ),
        )
    }

    @Test
    fun `explicit console script invocation requires full project staging`() {
        assertEquals(
            InternalPythonStagingProfile.FULL_PROJECT,
            InternalPythonStagingProfilePolicy.select(
                requiresNodeVite = false,
                launchInvocation = PythonLaunchInvocation.consoleScript("demo", listOf("serve")),
                pyprojectToml = null,
            ),
        )
    }

    @Test
    fun `project owned console script requires full staging before invocation is supplied`() {
        assertEquals(
            InternalPythonStagingProfile.FULL_PROJECT,
            InternalPythonStagingProfilePolicy.select(
                requiresNodeVite = false,
                launchInvocation = null,
                pyprojectToml = """
                    [project]
                    name = "demo"

                    [project.scripts]
                    demo = "demo.cli:main"
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `simple Python file retains entrypoint staging`() {
        assertEquals(
            InternalPythonStagingProfile.ENTRYPOINT,
            InternalPythonStagingProfilePolicy.select(
                requiresNodeVite = false,
                launchInvocation = PythonLaunchInvocation.pythonFile("main.py"),
                pyprojectToml = """
                    [project]
                    name = "demo"
                    dependencies = ["requests"]
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `invalid or unsupported script contract does not widen staging`() {
        assertEquals(
            InternalPythonStagingProfile.ENTRYPOINT,
            InternalPythonStagingProfilePolicy.select(
                requiresNodeVite = false,
                launchInvocation = null,
                pyprojectToml = """
                    [project]
                    name = "demo"

                    [project.scripts]
                    demo = { reference = "demo.py", type = "file" }
                """.trimIndent(),
            ),
        )
    }
}
