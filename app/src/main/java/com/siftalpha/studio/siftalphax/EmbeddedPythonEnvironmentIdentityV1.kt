package com.siftalpha.studio.siftalphax

import java.security.MessageDigest

/**
 * Alpha45 Pure-Python Environment v1 identity contract.
 *
 * This layer receives an already-selected dependency set. It does not parse markers, select wheels,
 * fetch artifacts, install packages, or mutate a Worker. Its only responsibility is to produce
 * deterministic dependency and project-owned environment identities for later preparation slices.
 */
@JvmInline
value class EmbeddedPythonDependencyFingerprintV1 private constructor(val value: String) {
    companion object {
        private val pattern = Regex("sha256:[0-9a-f]{64}")

        fun parse(value: String): EmbeddedPythonDependencyFingerprintV1 {
            require(pattern.matches(value)) {
                "dependency fingerprint must be sha256:<64 lowercase hex>"
            }
            return EmbeddedPythonDependencyFingerprintV1(value)
        }

        internal fun fromCanonicalUtf8(value: String): EmbeddedPythonDependencyFingerprintV1 =
            EmbeddedPythonDependencyFingerprintV1("sha256:" + sha256Hex(value.toByteArray(Charsets.UTF_8)))
    }
}

@JvmInline
value class EmbeddedPythonEnvironmentIdV1 private constructor(val value: String) {
    companion object {
        private val pattern = Regex("sha256:[0-9a-f]{64}")

        fun parse(value: String): EmbeddedPythonEnvironmentIdV1 {
            require(pattern.matches(value)) {
                "environment id must be sha256:<64 lowercase hex>"
            }
            return EmbeddedPythonEnvironmentIdV1(value)
        }

        internal fun fromCanonicalUtf8(value: String): EmbeddedPythonEnvironmentIdV1 =
            EmbeddedPythonEnvironmentIdV1("sha256:" + sha256Hex(value.toByteArray(Charsets.UTF_8)))
    }
}

data class EmbeddedPythonDependencyRuntimeContextV1(
    val pythonFullVersion: String,
    val pythonImplementation: String,
    val pythonAbi: String,
    val androidAbi: String,
    val androidApiPolicy: String,
) {
    init {
        requireToken(pythonFullVersion, "pythonFullVersion")
        requireToken(pythonImplementation, "pythonImplementation")
        requireToken(pythonAbi, "pythonAbi")
        requireToken(androidAbi, "androidAbi")
        requireToken(androidApiPolicy, "androidApiPolicy")
    }
}

data class EmbeddedPythonSelectedPackageV1(
    val normalizedName: String,
    val version: String,
    val marker: String?,
    val requiresPython: String?,
    val wheelFilename: String,
    val artifactSha256: String,
    val pythonTags: List<String>,
    val abiTags: List<String>,
    val platformTags: List<String>,
    val artifactOrigin: String,
) {
    init {
        require(normalizedName.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
            "normalizedName must be a normalized distribution name"
        }
        requireToken(version, "version")
        requireOptionalText(marker, "marker")
        requireOptionalText(requiresPython, "requiresPython")
        require(wheelFilename.isNotBlank() && wheelFilename.endsWith(".whl")) {
            "wheelFilename must end in .whl"
        }
        require(!wheelFilename.contains('/') && !wheelFilename.contains('\\')) {
            "wheelFilename must be a basename"
        }
        require(artifactSha256.matches(Regex("[0-9a-f]{64}"))) {
            "artifactSha256 must be 64 lowercase hex characters"
        }
        requireTagList(pythonTags, "pythonTags")
        requireTagList(abiTags, "abiTags")
        requireTagList(platformTags, "platformTags")
        requireToken(artifactOrigin, "artifactOrigin")
    }
}

data class EmbeddedPythonEnvironmentIdentityInputV1(
    val stableProjectIdentity: String,
    val runtimeContract: String,
    val runtimeProvenanceDigest: RuntimeProvenanceDigest,
    val dependencyFingerprint: EmbeddedPythonDependencyFingerprintV1,
    val installerPolicyVersion: String = EmbeddedPythonEnvironmentIdentityV1.INSTALLER_POLICY_VERSION,
) {
    init {
        requireToken(stableProjectIdentity, "stableProjectIdentity")
        requireToken(runtimeContract, "runtimeContract")
        requireToken(installerPolicyVersion, "installerPolicyVersion")
    }
}

object EmbeddedPythonEnvironmentIdentityV1 {
    const val DEPENDENCY_SCHEMA = "siftalpha.dependency-fingerprint.v1"
    const val ENVIRONMENT_SCHEMA = "siftalpha.environment.v1"
    const val INSTALLER_POLICY_VERSION = "pure-python-v1"
    const val LOCK_VERSION = "1.0"

