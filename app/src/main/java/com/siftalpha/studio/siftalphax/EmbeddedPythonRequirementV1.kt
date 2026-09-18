package com.siftalpha.studio.siftalphax

import java.security.MessageDigest
import org.tomlj.Toml

data class EmbeddedPythonRequirementV1(
    val name: String,
    val normalizedName: String,
    val specifier: String,
    val extras: Set<String>,
    val marker: String?,
)

data class EmbeddedPythonDependencyInputV1(
    val sourceKind: SourceKind,
    val requirements: List<EmbeddedPythonRequirementV1>,
    val projectRequiresPython: String?,
    val sourceFingerprint: String,
) {
    enum class SourceKind {
        REQUIREMENTS_TXT,
        PYPROJECT_TOML,
        NONE,
    }
}

object EmbeddedPythonRequirementParserV1 {
    private val namePattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")
    private val normalizer = Regex("[-_.]+")
    private val requirementPattern =
        Regex("^([A-Za-z0-9][A-Za-z0-9._-]*)(?:\\[([^]]+)])?\\s*(.*)$")
    private val specifierPattern =
        Regex("^(~=|==|!=|>=|<=|>|<)\\s*([0-9]+(?:\\.[0-9]+)*(?:\\.post[0-9]+)?)(\\.\\*)?$")
    private const val MAX_REQUIREMENTS = 512
    private const val MAX_TEXT_BYTES = 512 * 1024

    fun fromProjectFiles(
        requirementsText: String?,
        pyprojectText: String?,
    ): EmbeddedPythonDependencyInputV1 {
        val pyproject = pyprojectText?.let(::parsePyprojectMetadata)
        val sourceKind: EmbeddedPythonDependencyInputV1.SourceKind
        val requirements: List<EmbeddedPythonRequirementV1>
        val canonicalSource: String
        if (requirementsText != null) {
            require(requirementsText.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) {
                "requirements.txt exceeds internal environment limit"
            }
            sourceKind = EmbeddedPythonDependencyInputV1.SourceKind.REQUIREMENTS_TXT
            requirements = parseRequirements(requirementsText)
            canonicalSource = "requirements.txt\n" + normalizeNewlines(requirementsText) +
                "\npyproject-requires-python\n" + pyproject?.requiresPython.orEmpty()
        } else if (pyproject?.projectTablePresent == true) {
            require(!pyproject.dynamicDependencies) {
                "dynamic project dependencies are not supported by Internal Python preparation"
            }
            sourceKind = EmbeddedPythonDependencyInputV1.SourceKind.PYPROJECT_TOML
            requirements = pyproject.dependencies.map(::parseRequirement)
            canonicalSource = "pyproject.toml\n" + normalizeNewlines(pyprojectText.orEmpty())
        } else if (pyprojectText != null) {
            error("pyproject.toml without a [project] table is not a supported runtime dependency source")
        } else {
            sourceKind = EmbeddedPythonDependencyInputV1.SourceKind.NONE
            requirements = emptyList()
            canonicalSource = "none\n"
        }
        require(requirements.size <= MAX_REQUIREMENTS) {
            "dependency declaration exceeds $MAX_REQUIREMENTS requirements"
        }
        return EmbeddedPythonDependencyInputV1(
            sourceKind = sourceKind,
            requirements = requirements,
            projectRequiresPython = pyproject?.requiresPython,
            sourceFingerprint = "sha256:" + sha256Hex(canonicalSource.toByteArray(Charsets.UTF_8)),
        )
    }

    fun parseRequirements(text: String): List<EmbeddedPythonRequirementV1> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { stripInlineComment(it) }
            .filter { it.isNotBlank() }
            .map { line ->
                require(!line.startsWith("-")) {
                    "requirements options and nested requirement files are not supported: $line"
                }
                parseRequirement(line)
            }
            .toList()

    fun parseRequirement(raw: String): EmbeddedPythonRequirementV1 {
        require(raw.toByteArray(Charsets.UTF_8).size <= 8 * 1024) {
            "requirement is too long"
        }
        val sections = raw.split(';', limit = 2)
        val left = sections[0].trim()
        val marker = sections.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        require('@' !in left) { "direct URL requirements are not supported: $raw" }
        val match = requirementPattern.matchEntire(left)
            ?: error("unsupported requirement syntax: $raw")
        val name = match.groupValues[1]
        require(namePattern.matches(name)) { "invalid distribution name: $name" }
        val extras = match.groupValues[2]
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        extras.forEach { extra ->
            require(extra.matches(Regex("[a-z0-9][a-z0-9._-]*"))) {
                "invalid requirement extra: $extra"
            }
        }
        val rawSpecifier = match.groupValues[3].trim()
        val specifier = if (rawSpecifier.startsWith("(") && rawSpecifier.endsWith(")")) {
            rawSpecifier.substring(1, rawSpecifier.length - 1).trim()
        } else {
            rawSpecifier
        }
        validateSpecifierSet(specifier)
        return EmbeddedPythonRequirementV1(
            name = name,
            normalizedName = normalizeName(name),
            specifier = specifier,
            extras = extras,
            marker = marker,
        )
    }

