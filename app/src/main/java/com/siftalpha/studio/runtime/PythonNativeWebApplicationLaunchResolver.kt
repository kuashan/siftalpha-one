package com.siftalpha.studio.runtime

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

data class PythonNativeWebLaunchCandidate(
    val executableName: String,
    val arguments: List<String>,
    val evidencePath: String,
) {
    fun toInvocation(): PythonLaunchInvocation =
        PythonLaunchInvocation.consoleScript(executableName, arguments)

    fun displayCommand(): String =
        (listOf(executableName) + arguments).joinToString(" ")
}

/**
 * High-confidence discovery for project-owned Python Web applications.
 *
 * This policy deliberately fails closed. It never guesses from a package name. Automatic native-Web
 * launch is allowed only when project metadata, frontend build evidence and a literal server command
 * all agree. Projects outside this narrow contract fall back to the accepted CLI launch path.
 */
object PythonNativeWebApplicationLaunchResolver {
    private val serveCommandDecorators = listOf(
        Regex("""(?m)@\s*click\.command\s*\(\s*[\"']serve[\"']"""),
        Regex("""(?m)@\s*[A-Za-z_][A-Za-z0-9_]*\.command\s*\(\s*[\"']serve[\"']"""),
        Regex("""(?m)\badd_parser\s*\(\s*[\"']serve[\"']"""),
    )
    private val viteConfigs = setOf(
        "vite.config.ts",
        "vite.config.js",
        "vite.config.mts",
        "vite.config.mjs",
        "vite.config.cjs",
    )
    private val browserDisableFlags = listOf(
        "--no-open-browser",
        "--no-browser",
        "--headless",
    )

    fun resolve(
        declaredRun: String?,
        pyprojectToml: String?,
        relativePaths: Collection<String>,
        pythonSources: Map<String, String>,
        webProjectEnabled: Boolean,
    ): PythonNativeWebLaunchCandidate? {
        if (!webProjectEnabled || !declaredRun.isNullOrBlank() || pyprojectToml.isNullOrBlank()) {
            return null
        }

        val root = runCatching { Toml.parse(pyprojectToml) }.getOrNull() ?: return null
        if (root.hasErrors()) return null
        val project = root.get("project") as? TomlTable ?: return null
        val optional = project.get("optional-dependencies") as? TomlTable ?: return null
        val webExtra = optional.get("web") as? TomlArray ?: return null
        if (webExtra.size() == 0) return null

        val scripts = project.get("scripts") as? TomlTable ?: return null
        val scriptEntries = scripts.keySet()
            .asSequence()
            .sorted()
            .mapNotNull { name ->
                val target = scripts.get(name)
                if (
                    target is String &&
                    target.isNotBlank() &&
                    PythonCliLaunchResolver.isSafeCommandName(name)
                ) {
                    name
                } else {
                    null
                }
            }
            .toList()
        if (scriptEntries.isEmpty()) return null

        val projectName = (project.get("name") as? String).orEmpty()
        val executable = when {
            scriptEntries.size == 1 -> scriptEntries.single()
            projectName.isNotBlank() -> {
                val wanted = canonicalCommandName(projectName)
                scriptEntries.singleOrNull { canonicalCommandName(it) == wanted } ?: return null
            }
            else -> return null
        }

        if (!hasViteComponent(relativePaths)) return null

        val sourceEvidence = pythonSources.entries.firstNotNullOfOrNull { (path, source) ->
            inspectServeSource(path, source)
        } ?: return null

        val arguments = buildList {
            add("serve")
            if (sourceEvidence.supportsHost) {
                add("--host")
                add("127.0.0.1")
            }
            add(sourceEvidence.browserDisableFlag)
        }
        return PythonNativeWebLaunchCandidate(
            executableName = executable,
            arguments = arguments,
            evidencePath = sourceEvidence.path,
        )
    }

    private data class ServeSourceEvidence(
        val path: String,
        val supportsHost: Boolean,
        val browserDisableFlag: String,
    )

    private fun inspectServeSource(path: String, source: String): ServeSourceEvidence? {
        if (serveCommandDecorators.none { it.containsMatchIn(source) }) return null
        val browserFlag = browserDisableFlags.firstOrNull { it in source } ?: return null
        return ServeSourceEvidence(
            path = path,
            supportsHost = "--host" in source,
            browserDisableFlag = browserFlag,
        )
    }

    private fun hasViteComponent(relativePaths: Collection<String>): Boolean {
        val normalized = relativePaths
            .asSequence()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() }
            .toSet()
        return normalized.any { path ->
            val name = path.substringAfterLast('/').lowercase()
            if (name !in viteConfigs) return@any false
            val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
            val packageJson = if (parent.isBlank()) "package.json" else "$parent/package.json"
            packageJson in normalized
        }
    }

    private fun canonicalCommandName(value: String): String =
        value.trim().lowercase().replace('_', '-')
}
