package com.siftalpha.studio.siftalphax

/**
 * Environment v1 wheel compatibility and deterministic selection.
 *
 * This layer only selects from wheel candidates already present in an accepted pylock document.
 * It performs no network access, dependency resolution, extraction, installation, or import.
 */
enum class EmbeddedPythonWheelSelectionFailureCode {
    INVALID_WHEEL_FILENAME,
    WHEEL_IDENTITY_MISMATCH,
    NO_SUPPORTED_PURE_PYTHON_WHEEL,
    NO_SUPPORTED_ANDROID_WHEEL,
}

enum class EmbeddedPythonWheelArtifactKind {
    PURE_PYTHON,
    ANDROID_NATIVE,
}

sealed interface EmbeddedPythonWheelSelectionResultV1 {
    data class Selected(
        val wheel: EmbeddedPythonPylockV1Wheel,
        val tags: EmbeddedPythonWheelTagsV1,
        val priority: Int,
        val artifactKind: EmbeddedPythonWheelArtifactKind = EmbeddedPythonWheelArtifactKind.PURE_PYTHON,
        val minimumAndroidApi: Int? = null,
    ) : EmbeddedPythonWheelSelectionResultV1

    data class Rejected(
        val code: EmbeddedPythonWheelSelectionFailureCode,
        val detail: String,
    ) : EmbeddedPythonWheelSelectionResultV1
}

data class EmbeddedPythonWheelTagsV1(
    val distribution: String,
    val normalizedDistribution: String,
    val version: String,
    val buildTag: String?,
    val pythonTags: List<String>,
    val abiTags: List<String>,
    val platformTags: List<String>,
)

object EmbeddedPythonWheelSelectionV1 {
    private val tagToken = Regex("[A-Za-z0-9_]+")
    private val buildTagPattern = Regex("[0-9][A-Za-z0-9_]*")
    private val nameNormalizer = Regex("[-_.]+")
    private val androidPlatformPattern = Regex("android_([0-9]+)_([A-Za-z0-9_]+)")

    /**
     * Stable Pure-Python Environment v1 selector:
     *
     *   cp314-none-any > py314-none-any > py3-none-any
     */
    fun select(
        pkg: EmbeddedPythonPylockV1Package,
    ): EmbeddedPythonWheelSelectionResultV1 =
        selectCandidates(
            pkg = pkg,
            mode = SelectionMode.PURE_ONLY,
            androidApiLevel = null,
            androidAbi = null,
        )

    /**
     * OCI-priority Android selector. It extends the same deterministic contract with Android
     * CPython wheels while keeping manylinux/musllinux and other foreign platforms ineligible.
     *
     * Priority:
     *   cp314-cp314-android > py314-none-android > py3-none-android
     *   > cp314-none-any > py314-none-any > py3-none-any
     *
     * The installer still has to verify wheel contents and native-library closure before loading.
     */
    fun selectForAndroidRuntime(
        pkg: EmbeddedPythonPylockV1Package,
        androidApiLevel: Int,
        androidAbi: String = "arm64_v8a",
    ): EmbeddedPythonWheelSelectionResultV1 {
        require(androidApiLevel > 0) { "androidApiLevel must be positive" }
        require(androidAbi.matches(Regex("[A-Za-z0-9_]+"))) { "androidAbi is invalid" }

        return selectCandidates(
            pkg = pkg,
            mode = SelectionMode.ANDROID_ALLOWED,
            androidApiLevel = androidApiLevel,
            androidAbi = androidAbi,
        )
    }

    fun parseWheelFilename(filename: String): EmbeddedPythonWheelTagsV1? {
        if (!filename.endsWith(".whl")) return null
        val stem = filename.removeSuffix(".whl")
        val parts = stem.split('-')
        if (parts.size !in 5..6) return null
        if (parts.any { it.isBlank() }) return null

        val distribution = parts[0]
        val version = parts[1]
        val buildTag = if (parts.size == 6) parts[2] else null
        val pythonPart = parts[parts.size - 3]
        val abiPart = parts[parts.size - 2]
        val platformPart = parts[parts.size - 1]

        if (buildTag != null && !buildTagPattern.matches(buildTag)) return null

        val pythonTags = splitTags(pythonPart) ?: return null
        val abiTags = splitTags(abiPart) ?: return null
        val platformTags = splitTags(platformPart) ?: return null

        return EmbeddedPythonWheelTagsV1(
            distribution = distribution,
            normalizedDistribution = normalizeDistribution(distribution),
            version = version,
            buildTag = buildTag,
            pythonTags = pythonTags,
            abiTags = abiTags,
            platformTags = platformTags,
        )
    }

