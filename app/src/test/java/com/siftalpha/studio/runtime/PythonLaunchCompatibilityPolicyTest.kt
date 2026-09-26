package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.ConfigurationEvidence
import com.siftalpha.studio.project.PythonCliArgumentKind
import com.siftalpha.studio.project.PythonCliRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonLaunchCompatibilityPolicyTest {

    @Test
    fun `existing summary entry wins when it still exists in imported project`() {
        val result = PythonLaunchCompatibilityPolicy.fallbackEntrypoint(
            summaryEntry = "foo.py",
            relativePaths = listOf("foo.py", "bar.py"),
            strictFallback = null,
        )

        assertEquals("foo.py", result)
    }

    @Test
    fun `stale summary entry does not become structured invocation`() {
        val result = PythonLaunchCompatibilityPolicy.fallbackEntrypoint(
            summaryEntry = "main.py",
            relativePaths = listOf("src/main.py"),
            strictFallback = null,
        )

        assertEquals(null, result)
    }

    @Test
    fun `entrypoint bound cli requirements override unrelated console script`() {
        val result = PythonLaunchCompatibilityPolicy.bindCliRequirementsToEntrypoint(
            resolution = PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("easy-tdx")),
            fallbackEntrypoint = "run_all_strategies.py",
            requirements = listOf(
                PythonCliRequirement(
                    name = "market",
                    token = "MARKET",
                    kind = PythonCliArgumentKind.POSITIONAL,
                    required = true,
                    evidence = ConfigurationEvidence(
                        filePath = "run_all_strategies.py",
                        lineNumber = 10,
                        detail = "Click required positional argument",
                    ),
                ),
                PythonCliRequirement(
                    name = "code",
                    token = "CODE",
                    kind = PythonCliArgumentKind.POSITIONAL,
                    required = true,
                    evidence = ConfigurationEvidence(
                        filePath = "run_all_strategies.py",
                        lineNumber = 11,
                        detail = "Click required positional argument",
                    ),
                ),
            ),
        )

        assertEquals(
            PythonCliLaunchResolver.Resolution.PythonFile("run_all_strategies.py"),
            result,
        )
    }

    @Test
    fun `cli requirements from another file do not override console script`() {
        val console = PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("tool"))
        val result = PythonLaunchCompatibilityPolicy.bindCliRequirementsToEntrypoint(
            resolution = console,
            fallbackEntrypoint = "main.py",
            requirements = listOf(
                PythonCliRequirement(
                    name = "market",
                    token = "MARKET",
                    kind = PythonCliArgumentKind.POSITIONAL,
                    required = true,
                    evidence = ConfigurationEvidence(filePath = "commands.py"),
                ),
            ),
        )

        assertEquals(console, result)
    }

    @Test
    fun `runtime only cli hints never guess a new entrypoint`() {
        val console = PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("tool"))
        val result = PythonLaunchCompatibilityPolicy.bindCliRequirementsToEntrypoint(
            resolution = console,
            fallbackEntrypoint = "main.py",
            requirements = listOf(
                PythonCliRequirement(
                    name = "market",
                    token = "MARKET",
                    kind = PythonCliArgumentKind.POSITIONAL,
                    required = true,
                    evidence = ConfigurationEvidence(detail = "Runtime reported missing CLI argument"),
                ),
            ),
        )

        assertEquals(console, result)
    }

    @Test
    fun `legacy run is preserved only when structured resolution is missing`() {
        val missing = PythonLaunchCompatibilityPolicy.preserveLegacyRun(
            resolution = PythonCliLaunchResolver.Resolution.Missing,
            legacyRun = "python main.py",
        )
        assertEquals(
            PythonCliLaunchResolver.Resolution.DeclaredRun("python main.py"),
            missing,
        )

        val invalid = PythonCliLaunchResolver.Resolution.Invalid(
            PythonCliLaunchResolver.InvalidReason.TOML_PARSE_FAILED,
        )
        assertEquals(
            invalid,
            PythonLaunchCompatibilityPolicy.preserveLegacyRun(
                resolution = invalid,
                legacyRun = "python main.py",
            ),
        )
    }

    @Test
    fun `native Web launch synthesis is allowed only when project launch is missing`() {
        assertTrue(
            PythonLaunchCompatibilityPolicy.allowsNativeWebFallback(
                PythonCliLaunchResolver.Resolution.Missing,
            ),
        )
        assertFalse(
            PythonLaunchCompatibilityPolicy.allowsNativeWebFallback(
                PythonCliLaunchResolver.Resolution.PythonFile("run_all_strategies.py"),
            ),
        )
        assertFalse(
            PythonLaunchCompatibilityPolicy.allowsNativeWebFallback(
                PythonCliLaunchResolver.Resolution.ConsoleScripts(listOf("easy-tdx")),
            ),
        )
        assertFalse(
            PythonLaunchCompatibilityPolicy.allowsNativeWebFallback(
                PythonCliLaunchResolver.Resolution.DeclaredRun("python run_all_strategies.py"),
            ),
        )
        assertFalse(
            PythonLaunchCompatibilityPolicy.allowsNativeWebFallback(
                PythonCliLaunchResolver.Resolution.Invalid(
                    PythonCliLaunchResolver.InvalidReason.TOML_PARSE_FAILED,
                ),
            ),
        )
    }

}