    fun dependencyFingerprint(
        runtime: EmbeddedPythonDependencyRuntimeContextV1,
        selectedPackages: List<EmbeddedPythonSelectedPackageV1>,
    ): EmbeddedPythonDependencyFingerprintV1 {
        val canonicalPackages = selectedPackages
            .sortedWith(
                compareBy<EmbeddedPythonSelectedPackageV1>(
                    { it.normalizedName },
                    { it.version },
                    { it.wheelFilename },
                    { it.artifactSha256 },
                ),
            )
        require(
            canonicalPackages.zipWithNext().none { (left, right) ->
                left.normalizedName == right.normalizedName
            },
        ) {
            "selectedPackages must contain at most one selected package per normalized name"
        }

        val canonical = buildString {
            append('{')
            appendJsonKeyValue("installerPolicyVersion", INSTALLER_POLICY_VERSION)
            append(',')
            appendJsonKeyValue("lockVersion", LOCK_VERSION)
            append(',')
            appendJsonString("packages")
            append(':')
            append('[')
            canonicalPackages.forEachIndexed { index, pkg ->
                if (index > 0) append(',')
                appendPackage(pkg)
            }
            append(']')
            append(',')
            appendJsonKeyValue("schema", DEPENDENCY_SCHEMA)
            append(',')
            appendJsonString("selectedDependencyGroups")
            append(':')
            append("[]")
            append(',')
            appendJsonString("selectedEnvironment")
            append(':')
            append('{')
            appendJsonKeyValue("androidAbi", runtime.androidAbi)
            append(',')
            appendJsonKeyValue("androidApiPolicy", runtime.androidApiPolicy)
            append(',')
            appendJsonKeyValue("python", runtime.pythonFullVersion)
            append(',')
            appendJsonKeyValue("pythonAbi", runtime.pythonAbi)
            append(',')
            appendJsonKeyValue("pythonImplementation", runtime.pythonImplementation)
            append('}')
            append(',')
            appendJsonString("selectedExtras")
            append(':')
            append("[]")
            append('}')
        }

        return EmbeddedPythonDependencyFingerprintV1.fromCanonicalUtf8(canonical)
    }

    fun environmentId(input: EmbeddedPythonEnvironmentIdentityInputV1): EmbeddedPythonEnvironmentIdV1 {
        val canonical = buildString {
            append('{')
            appendJsonKeyValue("dependencyFingerprint", input.dependencyFingerprint.value)
            append(',')
            appendJsonKeyValue("installerPolicyVersion", input.installerPolicyVersion)
            append(',')
            appendJsonKeyValue("runtimeContract", input.runtimeContract)
            append(',')
            appendJsonKeyValue("runtimeProvenanceDigest", input.runtimeProvenanceDigest.value)
            append(',')
            appendJsonKeyValue("schema", ENVIRONMENT_SCHEMA)
            append(',')
            appendJsonKeyValue("stableProjectIdentity", input.stableProjectIdentity)
            append('}')
        }
        return EmbeddedPythonEnvironmentIdV1.fromCanonicalUtf8(canonical)
    }

    private fun StringBuilder.appendPackage(pkg: EmbeddedPythonSelectedPackageV1) {
        append('{')
        appendStringArray("abiTags", pkg.abiTags.sorted())
        append(',')
        appendJsonKeyValue("artifactOrigin", pkg.artifactOrigin)
        append(',')
        appendJsonKeyValue("artifactSha256", pkg.artifactSha256)
        append(',')
        appendJsonString("marker")
        append(':')
        appendNullableJsonString(pkg.marker)
        append(',')
        appendJsonKeyValue("name", pkg.normalizedName)
        append(',')
        appendStringArray("platformTags", pkg.platformTags.sorted())
        append(',')
        appendStringArray("pythonTags", pkg.pythonTags.sorted())
        append(',')
        appendJsonString("requiresPython")
        append(':')
        appendNullableJsonString(pkg.requiresPython)
        append(',')
        appendJsonKeyValue("version", pkg.version)
        append(',')
        appendJsonKeyValue("wheelFilename", pkg.wheelFilename)
        append('}')
    }

    private fun StringBuilder.appendStringArray(key: String, values: List<String>) {
        appendJsonString(key)
        append(':')
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendJsonString(value)
        }
        append(']')
    }

    private fun StringBuilder.appendJsonKeyValue(key: String, value: String) {
        appendJsonString(key)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendNullableJsonString(value: String?) {
        if (value == null) {
            append("null")
        } else {
            appendJsonString(value)
        }
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }
}

private fun requireToken(value: String, field: String) {
    require(value.isNotBlank()) { "$field must not be blank" }
    require(!value.contains('\u0000')) { "$field must not contain NUL" }
    require(value.toByteArray(Charsets.UTF_8).size <= 8 * 1024) {
        "$field exceeds 8192 UTF-8 bytes"
    }
}

private fun requireOptionalText(value: String?, field: String) {
    if (value != null) requireToken(value, field)
}

private fun requireTagList(values: List<String>, field: String) {
    require(values.isNotEmpty()) { "$field must not be empty" }
    require(values.distinct().size == values.size) { "$field must not contain duplicates" }
    values.forEachIndexed { index, value -> requireToken(value, "$field[$index]") }
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            append("%02x".format(byte.toInt() and 0xff))
        }
    }
}
