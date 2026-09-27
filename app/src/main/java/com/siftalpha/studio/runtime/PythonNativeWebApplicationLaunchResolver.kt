package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.CommonWebSignatureRegistry
import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
import org.tomlj.Toml
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
 * 1. project-authored console script + serve contract;
 * 2. common high-confidence Web framework signatures whose runtime capability is prepared;
 * 3. null, which preserves the normal CLI fallback.
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
            resolveProjectOwnedServeLaunch(
                pyprojectToml = pyprojectToml,
                pythonSources = pythonSources,
            )?.let { return it }
        }

        return resolveCommonWebLaunch(
            requirementsText = requirementsText,
            pyprojectToml = pyprojectToml,
            relativePaths = relativePaths,
            pythonSources = pythonSources,
        )
    }

    private fun resolveCommonWebLaunch(
        requirementsText: String?,
        pyprojectToml: String?,
        relativePaths: Collection<String>,
        pythonSources: Map<String, String>,
    ): PythonNativeWebLaunchCandidate? {
        val signature = CommonWebSignatureRegistry.detectPython(
            requirements = requirementsText,
            pyproject = pyprojectToml,
            relativePaths = relativePaths,
            pythonSources = pythonSources,
        ) ?: return null

        val frameworkPackage = when (signature.framework) {
            "streamlit" -> "streamlit"
            "fastapi" -> "fastapi"
            "django" -> "django"
            "flask" -> "flask"
            "gradio" -> "gradio"
            "nicegui" -> "nicegui"
            "dash" -> "dash"
            "aiohttp" -> "aiohttp"
            "tornado" -> "tornado"
            else -> null
        }
        if (
            frameworkPackage != null &&
            !PythonWebEnvironmentCapabilityPolicy.providesPackage(
                packageName = frameworkPackage,
                requirementsText = requirementsText,
                pyprojectToml = pyprojectToml,
            )
        ) {
            return null
        }

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

            "fastapi" -> {
                if (
                    !PythonWebEnvironmentCapabilityPolicy.providesPackage(
                        packageName = "uvicorn",
                        requirementsText = requirementsText,
                        pyprojectToml = pyprojectToml,
                    )
                ) {
                    null
                } else {
                    fastApiCandidate(pythonSources)
                }
            }

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
        val entry = pythonSources.entries.firstOrNull { (_, source) ->
            appAssignment.containsMatchIn(source)
        } ?: return null
        val path = normalizedSafePythonPath(entry.key) ?: return null
        val appName = appAssignment.find(entry.value)?.groupValues?.getOrNull(1) ?: return null
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

    private fun resolveProjectOwnedServeLaunch(
        pyprojectToml: String,
        pythonSources: Map<String, String>,
    ): PythonNativeWebLaunchCandidate? {
        val root = runCatching { Toml.parse(pyprojectToml) }.getOrNull() ?: return null
        if (root.hasErrors()) return null
        val project = root.get("project") as? TomlTable ?: return null
        if (PythonWebEnvironmentCapabilityPolicy.installExtras(pyprojectToml).isEmpty()) return null

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
        val safePath = normalizedSafePythonPath(path) ?: return null
        return ServeSourceEvidence(
            path = safePath,
            supportsHost = "--host" in source,
            browserDisableFlag = browserFlag,
        )
    }

    private fun canonicalCommandName(value: String): String =
        value.trim().lowercase().replace('_', '-')

    private val PYTHON_MODULE_SEGMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
}
