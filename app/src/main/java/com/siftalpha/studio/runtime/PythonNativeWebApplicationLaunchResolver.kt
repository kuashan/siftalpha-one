package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.CommonWebSignatureRegistry
import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
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
 * Project-owned Python Web launch discovery.
 *
 * Resolution order:
 * 1. strict project-owned Python+Vite Web launch contract;
 * 2. generic high-confidence framework signatures;
 * 3. null, which preserves the normal CLI fallback.
 *
 * A complete project-owned serve contract is stronger evidence than a framework import or app
 * object in one source file. Generic framework recognition is therefore fallback-only.
 *
 * Static recognition never marks a Web endpoint verified. Runtime Identity, scoped Web Discovery
 * and Endpoint Probe remain the only path to VERIFIED Web presentation.
 */
object PythonNativeWebApplicationLaunchResolver {
    private val serveCommandDecorators = listOf(
        Regex("""(?m)@\s*click\.command\s*\(\s*["']serve["']"""),
        Regex("""(?m)@\s*[A-Za-z_][A-Za-z0-9_]*\.command\s*\(\s*["']serve["']"""),
        Regex("""(?m)\badd_parser\s*\(\s*["']serve["']"""),
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
        requirementsText: String? = null,
    ): PythonNativeWebLaunchCandidate? {
        if (!webProjectEnabled || !declaredRun.isNullOrBlank()) {
            return null
        }

        if (!pyprojectToml.isNullOrBlank()) {
            resolvePythonViteWebLaunch(
                pyprojectToml = pyprojectToml,
                relativePaths = relativePaths,
                pythonSources = pythonSources,
            )?.let { return it }

            // A project-owned console-script contract is stronger launch authority than a generic
            // framework signature. If the strict Web contract cannot be proven, fail closed here
            // and let the normal CLI workflow resolve the declared project command instead of
            // synthesizing (for example) an internal FastAPI module into the main launch.
            if (hasProjectOwnedCliContract(pyprojectToml)) return null
        }

        val pythonSourcesComplete = completePythonSourceSample(
            relativePaths = relativePaths,
            pythonSources = pythonSources,
        )

        return resolveCommonWebLaunch(
            requirementsText = requirementsText,
            pyprojectToml = pyprojectToml,
            relativePaths = relativePaths,
            pythonSources = pythonSources,
            pythonSourcesComplete = pythonSourcesComplete,
        )
    }

    private fun resolveCommonWebLaunch(
        requirementsText: String?,
        pyprojectToml: String?,
        relativePaths: Collection<String>,
        pythonSources: Map<String, String>,
        pythonSourcesComplete: Boolean,
    ): PythonNativeWebLaunchCandidate? {
        val signature = CommonWebSignatureRegistry.detectPython(
            requirements = requirementsText,
            pyproject = pyprojectToml,
            relativePaths = relativePaths,
            pythonSources = pythonSources,
        ) ?: return null

        return when (signature.framework) {
            "streamlit" -> sourceCandidate(
                pythonSources = pythonSources,
                sourceRegex = Regex("""(?im)^\s*(?:import\s+streamlit\b|from\s+streamlit\b)"""),
                preferredNames = listOf("streamlit_app.py", "app.py", "main.py"),
            )?.let { path ->
                PythonNativeWebLaunchCandidate(
                    executableName = "streamlit",
                    arguments = listOf(
                        "run",
                        path,
                        "--server.address",
                        "127.0.0.1",
                        "--server.headless",
                        "true",
                    ),
                    evidencePath = path,
                )
            }

            "fastapi" -> if (pythonSourcesComplete) fastApiCandidate(pythonSources) else null

            "django" -> relativePaths
                .asSequence()
                .map(::normalizedSafePythonPath)
                .filterNotNull()
                .firstOrNull { it.substringAfterLast('/').equals("manage.py", true) }
                ?.let { path ->
                    PythonNativeWebLaunchCandidate(
                        executableName = "python",
                        arguments = listOf(path, "runserver", "127.0.0.1:8000"),
                        evidencePath = path,
                    )
                }

            "flask" -> directPythonServerCandidate(
                pythonSources,
                Regex("""(?is)\bFlask\s*\([\s\S]{0,6000}?\.run\s*\("""),
            )

            "gradio" -> directPythonServerCandidate(
                pythonSources,
                Regex("""(?is)(?:\bgradio\b|\bgr\.)[\s\S]{0,6000}?\.launch\s*\("""),
            )

            "nicegui" -> directPythonServerCandidate(
                pythonSources,
                Regex("""(?is)\bui\.run\s*\("""),
            )

            "dash" -> directPythonServerCandidate(
                pythonSources,
                Regex("""(?is)\bDash\s*\([\s\S]{0,6000}?\.(?:run|run_server)\s*\("""),
            )

            "aiohttp" -> directPythonServerCandidate(
                pythonSources,
                Regex("""(?is)\bweb\.run_app\s*\("""),
            )

            "tornado" -> directPythonServerCandidate(
                pythonSources,
                Regex("""(?is)\.listen\s*\([\s\S]{0,3000}?(?:IOLoop|start)\b"""),
            )

            else -> null
        }
    }