    private fun selectCandidates(
        pkg: EmbeddedPythonPylockV1Package,
        mode: SelectionMode,
        androidApiLevel: Int?,
        androidAbi: String?,
    ): EmbeddedPythonWheelSelectionResultV1 {
        val compatible = mutableListOf<EmbeddedPythonWheelSelectionResultV1.Selected>()

        pkg.wheels.forEach { wheel ->
            val tags = parseWheelFilename(wheel.filename)
                ?: return rejected(
                    EmbeddedPythonWheelSelectionFailureCode.INVALID_WHEEL_FILENAME,
                    "invalid wheel filename: " + wheel.filename,
                )

            if (tags.normalizedDistribution != pkg.normalizedName || tags.version != pkg.version) {
                return rejected(
                    EmbeddedPythonWheelSelectionFailureCode.WHEEL_IDENTITY_MISMATCH,
                    "wheel " + wheel.filename + " does not match " +
                        pkg.normalizedName + "==" + pkg.version,
                )
            }

            val selected = when (mode) {
                SelectionMode.PURE_ONLY -> pureCandidate(wheel, tags)
                SelectionMode.ANDROID_ALLOWED -> {
                    requireNotNull(androidApiLevel)
                    requireNotNull(androidAbi)
                    androidCandidate(
                        wheel = wheel,
                        tags = tags,
                        androidApiLevel = androidApiLevel,
                        androidAbi = androidAbi,
                    ) ?: pureCandidate(wheel, tags)?.copy(priority = itPriority(pureCandidate(wheel, tags)) + 10)
                }
            }
            if (selected != null) compatible += selected
        }

        return compatible
            .sortedWith(compareBy({ it.priority }, { it.wheel.filename }))
            .firstOrNull()
            ?: rejected(
                if (mode == SelectionMode.PURE_ONLY) {
                    EmbeddedPythonWheelSelectionFailureCode.NO_SUPPORTED_PURE_PYTHON_WHEEL
                } else {
                    EmbeddedPythonWheelSelectionFailureCode.NO_SUPPORTED_ANDROID_WHEEL
                },
                if (mode == SelectionMode.PURE_ONLY) {
                    "no py3/py314/cp314 none-any wheel for " +
                        pkg.normalizedName + "==" + pkg.version
                } else {
                    "no compatible CPython 3.14 Android or pure wheel for " +
                        pkg.normalizedName + "==" + pkg.version
                },
            )
    }

    private fun pureCandidate(
        wheel: EmbeddedPythonPylockV1Wheel,
        tags: EmbeddedPythonWheelTagsV1,
    ): EmbeddedPythonWheelSelectionResultV1.Selected? {
        if (tags.abiTags != listOf("none")) return null
        if (tags.platformTags != listOf("any")) return null

        val priority = when {
            "cp314" in tags.pythonTags -> 0
            "py314" in tags.pythonTags -> 1
            "py3" in tags.pythonTags -> 2
            else -> return null
        }
        return EmbeddedPythonWheelSelectionResultV1.Selected(
            wheel = wheel,
            tags = tags,
            priority = priority,
            artifactKind = EmbeddedPythonWheelArtifactKind.PURE_PYTHON,
        )
    }

    private fun androidCandidate(
        wheel: EmbeddedPythonPylockV1Wheel,
        tags: EmbeddedPythonWheelTagsV1,
        androidApiLevel: Int,
        androidAbi: String,
    ): EmbeddedPythonWheelSelectionResultV1.Selected? {
        val compatiblePlatformApis = tags.platformTags.mapNotNull { platformTag ->
            val match = androidPlatformPattern.matchEntire(platformTag) ?: return@mapNotNull null
            val minimumApi = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val abi = match.groupValues[2]
            if (abi != androidAbi || minimumApi > androidApiLevel) return@mapNotNull null
            minimumApi
        }
        if (compatiblePlatformApis.isEmpty()) return null

        val priority = when {
            "cp314" in tags.pythonTags && "cp314" in tags.abiTags -> 0
            "py314" in tags.pythonTags && tags.abiTags == listOf("none") -> 1
            "py3" in tags.pythonTags && tags.abiTags == listOf("none") -> 2
            else -> return null
        }

        return EmbeddedPythonWheelSelectionResultV1.Selected(
            wheel = wheel,
            tags = tags,
            priority = priority,
            artifactKind = EmbeddedPythonWheelArtifactKind.ANDROID_NATIVE,
            minimumAndroidApi = compatiblePlatformApis.maxOrNull(),
        )
    }

    private fun itPriority(
        selected: EmbeddedPythonWheelSelectionResultV1.Selected?,
    ): Int = requireNotNull(selected).priority

    private fun splitTags(value: String): List<String>? {
        val values = value.split('.')
        if (values.isEmpty() || values.any { !tagToken.matches(it) }) return null
        if (values.distinct().size != values.size) return null
        return values
    }

    private fun normalizeDistribution(value: String): String =
        nameNormalizer.replace(value.lowercase(), "-")

    private fun rejected(
        code: EmbeddedPythonWheelSelectionFailureCode,
        detail: String,
    ): EmbeddedPythonWheelSelectionResultV1.Rejected =
        EmbeddedPythonWheelSelectionResultV1.Rejected(code = code, detail = detail)

    private enum class SelectionMode {
        PURE_ONLY,
        ANDROID_ALLOWED,
    }
}
