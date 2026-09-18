package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonDependencyModelV1Test {
    @Test
    fun parsesRequirementsAndPyproject() {
        val input = EmbeddedPythonRequirementParserV1.fromProjectFiles(
            requirementsText = "oci>=2.161,<3\nrequests>=2; python_version >= \"3.10\"\n",
            pyprojectText = """
                [project]
                requires-python = ">=3.13"
                dependencies = ["ignored==1.0"]
            """.trimIndent(),
        )
        assertEquals(
            EmbeddedPythonDependencyInputV1.SourceKind.REQUIREMENTS_TXT,
            input.sourceKind,
        )
        assertEquals(2, input.requirements.size)
        assertEquals(">=3.13", input.projectRequiresPython)
        assertTrue(
            EmbeddedPythonRequirementParserV1.versionMatches(
                input.requirements.first().specifier,
                "2.186.0",
            ),
        )
    }

    @Test
    fun parsesParenthesizedMetadataRequirement() {
        val requirement = EmbeddedPythonRequirementParserV1.parseRequirement(
            "demo (>=1.0,<2.0); python_version >= '3.10'",
        )
        assertEquals("demo", requirement.normalizedName)
        assertTrue(EmbeddedPythonRequirementParserV1.versionMatches(">=1.0,<2.0", "1.5"))
    }

    @Test
    fun rejectsUnsupportedDynamicOrBuildOnlyPyprojectAsRuntimeDependencies() {
        assertRejects {
            EmbeddedPythonRequirementParserV1.fromProjectFiles(
                requirementsText = null,
                pyprojectText = """
                    [build-system]
                    requires = ["setuptools"]
                """.trimIndent(),
            )
        }
        assertRejects {
            EmbeddedPythonRequirementParserV1.fromProjectFiles(
                requirementsText = null,
                pyprojectText = """
                    [project]
                    name = "demo"
                    version = "1.0"
                    dynamic = ["dependencies"]
                """.trimIndent(),
            )
        }
    }

    @Test
    fun markerEvaluationUsesAndroidRuntimeAndExtras() {
        val android = EmbeddedPythonRuntimeCompatibilityV1.marker(
            "sys_platform == 'android' and python_version >= '3.14'",
        )
        val extra = EmbeddedPythonRuntimeCompatibilityV1.marker(
            "extra == 'security'",
            EmbeddedPythonRuntimeCompatibilityContextV1(extra = "security"),
        )
        val notIn = EmbeddedPythonRuntimeCompatibilityV1.marker(
            "sys_platform not in 'win32,darwin'",
        )
        assertTrue(android.matches)
        assertTrue(extra.matches)
        assertTrue(notIn.matches)
    }

    @Test
    fun wheelSelectionRejectsManylinuxAndPrefersAndroid() {
        val pure = wheel("demo-1.0-py3-none-any.whl")
        val linux = wheel("demo-1.0-cp314-cp314-manylinux_2_28_aarch64.whl")
        val android = wheel("demo-1.0-cp314-cp314-android_26_arm64_v8a.whl")
        val selected = EmbeddedPythonWheelSelectionV1.select(
            normalizedName = "demo",
            version = "1.0",
            candidates = listOf(pure, linux, android),
            androidApiLevel = 36,
        )
        assertEquals(android.filename, selected?.wheel?.filename)
        assertTrue(selected?.nativeAndroid == true)

        val pureOnly = EmbeddedPythonWheelSelectionV1.select(
            normalizedName = "demo",
            version = "1.0",
            candidates = listOf(linux, pure),
            androidApiLevel = 36,
        )
        assertEquals(pure.filename, pureOnly?.wheel?.filename)
        assertFalse(pureOnly?.nativeAndroid == true)
    }

    @Test
    fun resolverIncludesTransitiveDependenciesWithoutNetwork() {
        val index = FakeIndex()
        val resolver = EmbeddedPythonDependencyResolverV1(index)
        val input = EmbeddedPythonRequirementParserV1.fromProjectFiles(
            requirementsText = "root>=1,<2\n",
            pyprojectText = null,
        )
        val plan = resolver.resolve(input, androidApiLevel = 36)
        assertEquals(listOf("child", "root"), plan.packages.map { it.normalizedName })
    }

    private fun assertRejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected validation failure")
        } catch (_: IllegalArgumentException) {
            // expected
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    private fun wheel(filename: String) = EmbeddedPythonIndexWheelV1(
        filename = filename,
        url = "https://files.pythonhosted.org/packages/$filename",
        sha256 = "a".repeat(64),
        size = 1L,
        requiresPython = ">=3.10",
        yanked = false,
    )

    private inner class FakeIndex : EmbeddedPythonPackageIndexV1 {
        override fun listWheels(normalizedName: String): List<EmbeddedPythonIndexWheelV1> =
            when (normalizedName) {
                "root" -> listOf(wheel("root-1.5-py3-none-any.whl"))
                "child" -> listOf(wheel("child-2.0-py3-none-any.whl"))
                else -> emptyList()
            }

        override fun metadata(
            normalizedName: String,
            version: String,
        ): EmbeddedPythonPackageMetadataV1 =
            when (normalizedName) {
                "root" -> EmbeddedPythonPackageMetadataV1(
                    normalizedName = "root",
                    version = version,
                    requiresPython = ">=3.10",
                    requiresDist = listOf("child>=2; sys_platform == 'android'"),
                )
                "child" -> EmbeddedPythonPackageMetadataV1(
                    normalizedName = "child",
                    version = version,
                    requiresPython = ">=3.10",
                    requiresDist = emptyList(),
                )
                else -> error("unknown package")
            }
    }
}
