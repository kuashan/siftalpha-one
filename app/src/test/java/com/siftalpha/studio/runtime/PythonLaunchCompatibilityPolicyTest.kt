package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
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
}
