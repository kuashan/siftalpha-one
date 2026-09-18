package com.siftalpha.studio.siftalphax

data class EmbeddedPythonIndexWheelV1(
    val filename: String,
    val url: String,
    val sha256: String,
    val size: Long?,
    val requiresPython: String?,
    val yanked: Boolean,
)

data class EmbeddedPythonWheelTagsV1(
    val normalizedDistribution: String,
    val version: String,
    val pythonTags: List<String>,
    val abiTags: List<String>,
    val platformTags: List<String>,
)

data class EmbeddedPythonSelectedWheelV1(
    val wheel: EmbeddedPythonIndexWheelV1,
    val tags: EmbeddedPythonWheelTagsV1,
    val nativeAndroid: Boolean,
)

object EmbeddedPythonWheelSelectionV1 {
    private val tagToken = Regex("[A-Za-z0-9_]+")
    private val normalizer = Regex("[-_.]+")
    private val androidPlatformPattern = Regex("android_([0-9]+)_([A-Za-z0-9_]+)")

    fun parseWheelFilename(filename: String): EmbeddedPythonWheelTagsV1? {
        if (!filename.endsWith(".whl")) return null
        val parts = filename.removeSuffix(".whl").split('-')
        if (parts.size !in 5..6 || parts.any { it.isBlank() }) return null
        val pythonTags = splitTags(parts[parts.size - 3]) ?: return null
        val abiTags = splitTags(parts[parts.size - 2]) ?: return null
        val platformTags = splitTags(parts[parts.size - 1]) ?: return null
        return EmbeddedPythonWheelTagsV1(
            normalizedDistribution = normalizer.replace(parts[0].lowercase(), "-"),
            version = parts[1],
            pythonTags = pythonTags,
            abiTags = abiTags,
            platformTags = platformTags,
        )
    }

    fun select(
        normalizedName: String,
        version: String,
        candidates: List<EmbeddedPythonIndexWheelV1>,
        androidApiLevel: Int,
        androidAbi: String = "arm64_v8a",
    ): EmbeddedPythonSelectedWheelV1? {
        val compatible = candidates.mapNotNull { wheel ->
            if (wheel.yanked) return@mapNotNull null
            val tags = parseWheelFilename(wheel.filename) ?: return@mapNotNull null
            if (tags.normalizedDistribution != normalizedName || tags.version != version) {
                return@mapNotNull null
            }
            androidCandidate(wheel, tags, androidApiLevel, androidAbi)
                ?: pureCandidate(wheel, tags)
        }
        return compatible.sortedWith(
            compareBy<Ranked>({ it.rank }, { it.selected.wheel.filename }),
        ).firstOrNull()?.selected
    }

    private fun pureCandidate(
        wheel: EmbeddedPythonIndexWheelV1,
        tags: EmbeddedPythonWheelTagsV1,
    ): Ranked? {
        if (tags.abiTags != listOf("none") || tags.platformTags != listOf("any")) return null
        val rank = when {
            "cp314" in tags.pythonTags -> 20
            "py314" in tags.pythonTags -> 21
            "py3" in tags.pythonTags -> 22
            else -> return null
        }
        return Ranked(rank, EmbeddedPythonSelectedWheelV1(wheel, tags, nativeAndroid = false))
    }

    private fun androidCandidate(
        wheel: EmbeddedPythonIndexWheelV1,
        tags: EmbeddedPythonWheelTagsV1,
        androidApiLevel: Int,
        androidAbi: String,
    ): Ranked? {
        val compatibleApi = tags.platformTags.mapNotNull { tag ->
            val match = androidPlatformPattern.matchEntire(tag) ?: return@mapNotNull null
            val api = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val abi = match.groupValues[2]
            api.takeIf { abi == androidAbi && api <= androidApiLevel }
        }.maxOrNull() ?: return null

        val rank = when {
            "cp314" in tags.pythonTags && "cp314" in tags.abiTags -> 0
            tags.abiTags == listOf("abi3") && tags.pythonTags.any(::abi3CompatibleTag) -> 1
            "py314" in tags.pythonTags && tags.abiTags == listOf("none") -> 2
            "py3" in tags.pythonTags && tags.abiTags == listOf("none") -> 3
            else -> return null
        }
        return Ranked(
            rank + (androidApiLevel - compatibleApi),
            EmbeddedPythonSelectedWheelV1(wheel, tags, nativeAndroid = true),
        )
    }

    private fun abi3CompatibleTag(tag: String): Boolean {
        val match = Regex("cp3([0-9]{1,2})").matchEntire(tag) ?: return false
        val minor = match.groupValues[1].toIntOrNull() ?: return false
        return minor <= 14
    }

    private fun splitTags(value: String): List<String>? {
        val values = value.split('.')
        if (values.isEmpty() || values.any { !tagToken.matches(it) }) return null
        return values.takeIf { it.distinct().size == it.size }
    }

    private data class Ranked(
        val rank: Int,
        val selected: EmbeddedPythonSelectedWheelV1,
    )
}
