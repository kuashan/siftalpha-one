package com.siftalpha.studio.project

/**
 * High-confidence fast-path recognition for common Web project shapes.
 *
 * A single dependency name is never enough. Fast-path matches require at least two independent
 * pieces of project-owned evidence. Lower-confidence projects fall through to the existing bounded
 * deep inspection path.
 */
object CommonWebSignatureRegistry {

    enum class Confidence {
        HIGH,
        MEDIUM,
    }

    data class Match(
        val framework: String,
        val confidence: Confidence,
        val defaultPort: Int?,
        val evidence: List<String>,
    )

    fun detectPython(
        requirements: String?,
        pyproject: String?,
        relativePaths: Collection<String> = emptyList(),
        pythonSources: Map<String, String> = emptyMap(),
        runCommand: String? = null,
    ): Match? {
        val dependencyText = buildString {
            if (!requirements.isNullOrBlank()) appendLine(requirements)
            if (!pyproject.isNullOrBlank()) appendLine(pyproject)
        }
        val paths = relativePaths
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() }
            .toSet()
        val sources = pythonSources.values.joinToString("\n")
        val run = runCommand.orEmpty()

        fun dep(name: String): Boolean =
            Regex("(?i)(^|[\\s\\\"'])" + Regex.escape(name) + "(?:\\[[^]]+])?(?=\\s|[<>=~!;,\\\"']|$)")
                .containsMatchIn(dependencyText)

        fun source(regex: String): Boolean = Regex(regex).containsMatchIn(sources)
        fun runMatches(regex: String): Boolean = Regex(regex).containsMatchIn(run)
        fun hasName(name: String): Boolean = paths.any { it.substringAfterLast('/').equals(name, true) }

        val hasViteHybrid = paths.any {
            it.substringAfterLast('/').lowercase() in setOf(
                "vite.config.ts", "vite.config.js", "vite.config.mts",
                "vite.config.mjs", "vite.config.cjs",
            )
        }
        val hasProjectScriptsHybrid = pyproject.orEmpty().contains("[project.scripts]")
        val hasWebExtraHybrid = Regex("""(?is)\[project\.optional-dependencies]\s*[\s\S]*?\bweb\s*=""")
            .containsMatchIn(pyproject.orEmpty())
        val hasWebSubcommandHybrid = Regex(
            """(?is)(?:@\s*[A-Za-z_][A-Za-z0-9_.]*\.command\s*\(\s*["'](?:serve|web|ui|dashboard)["']|add_parser\s*\(\s*["'](?:serve|web|ui|dashboard)["'])""",
        ).containsMatchIn(sources)
        if (
            hasViteHybrid &&
            hasProjectScriptsHybrid &&
            hasWebExtraHybrid &&
            hasWebSubcommandHybrid
        ) {
            return Match(
                "python-vite-web",
                Confidence.HIGH,
                null,
                listOf("project-script", "web-extra", "vite", "web-subcommand"),
            )
        }

