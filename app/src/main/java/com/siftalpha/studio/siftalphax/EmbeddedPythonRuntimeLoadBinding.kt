package com.siftalpha.studio.siftalphax

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Stable source identity for the project content a Runtime worker is asked to load.
 *
 * This is deliberately separate from EmbeddedPythonExecutionSpec.generation, which is a
 * session/execution prototype counter and is not a durable project source identity.
 */
@JvmInline
value class ProjectSourceGeneration private constructor(val value: String) {
    companion object {
        const val MAX_UTF8_BYTES = 256

        fun of(value: String): ProjectSourceGeneration {
            require(value.isNotBlank()) { "project source generation must not be blank" }
            require(!value.contains('\u0000')) { "project source generation must not contain NUL" }
            require(value.toByteArray(Charsets.UTF_8).size <= MAX_UTF8_BYTES) {
                "project source generation exceeds $MAX_UTF8_BYTES UTF-8 bytes"
            }
            return ProjectSourceGeneration(value)
        }
    }
}

/**
 * Digest identifying the exact Runtime Base provenance used by the worker.
 */
@JvmInline
value class RuntimeProvenanceDigest private constructor(val value: String) {
    companion object {
        private val SHA256_PATTERN = Regex("sha256:[0-9a-f]{64}")

        fun of(value: String): RuntimeProvenanceDigest {
            require(SHA256_PATTERN.matches(value)) {
                "runtime provenance digest must be sha256:<64 lowercase hex>"
            }
            return RuntimeProvenanceDigest(value)
        }
    }
}

/**
 * The only dependency layer that alpha44 Slice 1 can truthfully bind.
 */
enum class DependencyLayerBinding(val wireValue: String) {
    STDLIB_ONLY("stdlib-only");

    companion object {
        fun fromWireValue(value: String): DependencyLayerBinding {
            require(value == STDLIB_ONLY.wireValue) {
                "unsupported alpha44 dependency layer binding"
            }
            return STDLIB_ONLY
        }
    }
}

/**
 * Text form of the hash over a RuntimeLoadBindingV1 canonical byte sequence.
 */
@JvmInline
value class ProcessBindingId private constructor(val value: String) {
    companion object {
        private val TEXT_PATTERN = Regex("sha256:[0-9a-f]{64}")

        fun parse(value: String): ProcessBindingId {
            require(TEXT_PATTERN.matches(value)) {
                "process binding id must be sha256:<64 lowercase hex>"
            }
            return ProcessBindingId(value)
        }

        fun fromCanonicalBytes(bytes: ByteArray): ProcessBindingId {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hex = buildString(digest.size * 2) {
                digest.forEach { byte ->
                    val unsigned = byte.toInt() and 0xff
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0f])
                }
            }
            return ProcessBindingId("sha256:$hex")
        }

        private const val HEX_DIGITS = "0123456789abcdef"
    }
}

/**
 * R-side alpha44 runtime load identity.
 *
 * The canonical byte contract is five length-prefixed UTF-8 fields in fixed order:
 *
 *   schema, projectIdentity, projectSourceGeneration, runtimeProvenanceDigest,
 *   dependencyLayerBinding
 *
 * Every length is an unsigned 32-bit big-endian byte count. The resulting bytes are hashed
 * directly to produce ProcessBindingId.
 */
data class RuntimeLoadBindingV1(
    val projectIdentity: String,
    val projectSourceGeneration: ProjectSourceGeneration,
    val runtimeProvenanceDigest: RuntimeProvenanceDigest,
    val dependencyLayerBinding: DependencyLayerBinding,
) {
    init {
        require(projectIdentity.isNotBlank()) { "project identity must not be blank" }
        require(!projectIdentity.contains('\u0000')) { "project identity must not contain NUL" }
        require(projectIdentity.toByteArray(Charsets.UTF_8).size <= MAX_PROJECT_IDENTITY_UTF8_BYTES) {
            "project identity exceeds $MAX_PROJECT_IDENTITY_UTF8_BYTES UTF-8 bytes"
        }
    }

    fun canonicalBytes(): ByteArray =
        ByteArrayOutputStream().apply {
            writeLengthPrefixedUtf8(SCHEMA)
            writeLengthPrefixedUtf8(projectIdentity)
            writeLengthPrefixedUtf8(projectSourceGeneration.value)
            writeLengthPrefixedUtf8(runtimeProvenanceDigest.value)
            writeLengthPrefixedUtf8(dependencyLayerBinding.wireValue)
        }.toByteArray()

    val processBindingId: ProcessBindingId
        get() = ProcessBindingId.fromCanonicalBytes(canonicalBytes())

    companion object {
        const val SCHEMA = "siftalpha.runtime-load-binding.v1"
        private const val MAX_PROJECT_IDENTITY_UTF8_BYTES = 256

        private fun ByteArrayOutputStream.writeLengthPrefixedUtf8(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size.toLong() <= UINT32_MAX) {
                "canonical field exceeds unsigned 32-bit length"
            }
            write((bytes.size ushr 24) and 0xff)
            write((bytes.size ushr 16) and 0xff)
            write((bytes.size ushr 8) and 0xff)
            write(bytes.size and 0xff)
            write(bytes)
        }

        private const val UINT32_MAX = 0xffff_ffffL
    }
}
