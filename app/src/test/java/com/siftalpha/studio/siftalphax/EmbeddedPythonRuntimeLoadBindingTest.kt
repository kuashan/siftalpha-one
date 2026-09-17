package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonRuntimeLoadBindingTest {
    @Test
    fun sameInputProducesSameCanonicalBytesAndProcessBindingId() {
        val first = binding()
        val second = binding()

        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes())
        assertEquals(first.processBindingId, second.processBindingId)
        assertTrue(first.processBindingId.value.matches(Regex("sha256:[0-9a-f]{64}")))
    }

    @Test
    fun eachIdentityFieldChangesTheProcessBindingId() {
        val base = binding()

        assertNotEquals(
            base.processBindingId,
            binding(projectIdentity = "fixture-project-b").processBindingId,
        )
        assertNotEquals(
            base.processBindingId,
            binding(sourceGeneration = "source-generation-0002").processBindingId,
        )
        assertNotEquals(
            base.processBindingId,
            binding(
                runtimeProvenanceDigest =
                    "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            ).processBindingId,
        )
    }

    @Test
    fun dependencyLayerUsesOnlyTheAlpha44StdlibWireValue() {
        assertEquals("stdlib-only", DependencyLayerBinding.STDLIB_ONLY.wireValue)
        assertEquals(
            DependencyLayerBinding.STDLIB_ONLY,
            DependencyLayerBinding.fromWireValue("stdlib-only"),
        )
        assertRejects {
            DependencyLayerBinding.fromWireValue("pylock")
        }
    }

    @Test
    fun lengthPrefixPreventsConcatenationCollision() {
        val left = binding(projectIdentity = "ab", sourceGeneration = "c")
        val right = binding(projectIdentity = "a", sourceGeneration = "bc")

        assertFalse(left.canonicalBytes().contentEquals(right.canonicalBytes()))
        assertNotEquals(left.processBindingId, right.processBindingId)
    }

    @Test
    fun rejectsInvalidRuntimeProvenanceDigest() {
        assertRejects {
            RuntimeProvenanceDigest.of(
                "SHA256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            )
        }
        assertRejects {
            RuntimeProvenanceDigest.of("sha256:0123456789")
        }
        assertRejects {
            RuntimeProvenanceDigest.of(
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789ABCDEf",
            )
        }
    }

    @Test
    fun rejectsBlankOverlongAndNulSourceGenerations() {
        assertRejects { ProjectSourceGeneration.of("") }
        assertRejects { ProjectSourceGeneration.of("   ") }
        assertRejects { ProjectSourceGeneration.of("source\u0000generation") }
        assertRejects {
            ProjectSourceGeneration.of("a".repeat(ProjectSourceGeneration.MAX_UTF8_BYTES + 1))
        }
    }

    @Test
    fun rejectsNulProjectIdentity() {
        assertRejects {
            binding(projectIdentity = "fixture\u0000project")
        }
    }

    @Test
    fun fixedCrossLanguageTestVectorMatchesExpectedProcessBindingId() {
        val vector = binding()

        assertEquals(
            "sha256:aa45a84993ee40b1398af1e0e4398d8b5959e31dd7ff195b099cb062d429be5d",
            vector.processBindingId.value,
        )
    }

    private fun binding(
        projectIdentity: String = "fixture-project-a",
        sourceGeneration: String = "source-generation-0001",
        runtimeProvenanceDigest: String =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    ): RuntimeLoadBindingV1 =
        RuntimeLoadBindingV1(
            projectIdentity = projectIdentity,
            projectSourceGeneration = ProjectSourceGeneration.of(sourceGeneration),
            runtimeProvenanceDigest = RuntimeProvenanceDigest.of(runtimeProvenanceDigest),
            dependencyLayerBinding = DependencyLayerBinding.STDLIB_ONLY,
        )

    private fun assertRejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected contract validation failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
