package com.siftalpha.studio.siftalphax

/**
 * Pure-Python Environment v1 wheel compatibility and deterministic selection.
 *
 * This layer only selects from wheel candidates already present in an accepted pylock document.
 * It performs no network access, dependency resolution, extraction, installation, or import.
 */
enum class EmbeddedPythonWheelSelectionFailureCode {
    INVALID_WHEEL_FILENAME,
    WHEEL_IDENTITY_MISMATCH,
    NO_SUPPORTED_PURE_PYTHON_WHEEL,
}

sealed interface EmbeddedPythonWheelSelectionResultV1 {
    data class Selected(
        val wheel: EmbeddedPythonPylockV1Wheel,
        val tags: EmbeddedPythonWheelTagsV1,
        val priority: Int,
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

    /**
     * v1 intentionally supports only pure-Python wheels for CPython 3.14:
     *
     *   cp314-none-any > py314-none-any > py3-none-any
     *
     * Android-native wheels are detected as non-matching and remain a later capability.
     */
    fun select(
        pkg: EmbeddedPythonPylockV1Package,
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

            val priority = compatibilityPriority(tags) ?: return@forEach
            compatible += EmbeddedPythonWheelSelectionResultV1.Selected(
                wheel = wheel,
                tags = tags,
                priority = priority,
            )
        }

        return compatible
            .sortedWith(compareBy({ it.priority }, { it.wheel.filename }))
            .firstOrNull()
            ?: rejected(
                EmbeddedPythonWheelSelectionFailureCode.NO_SUPPORTED_PURE_PYTHON_WHEEL,
                "no py3/py314/cp314 none-any wheel for " +
                    pkg.normalizedName + "==" + pkg.version,
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

    private fun compatibilityPriority(tags: EmbeddedPythonWheelTagsV1): Int? {
        if (tags.abiTags != listOf("none")) return null
        if (tags.platformTags != listOf("any")) return null

        return when {
            "cp314" in tags.pythonTags -> 0
            "py314" in tags.pythonTags -> 1
            "py3" in tags.pythonTags -> 2
            else -> null
        }
    }

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
}