    private fun fastApiCandidate(
        pythonSources: Map<String, String>,
    ): PythonNativeWebLaunchCandidate? {
        val appAssignment = Regex(
            """(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:[A-Za-z_][A-Za-z0-9_]*\.)?FastAPI\s*\(""",
        )
        val matches = pythonSources.entries.mapNotNull { (rawPath, source) ->
            val assignment = appAssignment.find(source) ?: return@mapNotNull null
            val path = normalizedSafePythonPath(rawPath) ?: return@mapNotNull null
            val appName = assignment.groupValues.getOrNull(1) ?: return@mapNotNull null
            Triple(path, appName, source)
        }
        // Generic FastAPI launch synthesis is fallback-only. Ambiguous app ownership must never be
        // converted into an arbitrary "first file wins" launch contract.
        if (matches.size != 1) return null
        val (path, appName, _) = matches.single()
        val module = pythonModuleName(path) ?: return null
        return PythonNativeWebLaunchCandidate(
            executableName = "uvicorn",
            arguments = listOf(
                "$module:$appName",
                "--host",
                "127.0.0.1",
            ),
            evidencePath = path,
        )
    }

    private fun directPythonServerCandidate(
        pythonSources: Map<String, String>,
        sourceRegex: Regex,
    ): PythonNativeWebLaunchCandidate? {
        val path = sourceCandidate(
            pythonSources = pythonSources,
            sourceRegex = sourceRegex,
        ) ?: return null
        return PythonNativeWebLaunchCandidate(
            executableName = "python",
            arguments = listOf(path),
            evidencePath = path,
        )
    }

    private fun sourceCandidate(
        pythonSources: Map<String, String>,
        sourceRegex: Regex,
        preferredNames: List<String> = emptyList(),
    ): String? {
        return pythonSources.entries
            .asSequence()
            .filter { (_, source) -> sourceRegex.containsMatchIn(source) }
            .mapNotNull { (path, _) -> normalizedSafePythonPath(path) }
            .sortedWith(
                compareBy<String> { path ->
                    val index = preferredNames.indexOf(path.substringAfterLast('/').lowercase())
                    if (index >= 0) index else Int.MAX_VALUE
                }.thenBy { it.count { ch -> ch == '/' } }
                    .thenBy { it.lowercase() },
            )
            .firstOrNull()
    }

    private fun normalizedSafePythonPath(path: String): String? {
        val normalized = path.replace('\\', '/').trim().trim('/')
        if (!normalized.endsWith(".py", true)) return null
        return EmbeddedPythonEntrypointPolicy.safeRelativePath(normalized)
    }

    private fun pythonModuleName(path: String): String? {
        val normalized = path
            .removePrefix("src/")
            .removeSuffix(".py")
            .replace('/', '.')
        if (normalized.isBlank()) return null
        if (normalized.split('.').any { !PYTHON_MODULE_SEGMENT.matches(it) }) return null
        return normalized
    }

    private fun resolvePythonViteWebLaunch(
        pyprojectToml: String,
        relativePaths: Collection<String>,
        pythonSources: Map<String, String>,
    ): PythonNativeWebLaunchCandidate? {
        val root = runCatching { Toml.parse(pyprojectToml) }.getOrNull() ?: return null
        if (root.hasErrors()) return null
        val project = root.get("project") as? TomlTable ?: return null
        val optional = project.get("optional-dependencies") as? TomlTable ?: return null
        val webExtra = optional.get("web") as? TomlArray ?: return null
        if (webExtra.size() == 0) return null

        val selectedScript = selectedProjectScript(project) ?: return null

        if (!hasViteComponent(relativePaths)) return null

        val sourceEvidence = pythonSources.entries.firstNotNullOfOrNull { (path, source) ->
            if (!sourceOwnedByScript(path, selectedScript.target)) return@firstNotNullOfOrNull null
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
            executableName = selectedScript.name,
            arguments = arguments,
            evidencePath = sourceEvidence.path,
        )
    }

    private data class ProjectScript(
        val name: String,
        val target: String,
    )

