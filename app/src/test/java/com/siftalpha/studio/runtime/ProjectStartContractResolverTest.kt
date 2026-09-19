package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStartContractResolverTest {

    @Test
    fun declaredProjectRunRemainsAuthoritative() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = "python custom.py",
            renderYaml = "services:\n  - type: web\n    startCommand: gunicorn app:app",
            procfile = "web: flask run",
            packageJson = """{"scripts":{"start":"node server.js"}}""",
            pyprojectToml = null,
            fallbackEntrypoint = "app.py",
        )!!

        assertEquals(ProjectStartContractResolver.Source.DECLARED_PROJECT_RUN, result.source)
        assertEquals("python custom.py", result.command)
    }

    @Test
    fun directDeclaredPythonRunPreservesNonShellEmbeddedPath() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = "python app.py",
            renderYaml = null,
            procfile = null,
            packageJson = null,
            pyprojectToml = null,
            fallbackEntrypoint = "app.py",
        )!!

        assertEquals(ProjectStartContractResolver.Source.DECLARED_PROJECT_RUN, result.source)
        assertFalse(result.requiresShell)
    }

    @Test
    fun renderWebServiceStartCommandIsDetectedBeforeFallbacks() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = null,
            renderYaml = """
                services:
                  - type: web
                    name: situation-monitor
                    startCommand: gunicorn app:app --bind 0.0.0.0:$PORT --workers 2
            """.trimIndent(),
            procfile = "web: python app.py",
            packageJson = null,
            pyprojectToml = null,
            fallbackEntrypoint = "app.py",
        )!!

        assertEquals(ProjectStartContractResolver.Source.RENDER_YAML, result.source)
        assertEquals("gunicorn app:app --bind 0.0.0.0:$PORT --workers 2", result.command)
        assertTrue(result.requiresPortEnvironment)
        assertNull(result.explicitPort)
    }

    @Test
    fun procfileWebCommandIsDetected() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = null,
            renderYaml = null,
            procfile = "worker: python worker.py\nweb: gunicorn app:app --bind 127.0.0.1:8123",
            packageJson = null,
            pyprojectToml = null,
            fallbackEntrypoint = "app.py",
        )!!

        assertEquals(ProjectStartContractResolver.Source.PROCFILE, result.source)
        assertEquals(8123, result.explicitPort)
    }

    @Test
    fun packageStartUsesNpmLifecycleButInspectsActualScriptPort() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = null,
            renderYaml = null,
            procfile = null,
            packageJson = """{"scripts":{"start":"node server.js --port 3007"}}""",
            pyprojectToml = null,
            fallbackEntrypoint = null,
        )!!

        assertEquals(ProjectStartContractResolver.Source.PACKAGE_START_SCRIPT, result.source)
        assertEquals("npm start", result.command)
        assertEquals(3007, result.explicitPort)
    }

    @Test
    fun singlePyprojectConsoleScriptIsAStartContract() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = null,
            renderYaml = null,
            procfile = null,
            packageJson = null,
            pyprojectToml = """
                [project]
                name = "demo"
                version = "1.0"
                [project.scripts]
                dashboard = "demo.app:main"
            """.trimIndent(),
            fallbackEntrypoint = "app.py",
        )!!

        assertEquals(ProjectStartContractResolver.Source.PYPROJECT_SCRIPT, result.source)
        assertEquals("dashboard", result.command)
        assertTrue(result.requiresShell)
    }

    @Test
    fun multiplePyprojectScriptsDoNotGuessWhichOneToRun() {
        assertNull(
            ProjectStartContractResolver.pyprojectSingleScript(
                """
                [project.scripts]
                one = "demo:one"
                two = "demo:two"
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun entrypointFallbackDoesNotForceShellRuntime() {
        val result = ProjectStartContractResolver.resolve(
            declaredRun = null,
            renderYaml = null,
            procfile = null,
            packageJson = null,
            pyprojectToml = null,
            fallbackEntrypoint = "app.py",
        )!!

        assertEquals(ProjectStartContractResolver.Source.ENTRYPOINT_FALLBACK, result.source)
        assertFalse(result.requiresShell)
    }
}