        val matches = buildList {
            if (
                dep("streamlit") &&
                (source("""(?im)^\s*(?:import\s+streamlit\b|from\s+streamlit\b)""") ||
                    hasName("streamlit_app.py") ||
                    runMatches("""(?i)(^|\s)streamlit\s+run(?:\s|$)"""))
            ) {
                add(Match("streamlit", Confidence.HIGH, 8501, listOf("dependency", "streamlit-entry")))
            }
            if (
                dep("gradio") &&
                source("""(?is)(?:\bgradio\b|\bgr\.)[\s\S]{0,1200}?(?:Interface|Blocks)\s*\(|\.launch\s*\(""")
            ) {
                add(Match("gradio", Confidence.HIGH, 7860, listOf("dependency", "launch-source")))
            }
            if (
                dep("nicegui") &&
                source("""(?is)(?:from\s+nicegui\s+import\s+ui|import\s+nicegui)[\s\S]{0,2000}?\bui\.run\s*\(""")
            ) {
                add(Match("nicegui", Confidence.HIGH, 8080, listOf("dependency", "ui.run")))
            }
            if (
                dep("dash") &&
                source("""(?is)\bDash\s*\([\s\S]{0,3000}?\b(?:run|run_server)\s*\(""")
            ) {
                add(Match("dash", Confidence.HIGH, 8050, listOf("dependency", "dash-run")))
            }
            if (
                dep("fastapi") &&
                (
                    source("""(?is)\bFastAPI\s*\(""") &&
                        (dep("uvicorn") || source("""(?is)\buvicorn(?:\.run)?\b"""))
                    || runMatches("""(?i)(^|\s)(?:uvicorn|fastapi\s+run)(?:\s|$)""")
                )
            ) {
                add(Match("fastapi", Confidence.HIGH, 8000, listOf("dependency", "asgi-entry")))
            }
            if (
                dep("flask") &&
                (
                    source("""(?is)\bFlask\s*\(""") &&
                        source("""(?is)\.(?:run)\s*\(""")
                    || runMatches("""(?i)(^|\s)flask(?:\s|$)""")
                )
            ) {
                add(Match("flask", Confidence.HIGH, 5000, listOf("dependency", "flask-entry")))
            }
            if (
                dep("django") &&
                hasName("manage.py") &&
                paths.any { it.endsWith("/settings.py", true) || it.equals("settings.py", true) } &&
                paths.any { it.endsWith("/urls.py", true) || it.equals("urls.py", true) }
            ) {
                add(Match("django", Confidence.HIGH, 8000, listOf("dependency", "manage.py", "settings+urls")))
            }
            if (
                dep("panel") &&
                source("""(?is)(?:\bpn\.serve\s*\(|\.servable\s*\()""")
            ) {
                add(Match("panel", Confidence.HIGH, 5006, listOf("dependency", "panel-serve")))
            }
            if (
                dep("bokeh") &&
                (
                    source("""(?is)\bcurdoc\s*\(""") ||
                        runMatches("""(?i)(^|\s)bokeh\s+serve(?:\s|$)""")
                )
            ) {
                add(Match("bokeh", Confidence.HIGH, 5006, listOf("dependency", "bokeh-app")))
            }
            if (
                dep("aiohttp") &&
                source("""(?is)\bweb\.Application\s*\([\s\S]{0,3000}?\bweb\.run_app\s*\(""")
            ) {
                add(Match("aiohttp", Confidence.HIGH, 8080, listOf("dependency", "run_app")))
            }
            if (
                dep("tornado") &&
                source("""(?is)\bApplication\s*\([\s\S]{0,3000}?\.listen\s*\(""")
            ) {
                add(Match("tornado", Confidence.HIGH, 8888, listOf("dependency", "listen")))
            }

        }

        return matches.firstOrNull()
    }

    fun detectNode(
        dependencies: Set<String>,
        packageStartCommand: String?,
        relativePaths: Collection<String> = emptyList(),
        nodeSources: List<String> = emptyList(),
        declaredRun: String? = null,
    ): Match? {
        val deps = dependencies.map { it.trim().lowercase() }.toSet()
        val paths = relativePaths
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() }
            .toSet()
        val sources = nodeSources.joinToString("\n")
        val run = declaredRun?.takeIf { it.isNotBlank() } ?: packageStartCommand.orEmpty()

        fun hasDep(vararg names: String): Boolean = names.any { it.lowercase() in deps }
        fun source(regex: String): Boolean = Regex(regex).containsMatchIn(sources)
        fun runMatches(regex: String): Boolean = Regex(regex).containsMatchIn(run)

        return when {
            hasDep("next") && runMatches("""(?i)(^|[\s;&|])next(?:\s+(?:dev|start))?(?:\s|$)""") ->
                Match("next", Confidence.HIGH, 3000, listOf("dependency", "package-script"))
            hasDep("nuxt", "nuxt3") && runMatches("""(?i)(^|[\s;&|])(?:nuxi|nuxt)(?:\s|$)""") ->
                Match("nuxt", Confidence.HIGH, 3000, listOf("dependency", "package-script"))
            hasDep("vite", "@vitejs/plugin-react", "@vitejs/plugin-vue") &&
                paths.any { it.substringAfterLast('/').lowercase().startsWith("vite.config.") } &&
                runMatches("""(?i)(^|[\s;&|])vite(?:\s|$)""") ->
                Match("vite", Confidence.HIGH, 5173, listOf("dependency", "vite-config", "package-script"))
            hasDep("express") &&
                source("""(?is)(?:require\(\s*["']express["']\s*\)|from\s+["']express["'])""") &&
                source("""(?is)\.listen\s*\(""") ->
                Match("express", Confidence.HIGH, null, listOf("dependency", "listen-source"))
            hasDep("fastify") &&
                source("""(?is)(?:require\(\s*["']fastify["']\s*\)|from\s+["']fastify["'])""") &&
                source("""(?is)\.listen\s*\(""") ->
                Match("fastify", Confidence.HIGH, null, listOf("dependency", "listen-source"))
            hasDep("koa") &&
                source("""(?is)(?:require\(\s*["']koa["']\s*\)|from\s+["']koa["'])""") &&
                source("""(?is)\.listen\s*\(""") ->
                Match("koa", Confidence.HIGH, null, listOf("dependency", "listen-source"))
            else -> null
        }
    }
}