    internal fun selectSourcePaths(
        pyprojectToml: String?,
        relativePaths: Collection<String>,
        maxFiles: Int,
    ): List<String> {
        require(maxFiles > 0) { "源码候选数量上限必须大于零" }
        val ownedPackage = selectedProjectPackage(pyprojectToml)
        return relativePaths
            .asSequence()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.endsWith(".py", ignoreCase = true) }
            .filterNot(::isGeneratedOrDependencyPythonPath)
            .distinct()
            .sortedWith(
                compareBy<String> { path ->
                    if (ownedPackage != null && sourceOwnedByPackage(path, ownedPackage)) 0 else 1
                }.thenBy(::nativeWebSourcePriority)
                    .thenBy { it.count { ch -> ch == '/' } }
                    .thenBy { it.lowercase() },
            )
            .take(maxFiles)
            .toList()
    }

    private fun selectedProjectPackage(pyprojectToml: String?): String? {
        if (pyprojectToml.isNullOrBlank()) return null
        val root = runCatching { Toml.parse(pyprojectToml) }.getOrNull() ?: return null
        if (root.hasErrors()) return null
        val project = root.get("project") as? TomlTable ?: return null
        val target = selectedProjectScript(project)?.target ?: return null
        return target.substringBefore(':').substringBefore('.')
            .takeIf(PYTHON_MODULE_SEGMENT::matches)
    }

    private fun selectedProjectScript(project: TomlTable): ProjectScript? {
        val scripts = project.get("scripts") as? TomlTable ?: return null
        val entries = scripts.keySet()
            .asSequence()
            .sorted()
            .mapNotNull { name ->
                val target = scripts.get(name) as? String ?: return@mapNotNull null
                val normalizedTarget = target.trim()
                if (
                    PythonCliLaunchResolver.isSafeCommandName(name) &&
                    consoleScriptTarget.matches(normalizedTarget)
                ) {
                    ProjectScript(name = name, target = normalizedTarget)
                } else {
                    null
                }
            }
            .toList()
        if (entries.isEmpty()) return null
        if (entries.size == 1) return entries.single()
        val projectName = (project.get("name") as? String).orEmpty()
        if (projectName.isBlank()) return null
        val wanted = canonicalCommandName(projectName)
        return entries.singleOrNull { canonicalCommandName(it.name) == wanted }
    }

    private data class ServeSourceEvidence(
        val path: String,
        val supportsHost: Boolean,
        val browserDisableFlag: String,
    )

    private fun hasProjectOwnedCliContract(pyprojectToml: String): Boolean {
        val root = runCatching { Toml.parse(pyprojectToml) }.getOrNull() ?: return false
        if (root.hasErrors()) return false
        val project = root.get("project") as? TomlTable ?: return false
        val scripts = project.get("scripts") as? TomlTable ?: return false
        return scripts.keySet().any { name ->
            val target = scripts.get(name) as? String
            target != null &&
                PythonCliLaunchResolver.isSafeCommandName(name) &&
                consoleScriptTarget.matches(target.trim())
        }
    }

    private fun sourceOwnedByScript(path: String, scriptTarget: String): Boolean {
        val normalizedTarget = scriptTarget.trim()
        if (!consoleScriptTarget.matches(normalizedTarget)) return false
        val module = normalizedTarget.substringBefore(':')
        val topLevelPackage = module.substringBefore('.')
        if (!PYTHON_MODULE_SEGMENT.matches(topLevelPackage)) return false
        return sourceOwnedByPackage(path, topLevelPackage)
    }

    private fun sourceOwnedByPackage(path: String, topLevelPackage: String): Boolean {
        val normalized = normalizedSafePythonPath(path) ?: return false
        val sourcePath = normalized.removePrefix("src/")
        return sourcePath == "$topLevelPackage.py" || sourcePath.startsWith("$topLevelPackage/")
    }

    private fun completePythonSourceSample(
        relativePaths: Collection<String>,
        pythonSources: Map<String, String>,
    ): Boolean {
        val expected = relativePaths
            .asSequence()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.endsWith(".py", ignoreCase = true) }
            .filterNot(::isGeneratedOrDependencyPythonPath)
            .mapNotNull(::normalizedSafePythonPath)
            .toSet()
        val inspected = pythonSources.keys.mapNotNull(::normalizedSafePythonPath).toSet()
        return expected.all { it in inspected }
    }

    private fun isGeneratedOrDependencyPythonPath(path: String): Boolean =
        path.split('/').any { it.lowercase() in ignoredPythonPathSegments }

    private fun nativeWebSourcePriority(path: String): Int {
        val normalized = path.replace('\\', '/').lowercase()
        val name = normalized.substringAfterLast('/')
        return when {
            name == "cmd_web.py" -> 0
            name in setOf("web.py", "server.py", "serve.py") -> 1
            "/web/" in normalized -> 2
            "/cli/" in normalized && name.startsWith("cmd_") -> 3
            name == "__main__.py" -> 4
            name == "cli.py" -> 5
            name == "app.py" -> 6
            else -> 20
        }
    }

    private fun inspectServeSource(path: String, source: String): ServeSourceEvidence? {
        if (serveCommandDecorators.none { it.containsMatchIn(source) }) return null
        val browserFlag = browserDisableFlags.firstOrNull { it in source } ?: return null
        val safePath = normalizedSafePythonPath(path) ?: return null
        return ServeSourceEvidence(
            path = safePath,
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

    private val PYTHON_MODULE_SEGMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val consoleScriptTarget = Regex(
        "^[A-Za-z_][A-Za-z0-9_.]*:[A-Za-z_][A-Za-z0-9_]*$",
    )
    private val ignoredPythonPathSegments = setOf(
        ".git",
        ".venv",
        "venv",
        "__pycache__",
        "node_modules",
        "dist",
        "build",
        "site-packages",
    )
}