    fun versionMatches(specifier: String, version: String): Boolean {
        if (specifier.isBlank()) return StableVersion.parse(version) != null
        val candidate = StableVersion.parse(version) ?: return false
        return specifier.split(',').map { it.trim() }.filter { it.isNotEmpty() }.all { clause ->
            val match = specifierPattern.matchEntire(clause) ?: return false
            val operator = match.groupValues[1]
            val base = StableVersion.parse(match.groupValues[2]) ?: return false
            val wildcard = match.groupValues[3].isNotEmpty()
            when (operator) {
                "==" -> if (wildcard) candidate.startsWith(base) else candidate == base
                "!=" -> if (wildcard) !candidate.startsWith(base) else candidate != base
                ">=" -> candidate >= base
                "<=" -> candidate <= base
                ">" -> candidate > base
                "<" -> candidate < base
                "~=" -> {
                    val upper = base.compatibleUpperBound() ?: return false
                    candidate >= base && candidate < upper
                }
                else -> false
            }
        }
    }

    fun compareVersions(left: String, right: String): Int {
        val l = StableVersion.parse(left) ?: return -1
        val r = StableVersion.parse(right) ?: return 1
        return l.compareTo(r)
    }

    private fun parsePyprojectMetadata(text: String): PyprojectMetadata {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) {
            "pyproject.toml exceeds internal environment limit"
        }
        val parsed = Toml.parse(text)
        require(!parsed.hasErrors()) {
            parsed.errors().take(3).joinToString(" | ") { it.toString() }
        }
        val project = parsed.getTable("project")
            ?: return PyprojectMetadata(
                dependencies = emptyList(),
                requiresPython = null,
                projectTablePresent = false,
                dynamicDependencies = false,
            )
        val dynamicDependencies = project.getArray("dynamic")?.let { dynamic ->
            (0 until dynamic.size()).any { index ->
                dynamic.get(index) == "dependencies"
            }
        } ?: false
        val dependencies = if (project.contains("dependencies")) {
            require(project.isArray("dependencies")) { "project.dependencies must be an array" }
            val array = requireNotNull(project.getArray("dependencies"))
            buildList(array.size()) {
                for (index in 0 until array.size()) {
                    val value = array.get(index) as? String
                        ?: error("project.dependencies[$index] must be a string")
                    add(value)
                }
            }
        } else {
            emptyList()
        }
        val requiresPython = project.getString("requires-python")?.trim()?.takeIf { it.isNotEmpty() }
        return PyprojectMetadata(
            dependencies = dependencies,
            requiresPython = requiresPython,
            projectTablePresent = true,
            dynamicDependencies = dynamicDependencies,
        )
    }

    private fun validateSpecifierSet(specifier: String) {
        if (specifier.isBlank()) return
        specifier.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { clause ->
            require(specifierPattern.matches(clause)) {
                "unsupported version specifier: $clause"
            }
        }
    }

    private fun stripInlineComment(line: String): String {
        val index = line.indexOf(" #")
        return if (index >= 0) line.substring(0, index).trimEnd() else line
    }

    private fun normalizeName(value: String): String =
        normalizer.replace(value.lowercase(), "-")

    private fun normalizeNewlines(value: String): String =
        value.replace("\r\n", "\n").replace("\r", "\n")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class PyprojectMetadata(
        val dependencies: List<String>,
        val requiresPython: String?,
        val projectTablePresent: Boolean,
        val dynamicDependencies: Boolean,
    )

    private data class StableVersion(
        val release: List<Int>,
        val post: Int?,
    ) : Comparable<StableVersion> {
        override fun compareTo(other: StableVersion): Int {
            val size = maxOf(release.size, other.release.size)
            for (index in 0 until size) {
                val l = release.getOrElse(index) { 0 }
                val r = other.release.getOrElse(index) { 0 }
                if (l != r) return l.compareTo(r)
            }
            return (post ?: -1).compareTo(other.post ?: -1)
        }

        fun startsWith(prefix: StableVersion): Boolean =
            prefix.release.indices.all { release.getOrNull(it) == prefix.release[it] }

        fun compatibleUpperBound(): StableVersion? {
            if (release.size < 2) return null
            val pivot = release.size - 2
            val upper = release.take(pivot + 1).toMutableList()
            upper[pivot] = upper[pivot] + 1
            return StableVersion(upper, null)
        }

        companion object {
            private val pattern = Regex("^([0-9]+(?:\\.[0-9]+)*)(?:\\.post([0-9]+))?$")
            fun parse(value: String): StableVersion? {
                val match = pattern.matchEntire(value.trim()) ?: return null
                val release = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
                val post = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
                return StableVersion(release, post)
            }
        }
    }
}
