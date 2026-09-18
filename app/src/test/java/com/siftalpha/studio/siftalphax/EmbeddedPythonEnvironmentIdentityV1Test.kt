package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonEnvironmentIdentityV1Test {
    private val runtime = EmbeddedPythonDependencyRuntimeContextV1(
        pythonFullVersion = "3.14.7",
        pythonImplementation = "cpython",
        pythonAbi = "cp314",
        androidAbi = "arm64-v8a",
        androidApiPolicy = "min:26",
    )

    @Test
    fun dependencyFingerprintIsStableAcrossPackageAndTagOrder() {
        val first = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime,
            selectedPackages = listOf(
                packageFact(
                    name = "zeta",
                    hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    pythonTags = listOf("py314", "py3"),
                ),
                packageFact(
                    name = "alpha",
                    hash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    pythonTags = listOf("py3", "py314"),
                ),
            ),
        )
        val second = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime,
            selectedPackages = listOf(
                packageFact(
                    name = "alpha",
                    hash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    pythonTags = listOf("py314", "py3"),
                ),
                packageFact(
                    name = "zeta",
                    hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    pythonTags = listOf("py3", "py314"),
                ),
            ),
        )

        assertEquals(first, second)
        assertTrue(first.value.matches(Regex("sha256:[0-9a-f]{64}")))
    }

    @Test
    fun exactArtifactAndRuntimeFactsChangeFingerprint() {
        val base = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime,
            selectedPackages = listOf(packageFact()),
        )
        val differentArtifact = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime,
            selectedPackages = listOf(
                packageFact(
                    hash = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                ),
            ),
        )
        val differentRuntime = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime.copy(androidApiPolicy = "min:28"),
            selectedPackages = listOf(packageFact()),
        )

        assertNotEquals(base, differentArtifact)
        assertNotEquals(base, differentRuntime)
    }

    @Test
    fun environmentIdIsProjectOwnedEvenForSameDependencies() {
        val fingerprint = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime,
            selectedPackages = listOf(packageFact()),
        )
        val provenance = RuntimeProvenanceDigest.of(
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )

        val projectA = EmbeddedPythonEnvironmentIdentityV1.environmentId(
            EmbeddedPythonEnvironmentIdentityInputV1(
                stableProjectIdentity = "project-a",
                runtimeContract = "cpython-3.14.7-android-arm64-v8a",
                runtimeProvenanceDigest = provenance,
                dependencyFingerprint = fingerprint,
            ),
        )
        val projectB = EmbeddedPythonEnvironmentIdentityV1.environmentId(
            EmbeddedPythonEnvironmentIdentityInputV1(
                stableProjectIdentity = "project-b",
                runtimeContract = "cpython-3.14.7-android-arm64-v8a",
                runtimeProvenanceDigest = provenance,
                dependencyFingerprint = fingerprint,
            ),
        )

        assertNotEquals(projectA, projectB)
        assertTrue(projectA.value.matches(Regex("sha256:[0-9a-f]{64}")))
    }

    @Test
    fun sameIdentityInputProducesSameEnvironmentId() {
        val fingerprint = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = runtime,
            selectedPackages = listOf(packageFact()),
        )
        val input = EmbeddedPythonEnvironmentIdentityInputV1(
            stableProjectIdentity = "project-a",
            runtimeContract = "cpython-3.14.7-android-arm64-v8a",
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
            dependencyFingerprint = fingerprint,
        )

        assertEquals(
            EmbeddedPythonEnvironmentIdentityV1.environmentId(input),
            EmbeddedPythonEnvironmentIdentityV1.environmentId(input),
        )
    }

    @Test
    fun duplicateSelectedPackageNameIsRejected() {
        assertRejects {
            EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
                runtime = runtime,
                selectedPackages = listOf(packageFact(), packageFact()),
            )
        }
    }

    @Test
    fun malformedHashesAndTagsAreRejected() {
        assertRejects {
            packageFact(hash = "ABC")
        }
        assertRejects {
            packageFact(pythonTags = emptyList())
        }
        assertRejects {
            packageFact(pythonTags = listOf("py3", "py3"))
        }
    }

    private fun packageFact(
        name: String = "demo",
        version: String = "1.0.0",
        hash: String = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        pythonTags: List<String> = listOf("py3"),
    ): EmbeddedPythonSelectedPackageV1 =
        EmbeddedPythonSelectedPackageV1(
            normalizedName = name,
            version = version,
            marker = null,
            requiresPython = ">=3.10",
            wheelFilename = "$name-$version-py3-none-any.whl",
            artifactSha256 = hash,
            pythonTags = pythonTags,
            abiTags = listOf("none"),
            platformTags = listOf("any"),
            artifactOrigin = "https://example.test/$name-$version-py3-none-any.whl",
        )

    private fun assertRejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected validation failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
