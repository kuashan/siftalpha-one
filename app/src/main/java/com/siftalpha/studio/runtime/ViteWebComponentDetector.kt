package com.siftalpha.studio.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One source of truth for Vite Web-component evidence.
 *
 * A component is anchored by package.json. Vite may be proven by an adjacent vite.config.* file,
 * an exact vite dependency/devDependency, or a package script that invokes the vite executable.
 * This intentionally supports config-less Vite projects while avoiding substring guesses such as
 * "vitest".
 */
data class ViteWebComponentEvidence(
    val directory: String,
    val packageJsonPath: String,
    val hasViteConfig: Boolean,
    val hasViteDependency: Boolean,
    val hasViteScript: Boolean,
) {
    val evidenceLabels: List<String>
        get() = buildList {
            add("package_json")
            if (hasViteDependency) add("vite_dependency")
            if (hasViteScript) add("vite_script")
            if (hasViteConfig) add("vite_config")
        }
}

object ViteWebComponentDetector {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val viteConfigs = setOf(
        "vite.config.ts",
        "vite.config.js",
        "vite.config.mts",
        "vite.config.mjs",
        "vite.config.cjs",
    )
    private val viteCommand = Regex(
        """(?:^|[\s;&|])(?:(?:npx|pnpm\s+exec|npm\s+exec)\s+)?vite(?:\s|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun detect(
        relativePaths: Collection<String>,
        packageJsonTexts: Map<String, String> = emptyMap(),
    ): List<ViteWebComponentEvidence> {
        val normalized = relativePaths
            .asSequence()
            .map(::normalizePath)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val lowerPaths = normalized.mapTo(hashSetOf()) { it.lowercase() }
        val textByLowerPath = packageJsonTexts.entries.associate { (path, text) ->
            normalizePath(path).lowercase() to text
        }

        return normalized
            .asSequence()
            .filter { it.substringAfterLast('/').equals("package.json", ignoreCase = true) }
            .mapNotNull { packageJsonPath ->
                val directory = packageJsonPath.substringBeforeLast('/', "")
                val prefix = if (directory.isBlank()) "" else "$directory/"
                val hasConfig = viteConfigs.any { config ->
                    (prefix + config).lowercase() in lowerPaths
                }
                val parsed = textByLowerPath[packageJsonPath.lowercase()]
                    ?.let(::parsePackageJson)
                val hasDependency = parsed?.let(::hasViteDependency) == true
                val hasScript = parsed?.let(::hasViteScript) == true
                if (!hasConfig && !hasDependency && !hasScript) return@mapNotNull null

                ViteWebComponentEvidence(
                    directory = directory.ifBlank { "." },
                    packageJsonPath = packageJsonPath,
                    hasViteConfig = hasConfig,
                    hasViteDependency = hasDependency,
                    hasViteScript = hasScript,
                )
            }
            .sortedBy { it.directory.lowercase() }
            .toList()
    }

    private fun parsePackageJson(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun hasViteDependency(root: JsonObject): Boolean =
        sequenceOf("dependencies", "devDependencies", "peerDependencies")
            .mapNotNull { key -> root[key] as? JsonObject }
            .any { dependencies -> dependencies.keys.any { it.equals("vite", ignoreCase = true) } }

    private fun hasViteScript(root: JsonObject): Boolean {
        val scripts = root["scripts"] as? JsonObject ?: return false
        return scripts.values.any { value ->
            runCatching { value.jsonPrimitive.content }.getOrNull()
                ?.let(viteCommand::containsMatchIn) == true
        }
    }

    private fun normalizePath(raw: String): String =
        raw.replace('\\', '/').trim().trim('/')
}
