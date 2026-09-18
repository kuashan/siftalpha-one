package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWheelSelectionV1Test {
    @Test
    fun selectsCp314BeforePy314AndPy3RegardlessOfInputOrder() {
        val result = EmbeddedPythonWheelSelectionV1.select(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-py3-none-any.whl"),
                    wheel("demo-1.0.0-cp314-none-any.whl"),
                    wheel("demo-1.0.0-py314-none-any.whl"),
                ),
            ),
        )

        val selected = result as EmbeddedPythonWheelSelectionResultV1.Selected
        assertEquals("demo-1.0.0-cp314-none-any.whl", selected.wheel.filename)
        assertEquals(0, selected.priority)
        assertEquals(listOf("cp314"), selected.tags.pythonTags)
        assertEquals(listOf("none"), selected.tags.abiTags)
        assertEquals(listOf("any"), selected.tags.platformTags)
    }

    @Test
    fun usesFilenameAsDeterministicTieBreak() {
        val result = EmbeddedPythonWheelSelectionV1.select(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-2-py3-none-any.whl"),
                    wheel("demo-1.0.0-1-py3-none-any.whl"),
                ),
            ),
        )

        val selected = result as EmbeddedPythonWheelSelectionResultV1.Selected
        assertEquals("demo-1.0.0-1-py3-none-any.whl", selected.wheel.filename)
        assertEquals("1", selected.tags.buildTag)
    }

    @Test
    fun rejectsNativeOnlyCandidatesInPurePythonV1() {
        val result = EmbeddedPythonWheelSelectionV1.select(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-cp314-cp314-android_26_arm64_v8a.whl"),
                    wheel("demo-1.0.0-cp314-cp314-manylinux_2_28_aarch64.whl"),
                ),
            ),
        )

        val rejected = result as EmbeddedPythonWheelSelectionResultV1.Rejected
        assertEquals(
            EmbeddedPythonWheelSelectionFailureCode.NO_SUPPORTED_PURE_PYTHON_WHEEL,
            rejected.code,
        )
    }

    @Test
    fun rejectsWheelIdentityMismatch() {
        val result = EmbeddedPythonWheelSelectionV1.select(
            pkg(wheels = listOf(wheel("other-1.0.0-py3-none-any.whl"))),
        )

        val rejected = result as EmbeddedPythonWheelSelectionResultV1.Rejected
        assertEquals(
            EmbeddedPythonWheelSelectionFailureCode.WHEEL_IDENTITY_MISMATCH,
            rejected.code,
        )
    }

    @Test
    fun rejectsMalformedWheelFilename() {
        val result = EmbeddedPythonWheelSelectionV1.select(
            pkg(wheels = listOf(wheel("demo.whl"))),
        )

        val rejected = result as EmbeddedPythonWheelSelectionResultV1.Rejected
        assertEquals(
            EmbeddedPythonWheelSelectionFailureCode.INVALID_WHEEL_FILENAME,
            rejected.code,
        )
    }

    @Test
    fun parsesCompressedPythonTags() {
        val tags = requireNotNull(
            EmbeddedPythonWheelSelectionV1.parseWheelFilename(
                "demo-1.0.0-py3.py314-none-any.whl",
            ),
        )

        assertEquals(listOf("py3", "py314"), tags.pythonTags)
        assertEquals("demo", tags.normalizedDistribution)
        assertEquals("1.0.0", tags.version)
    }

    @Test
    fun supportsNormalizedDistributionIdentity() {
        val result = EmbeddedPythonWheelSelectionV1.select(
            EmbeddedPythonPylockV1Package(
                name = "Example.Package",
                normalizedName = "example-package",
                version = "1.0.0",
                marker = null,
                requiresPython = null,
                wheels = listOf(wheel("example_package-1.0.0-py3-none-any.whl")),
            ),
        )

        assertTrue(result is EmbeddedPythonWheelSelectionResultV1.Selected)
    }

    private fun pkg(
        wheels: List<EmbeddedPythonPylockV1Wheel>,
    ): EmbeddedPythonPylockV1Package =
        EmbeddedPythonPylockV1Package(
            name = "demo",
            normalizedName = "demo",
            version = "1.0.0",
            marker = null,
            requiresPython = null,
            wheels = wheels,
        )

    private fun wheel(filename: String): EmbeddedPythonPylockV1Wheel =
        EmbeddedPythonPylockV1Wheel(
            filename = filename,
            url = "https://example.test/$filename",
            projectRelativePath = null,
            size = 1234L,
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )
}
