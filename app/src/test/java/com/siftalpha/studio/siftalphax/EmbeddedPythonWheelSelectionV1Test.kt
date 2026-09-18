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
        assertEquals(EmbeddedPythonWheelArtifactKind.PURE_PYTHON, selected.artifactKind)
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
    fun pureSelectorStillRejectsNativeOnlyCandidates() {
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
    fun androidSelectorAcceptsMatchingCp314AndroidWheel() {
        val result = EmbeddedPythonWheelSelectionV1.selectForAndroidRuntime(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-cp314-cp314-android_24_arm64_v8a.whl"),
                ),
            ),
            androidApiLevel = 26,
        )

        val selected = result as EmbeddedPythonWheelSelectionResultV1.Selected
        assertEquals(EmbeddedPythonWheelArtifactKind.ANDROID_NATIVE, selected.artifactKind)
        assertEquals(24, selected.minimumAndroidApi)
        assertEquals(0, selected.priority)
    }

    @Test
    fun androidSelectorAcceptsPy3NoneAndroidArtifact() {
        val result = EmbeddedPythonWheelSelectionV1.selectForAndroidRuntime(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-py3-none-android_24_arm64_v8a.whl"),
                ),
            ),
            androidApiLevel = 26,
        )

        val selected = result as EmbeddedPythonWheelSelectionResultV1.Selected
        assertEquals(EmbeddedPythonWheelArtifactKind.ANDROID_NATIVE, selected.artifactKind)
        assertEquals(2, selected.priority)
    }

    @Test
    fun androidSelectorRejectsTooNewApiWrongAbiAndManylinux() {
        val result = EmbeddedPythonWheelSelectionV1.selectForAndroidRuntime(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-cp314-cp314-android_28_arm64_v8a.whl"),
                    wheel("demo-1.0.0-cp314-cp314-android_24_x86_64.whl"),
                    wheel("demo-1.0.0-cp314-cp314-manylinux_2_28_aarch64.whl"),
                ),
            ),
            androidApiLevel = 26,
        )

        val rejected = result as EmbeddedPythonWheelSelectionResultV1.Rejected
        assertEquals(
            EmbeddedPythonWheelSelectionFailureCode.NO_SUPPORTED_ANDROID_WHEEL,
            rejected.code,
        )
    }

    @Test
    fun androidSelectorPrefersCompatibleAndroidWheelThenFallsBackToPure() {
        val native = EmbeddedPythonWheelSelectionV1.selectForAndroidRuntime(
            pkg(
                wheels = listOf(
                    wheel("demo-1.0.0-py3-none-any.whl"),
                    wheel("demo-1.0.0-cp314-cp314-android_24_arm64_v8a.whl"),
                ),
            ),
            androidApiLevel = 26,
        )
        val nativeSelected = native as EmbeddedPythonWheelSelectionResultV1.Selected
        assertEquals(EmbeddedPythonWheelArtifactKind.ANDROID_NATIVE, nativeSelected.artifactKind)

        val pureFallback = EmbeddedPythonWheelSelectionV1.selectForAndroidRuntime(
            pkg(wheels = listOf(wheel("demo-1.0.0-py3-none-any.whl"))),
            androidApiLevel = 26,
        )
        val pureSelected = pureFallback as EmbeddedPythonWheelSelectionResultV1.Selected
        assertEquals(EmbeddedPythonWheelArtifactKind.PURE_PYTHON, pureSelected.artifactKind)
        assertEquals(12, pureSelected.priority)
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
