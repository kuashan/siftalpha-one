package com.siftalpha.studio.siftalphax

import java.security.MessageDigest
import java.util.ArrayDeque

data class EmbeddedPythonResolvedPackageV1(
    val normalizedName: String,
    val version: String,
    val wheel: EmbeddedPythonSelectedWheelV1,
    val requiresPython: String?,
)

data class EmbeddedPythonDependencyPlanV1(
    val sourceFingerprint: String,
    val resolvedFingerprint: String,
    val packages: List<EmbeddedPythonResolvedPackageV1>,
)

class EmbeddedPythonDependencyResolverV1(
    private val index: EmbeddedPythonPackageIndexV1 = PypiEmbeddedPythonPackageIndexV1(),
    private val runtime: EmbeddedPythonRuntimeCompatibilityContextV1 =
        EmbeddedPythonRuntimeCompatibilityContextV1(),
) {
    fun resolve(
        input: EmbeddedPythonDependencyInputV1,
        androidApiLevel: Int,
    ): EmbeddedPythonDependencyPlanV1 {
        require(androidApiLevel > 0)
        input.projectRequiresPython?.let { requiresPython ->
            val result = EmbeddedPythonRuntimeCompatibilityV1.requiresPython(requiresPython, runtime)
            check(result.matches) {
                "PROJECT_REQUIRES_PYTHON_INCOMPATIBLE: " + result.detail.orEmpty()
            }
        }

        val constraints = linkedMapOf<String, MutableList<EmbeddedPythonRequirementV1>>()
        val extras = linkedMapOf<String, MutableSet<String>>()
        val selected = linkedMapOf<String, EmbeddedPythonResolvedPackageV1>()
        val selectedMetadata = linkedMapOf<String, EmbeddedPythonPackageMetadataV1>()
        val processedExtras = linkedMapOf<String, Set<String>>()
        val queue = ArrayDeque<String>()

        fun markerApplies(requirement: EmbeddedPythonRequirementV1, activeExtras: Set<String>): Boolean {
            val marker = requirement.marker ?: return true
            val contexts = buildList {
                add(runtime.copy(extra = ""))
                activeExtras.sorted().forEach { add(runtime.copy(extra = it)) }
            }
            var unsupported: String? = null
            contexts.forEach { context ->
                val result = EmbeddedPythonRuntimeCompatibilityV1.marker(marker, context)
                when (result.state) {
                    EmbeddedPythonCompatibilityStateV1.MATCH -> return true
                    EmbeddedPythonCompatibilityStateV1.NO_MATCH -> Unit
                    EmbeddedPythonCompatibilityStateV1.UNSUPPORTED ->
                        unsupported = result.detail ?: "unsupported marker"
                }
            }
            check(unsupported == null) { "UNSUPPORTED_ENVIRONMENT_MARKER: $unsupported" }
            return false
        }

        fun addRequirement(requirement: EmbeddedPythonRequirementV1, activeExtras: Set<String>) {
            if (!markerApplies(requirement, activeExtras)) return
            val list = constraints.getOrPut(requirement.normalizedName) { mutableListOf() }
            var changed = false
            if (list.none {
                    it.specifier == requirement.specifier &&
                        it.extras == requirement.extras
                }
            ) {
                list += requirement.copy(marker = null)
                changed = true
            }
            val targetExtras = extras.getOrPut(requirement.normalizedName) { linkedSetOf() }
            if (targetExtras.addAll(requirement.extras)) changed = true
            if (changed) queue.add(requirement.normalizedName)
        }

        input.requirements.forEach { addRequirement(it, emptySet()) }

        while (queue.isNotEmpty()) {
            check(selected.size <= MAX_PACKAGES) { "dependency graph exceeds $MAX_PACKAGES packages" }
            val name = queue.removeFirst()
            val packageConstraints = constraints[name].orEmpty()
            val activeExtras = extras[name].orEmpty().toSet()
            val existing = selected[name]
            if (existing != null) {
                check(packageConstraints.all {
                    EmbeddedPythonRequirementParserV1.versionMatches(it.specifier, existing.version)
                }) {
                    "DEPENDENCY_CONFLICT: " + name + " already selected as " + existing.version
                }
                if (activeExtras == processedExtras[name].orEmpty()) continue
                val metadata = requireNotNull(selectedMetadata[name])
                metadata.requiresDist.forEach { raw ->
                    addRequirement(
                        EmbeddedPythonRequirementParserV1.parseRequirement(raw),
                        activeExtras,
                    )
                }
                processedExtras[name] = activeExtras
                continue
            }

            val wheels = index.listWheels(name)
            val versions = wheels.mapNotNull {
                EmbeddedPythonWheelSelectionV1.parseWheelFilename(it.filename)?.version
            }
                .distinct()
                .filter { version ->
                    EmbeddedPythonRequirementParserV1.versionMatches("", version) &&
                        packageConstraints.all {
                        EmbeddedPythonRequirementParserV1.versionMatches(it.specifier, version)
                    }
                }
                .sortedWith { left, right ->
                    -EmbeddedPythonRequirementParserV1.compareVersions(left, right)
                }

            var resolved: EmbeddedPythonResolvedPackageV1? = null
            var packageMetadata: EmbeddedPythonPackageMetadataV1? = null
            for (version in versions.take(MAX_VERSION_ATTEMPTS)) {
                val versionWheels = wheels.filter {
                    EmbeddedPythonWheelSelectionV1.parseWheelFilename(it.filename)?.version == version &&
                        it.requiresPython?.let { spec ->
                            EmbeddedPythonRuntimeCompatibilityV1.requiresPython(spec, runtime).matches
                        } != false
                }
                val selectedWheel = EmbeddedPythonWheelSelectionV1.select(
                    normalizedName = name,
                    version = version,
                    candidates = versionWheels,
                    androidApiLevel = androidApiLevel,
                ) ?: continue
                val metadata = index.metadata(name, version)
                if (
                    metadata.requiresPython?.let { spec ->
                        EmbeddedPythonRuntimeCompatibilityV1.requiresPython(spec, runtime).matches
                    } == false
                ) continue
                resolved = EmbeddedPythonResolvedPackageV1(
                    normalizedName = name,
                    version = version,
                    wheel = selectedWheel,
                    requiresPython = metadata.requiresPython,
                )
                packageMetadata = metadata
                break
            }

            val finalResolved = checkNotNull(resolved) {
                "NO_COMPATIBLE_WHEEL: $name has no supported CPython 3.14 Android or pure-Python wheel"
            }
            val finalMetadata = checkNotNull(packageMetadata)
            selected[name] = finalResolved
            selectedMetadata[name] = finalMetadata
            processedExtras[name] = activeExtras
            finalMetadata.requiresDist.forEach { raw ->
                addRequirement(
                    EmbeddedPythonRequirementParserV1.parseRequirement(raw),
                    activeExtras,
                )
            }
        }

        val ordered = selected.values.sortedBy { it.normalizedName }
        val canonical = buildString {
            append("runtime=cpython-3.14.7-android-arm64-v8a\n")
            append("api=").append(androidApiLevel).append('\n')
            ordered.forEach { pkg ->
                append(pkg.normalizedName)
                    .append("==")
                    .append(pkg.version)
                    .append('#')
                    .append(pkg.wheel.wheel.sha256)
                    .append('\n')
            }
        }
        return EmbeddedPythonDependencyPlanV1(
            sourceFingerprint = input.sourceFingerprint,
            resolvedFingerprint = "sha256:" + sha256Hex(canonical.toByteArray(Charsets.UTF_8)),
            packages = ordered,
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val MAX_PACKAGES = 256
        private const val MAX_VERSION_ATTEMPTS = 64
    }
}
