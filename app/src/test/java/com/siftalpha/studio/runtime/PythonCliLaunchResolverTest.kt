package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonCliLaunchResolverTest {

    @Test
    fun standardProjectScriptsProducesSingleConsoleEntry() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project.scripts]
                demo = "pkg.cli:main"
            """.trimIndent(),
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("demo")),
            result,
        )
    }

    @Test
    fun poetryScriptsSupportsSherlockStyleStringEntry() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [tool.poetry.scripts]
                sherlock = "sherlock_project.sherlock:main"
            """.trimIndent(),
            fallbackEntrypoint = null,
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("sherlock")),
            result,
        )
    }

    @Test
    fun standardProjectScriptsHavePriorityOverPoetryScripts() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project.scripts]
                standard = "pkg.cli:main"

                [tool.poetry.scripts]
                legacy = "pkg.legacy:main"
            """.trimIndent(),
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("standard")),
            result,
        )
    }

    @Test
    fun multipleConsoleEntriesAreSortedForExplicitUiSelection() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project.scripts]
                foo = "pkg.foo:main"
                admin = "pkg.admin:main"
                bar = "pkg.bar:main"
            """.trimIndent(),
            fallbackEntrypoint = null,
        ) as PythonCliLaunchResolver.Resolution.ConsoleScripts

        assertEquals(listOf("admin", "bar", "foo"), result.names)
    }

    @Test
    fun declaredRunWinsEvenWhenTomlIsInvalid() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = "python custom.py --compat",
            pyprojectToml = "[project.scripts",
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.DeclaredRun("python custom.py --compat"),
            result,
        )
    }

    @Test
    fun validTomlWithoutScriptsFallsBackToExistingPythonFilePolicy() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project]
                name = "demo"
            """.trimIndent(),
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.PythonFile("main.py"),
            result,
        )
    }

    @Test
    fun invalidTomlDoesNotGuessAnEntryPoint() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = "[project",
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.Invalid(
                PythonCliLaunchResolver.InvalidReason.TOML_PARSE_FAILED,
            ),
            result,
        )
    }

    @Test
    fun unsupportedPoetryStructuredScriptFailsClosed() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [tool.poetry.scripts.sherlock]
                reference = "sherlock_project.sherlock:main"
                type = "file"
            """.trimIndent(),
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.Invalid(
                PythonCliLaunchResolver.InvalidReason.SCRIPT_ENTRY_UNSUPPORTED,
            ),
            result,
        )
    }

    @Test
    fun unsafeConsoleNameIsRejected() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project.scripts]
                "../escape" = "pkg.cli:main"
            """.trimIndent(),
            fallbackEntrypoint = "main.py",
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.Invalid(
                PythonCliLaunchResolver.InvalidReason.SCRIPT_NAME_UNSUPPORTED,
            ),
            result,
        )
    }

    @Test
    fun missingEntryIsExplicit() {
        val result = PythonCliLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = null,
            fallbackEntrypoint = null,
        )

        assertTrue(result is PythonCliLaunchResolver.Resolution.Missing)
        assertFalse(result is PythonCliLaunchResolver.Resolution.ConsoleScripts)
    }
}
