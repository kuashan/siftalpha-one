package com.siftalpha.macos

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

data class MacProjectConfigurationRequirement(
    val name: String,
    val sensitive: Boolean,
)

internal object MacProjectConfigurationDiscovery {
    private const val MAX_ENV_BYTES = 2L * 1024L * 1024L
    private val assignment = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$""")
    private val envName = Regex("""\b([A-Z][A-Z0-9_]{2,})\b""")
    private val notSet = Regex("""\b([A-Z][A-Z0-9_]{2,})\b\s+is\s+not\s+set\b""", RegexOption.IGNORE_CASE)
    private val missingNamed = Regex(
        """\b(?:missing|required)\b[^\n]{0,120}?\b([A-Z][A-Z0-9_]{2,})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val checkNamed = Regex("""\bcheck\s+([A-Z][A-Z0-9_]{2,})\b""", RegexOption.IGNORE_CASE)

    fun discover(
        projectRootPath: String,
        configuredKeys: Set<String>,
        runtimeLog: String,
    ): List<MacProjectConfigurationRequirement> {
        val root = runCatching { File(projectRootPath).canonicalFile }.getOrNull()
            ?: return emptyList()
        if (!root.isDirectory) return emptyList()

        val template = readRegular(root, ".env.example")
        val current = readRegular(root, ".env")
        val declared = parseAssignments(template).keys + parseAssignments(current).keys
        if (declared.isEmpty()) return emptyList()

        val values = parseAssignments(current)
        val normalizedConfigured = configuredKeys.map(String::trim).toSet()
        return missingFromRuntime(runtimeLog)
            .asSequence()
            .filter { it in declared }
            .filter { key -> values[key].isNullOrBlank() && key !in normalizedConfigured }
            .distinct()
            .sorted()
            .map { key ->
                MacProjectConfigurationRequirement(
                    name = key,
                    sensitive = isSensitive(key),
                )
            }
            .toList()
    }

    fun templateKeys(projectRootPath: String): List<String> {
        val root = runCatching { File(projectRootPath).canonicalFile }.getOrNull()
            ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        return parseAssignments(readRegular(root, ".env.example")).keys.sorted()
    }

    internal fun parseAssignments(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val match = assignment.matchEntire(raw) ?: return@forEach
            val name = match.groupValues[1]
            var value = match.groupValues[2].trim()
            if (
                value.length >= 2 &&
                ((value.startsWith('"') && value.endsWith('"')) ||
                    (value.startsWith("'") && value.endsWith("'")))
            ) {
                value = value.substring(1, value.length - 1)
            } else {
                value = value.substringBefore(" #").trim()
            }
            result[name] = value
        }
        return result
    }

    internal fun missingFromRuntime(runtimeLog: String): List<String> {
        if (runtimeLog.isBlank()) return emptyList()
        val keys = linkedSetOf<String>()
        runtimeLog.lineSequence().forEach { rawLine ->
            val line = stripAnsi(rawLine).trim()
            if (line.isBlank()) return@forEach
            if (
                line.contains("Defaulting to a blank string", ignoreCase = true) ||
                line.contains("level=warning", ignoreCase = true) &&
                line.contains("variable is not set", ignoreCase = true)
            ) {
                return@forEach
            }

            notSet.findAll(line).forEach { match ->
                match.groupValues.getOrNull(1)?.uppercase()?.let(keys::add)
            }
            missingNamed.findAll(line).forEach { match ->
                match.groupValues.getOrNull(1)?.uppercase()?.let(keys::add)
            }
            if (
                line.contains("licence", ignoreCase = true) ||
                line.contains("license", ignoreCase = true) ||
                line.contains("configuration", ignoreCase = true) ||
                line.contains("credential", ignoreCase = true)
            ) {
                checkNamed.findAll(line).forEach { match ->
                    match.groupValues.getOrNull(1)?.uppercase()?.let(keys::add)
                }
            }
        }
        return keys.filter { envName.matches(it) }
    }

    internal fun isSensitive(name: String): Boolean {
        val upper = name.uppercase()
        return listOf(
            "KEY",
            "TOKEN",
            "SECRET",
            "PASSWORD",
            "PASSWD",
            "CREDENTIAL",
        ).any { marker -> upper.contains(marker) }
    }

    private fun readRegular(root: File, name: String): String? {
        val file = File(root, name)
        if (!file.exists()) return null
        val path = file.toPath()
        if (
            Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            file.length() > MAX_ENV_BYTES
        ) {
            return null
        }
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!canonical.toPath().startsWith(root.toPath())) return null
        return runCatching { canonical.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun stripAnsi(value: String): String =
        value.replace(Regex("""\u001B\[[;\d]*[ -/]*[@-~]"""), "")
}
