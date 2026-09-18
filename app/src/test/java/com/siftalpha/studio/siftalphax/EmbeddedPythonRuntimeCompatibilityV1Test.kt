package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonRuntimeCompatibilityV1Test {
    private val context = EmbeddedPythonRuntimeCompatibilityContextV1()

    @Test
    fun requiresPythonMatchesCpython3147() {
        assertMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython(">=3.6,<4", context))
        assertMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython(">=3.9,!=3.9.0", context))
        assertMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython("==3.14.*", context))
        assertMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython("~=3.14.0", context))

        assertNoMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython("<3.14", context))
        assertNoMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython("!=3.14.*", context))
        assertNoMatch(EmbeddedPythonRuntimeCompatibilityV1.requiresPython("~=3.13.0", context))
    }

    @Test
    fun rejectsUnsupportedRequiresPythonSyntaxDeterministically() {
        val result = EmbeddedPythonRuntimeCompatibilityV1.requiresPython(">=3.9 || <4", context)
        assertEquals(EmbeddedPythonCompatibilityStateV1.UNSUPPORTED, result.state)
    }

    @Test
    fun evaluatesPythonAndAndroidMarkers() {
        assertMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "python_version >= '3.8' and sys_platform == 'android'",
                context,
            ),
        )
        assertMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "python_full_version != '3.9.0' and platform_machine == 'aarch64'",
                context,
            ),
        )
        assertNoMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "python_version <= '3.7'",
                context,
            ),
        )
    }

    @Test
    fun honorsBooleanPrecedenceAndParentheses() {
        assertMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "os_name == 'nt' or (os_name == 'posix' and sys_platform == 'android')",
                context,
            ),
        )
        assertNoMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "(os_name == 'nt' or os_name == 'java') and sys_platform == 'android'",
                context,
            ),
        )
    }

    @Test
    fun supportsMarkerMembershipOperators() {
        assertMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "sys_platform in 'linux android ios'",
                context,
            ),
        )
        assertMatch(
            EmbeddedPythonRuntimeCompatibilityV1.marker(
                "sys_platform not in 'linux win32'",
                context,
            ),
        )
    }

    @Test
    fun unknownMarkerVariableIsUnsupportedNotFalse() {
        val result = EmbeddedPythonRuntimeCompatibilityV1.marker(
            "extra == 'adk'",
            context,
        )
        assertEquals(EmbeddedPythonCompatibilityStateV1.UNSUPPORTED, result.state)
        assertFalse(result.matches)
    }

    @Test
    fun currentOciPythonMarkersSelectThe314DependencyBranches() {
        val matching = listOf(
            "python_version >= '3.8'",
            "python_version >= '3.7'",
            "python_version >= '3.10.0'",
            "python_version >= '3.9'",
        )
        val excluded = listOf(
            "python_version <= '3.7'",
            "python_version <= '3.6'",
            "python_version < '3.10.0'",
            "python_version < '3.9'",
        )

        matching.forEach {
            assertMatch(EmbeddedPythonRuntimeCompatibilityV1.marker(it, context))
        }
        excluded.forEach {
            assertNoMatch(EmbeddedPythonRuntimeCompatibilityV1.marker(it, context))
        }
    }

    private fun assertMatch(result: EmbeddedPythonCompatibilityResultV1) {
        assertEquals(EmbeddedPythonCompatibilityStateV1.MATCH, result.state)
        assertTrue(result.matches)
    }

    private fun assertNoMatch(result: EmbeddedPythonCompatibilityResultV1) {
        assertEquals(EmbeddedPythonCompatibilityStateV1.NO_MATCH, result.state)
        assertFalse(result.matches)
    }
}
