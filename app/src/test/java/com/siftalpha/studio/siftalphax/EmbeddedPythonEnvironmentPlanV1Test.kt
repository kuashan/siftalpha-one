package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonEnvironmentPlanV1Test {
    @Test
    fun composesMarkerFilteringAndroidWheelSelectionAndFingerprint() {
        val document = EmbeddedPythonPylockV1Document(
            lockVersion = "1.0",
            requiresPython = ">=3.14,<3.15",
            environments = listOf("sys_platform == 'android'"),
            createdBy = "test-locker",
            packages = listOf(
                pkg(
                    name = "old-only",
                    marker = "python_version <= '3.7'",
                    wheel = wheel("old_only-1.0.0-py3-none-any.whl"),
                ),
                pkg(
                    name = "crypto-demo",
                    marker = "python_version >= '3.8'",
                    wheel = wheel(
                        "crypto_demo-1.0.0-cp314-cp314-android_24_arm64_v8a.whl",
                    ),
                ),
                pkg(
                    name = "pure-demo",
                    wheel = wheel("pure_demo-1.0.0-py3-none-any.whl"),
                ),
            ),
        )

        val result = EmbeddedPythonEnvironmentPlannerV1.plan(
            document = document,
            androidApiLevel = 26,
        )
        val ready = result as EmbeddedPythonEnvironmentPlanResultV1.Ready

        assertEquals(
            listOf("crypto-demo", "pure-demo"),
            ready.plan.selectedPackages.map { it.packageEntry.normalizedName },
        )
        assertEquals(
            EmbeddedPythonWheelArtifactKind.ANDROID_NATIVE,
            ready.plan.selectedPackages[0].wheelSelection.artifactKind,
        )
        assertEquals(
            EmbeddedPythonWheelArtifactKind.PURE_PYTHON,
            ready.plan.selectedPackages[1].wheelSelection.artifactKind,
        )
        assertTrue(
            ready.plan.dependencyFingerprint.value.matches(
                Regex("sha256:[0-9a-f]{64}"),
            ),
        )
    }

    @Test
    fun currentOciStylePythonBranchesProduceStablePlan() {
        val document = EmbeddedPythonPylockV1Document(
            lockVersion = "1.0",
            requiresPython = ">=3.6",
            environments = emptyList(),
            createdBy = "test-locker",
            packages = listOf(
                pkg(
                    name = "cryptography",
                    version = "50.0.0",
                    marker = "python_version >= '3.8'",
                    requiresPython = ">=3.8",
                    wheel = wheel(
                        "cryptography-50.0.0-cp314-cp314-android_24_arm64_v8a.whl",
                    ),
                ),
                pkg(
                    name = "legacy-crypto",
                    marker = "python_version <= '3.7'",
                    wheel = wheel("legacy_crypto-1.0.0-py3-none-any.whl"),
                ),
                pkg(
                    name = "oci",
                    version = "2.186.0",
                    wheel = wheel("oci-2.186.0-py3-none-any.whl"),
                ),
            ),
        )

        val first = EmbeddedPythonEnvironmentPlannerV1.plan(document, 26)
            as EmbeddedPythonEnvironmentPlanResultV1.Ready
        val second = EmbeddedPythonEnvironmentPlannerV1.plan(document, 26)
            as EmbeddedPythonEnvironmentPlanResultV1.Ready

        assertEquals(first.plan.dependencyFingerprint, second.plan.dependencyFingerprint)
        assertEquals(
            listOf("cryptography", "oci"),
            first.plan.selectedPackages.map { it.packageEntry.normalizedName },
        )
    }

    @Test
    fun deviceApiChangesFingerprintWhenRuntimePolicyChanges() {
        val document = simpleDocument()
        val api26 = EmbeddedPythonEnvironmentPlannerV1.plan(document, 26)
            as EmbeddedPythonEnvironmentPlanResultV1.Ready
        val api30 = EmbeddedPythonEnvironmentPlannerV1.plan(document, 30)
            as EmbeddedPythonEnvironmentPlanResultV1.Ready

        assertNotEquals(api26.plan.dependencyFingerprint, api30.plan.dependencyFingerprint)
    }

    @Test
    fun rejectsIncompatibleRootPythonAndEnvironmentMarker() {
        assertRejected(
            simpleDocument().copy(requiresPython = "<3.14"),
            EmbeddedPythonEnvironmentPlanFailureCodeV1.LOCK_REQUIRES_PYTHON_INCOMPATIBLE,
        )
        assertRejected(
            simpleDocument().copy(environments = listOf("sys_platform == 'linux'")),
            EmbeddedPythonEnvironmentPlanFailureCodeV1.LOCK_ENVIRONMENT_INCOMPATIBLE,
        )
    }

    @Test
    fun rejectsUnknownMarkerInsteadOfSilentlyDroppingPackage() {
        assertRejected(
            simpleDocument().copy(
                packages = listOf(
                    pkg(
                        name = "demo",
                        marker = "extra == 'fast'",
                        wheel = wheel("demo-1.0.0-py3-none-any.whl"),
                    ),
                ),
            ),
            EmbeddedPythonEnvironmentPlanFailureCodeV1.COMPATIBILITY_UNSUPPORTED,
        )
    }

    @Test
    fun rejectsDuplicateActivePackageName() {
        assertRejected(
            simpleDocument().copy(
                packages = listOf(
                    pkg(
                        name = "demo",
                        version = "1.0.0",
                        wheel = wheel("demo-1.0.0-py3-none-any.whl"),
                    ),
                    pkg(
                        name = "demo",
                        version = "2.0.0",
                        wheel = wheel("demo-2.0.0-py3-none-any.whl"),
                    ),
                ),
            ),
            EmbeddedPythonEnvironmentPlanFailureCodeV1.DUPLICATE_SELECTED_PACKAGE,
        )
    }

    @Test
    fun rejectsForeignNativeWheel() {
        assertRejected(
            simpleDocument().copy(
                packages = listOf(
                    pkg(
                        name = "demo",
                        wheel = wheel("demo-1.0.0-cp314-cp314-manylinux_2_28_aarch64.whl"),
                    ),
                ),
            ),
            EmbeddedPythonEnvironmentPlanFailureCodeV1.WHEEL_UNSUPPORTED,
        )
    }

    private fun simpleDocument() = EmbeddedPythonPylockV1Document(
        lockVersion = "1.0",
        requiresPython = ">=3.14,<3.15",
        environments = emptyList(),
        createdBy = "test-locker",
        packages = listOf(
            pkg(
                name = "demo",
                wheel = wheel("demo-1.0.0-py3-none-any.whl"),
            ),
        ),
    )

    private fun pkg(
        name: String,
        version: String = "1.0.0",
        marker: String? = null,
        requiresPython: String? = null,
        wheel: EmbeddedPythonPylockV1Wheel,
    ): EmbeddedPythonPylockV1Package =
        EmbeddedPythonPylockV1Package(
            name = name,
            normalizedName = name.replace('_', '-').replace('.', '-').lowercase(),
            version = version,
            marker = marker,
            requiresPython = requiresPython,
            wheels = listOf(wheel),
        )

    private fun wheel(filename: String) = EmbeddedPythonPylockV1Wheel(
        filename = filename,
        url = "https://example.test/" + filename,
        projectRelativePath = null,
        size = 100L,
        sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    )

    private fun assertRejected(
        document: EmbeddedPythonPylockV1Document,
        code: EmbeddedPythonEnvironmentPlanFailureCodeV1,
    ) {
        val result = EmbeddedPythonEnvironmentPlannerV1.plan(document, 26)
        val rejected = result as EmbeddedPythonEnvironmentPlanResultV1.Rejected
        assertEquals(code, rejected.code)
    }
}
