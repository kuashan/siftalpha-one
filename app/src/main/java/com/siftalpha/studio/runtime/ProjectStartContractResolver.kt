package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
import org.json.JSONObject
import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * M-owned, read-only resolver for common project start declarations.
 *
 * Project declarations are only executed after an explicit START action. Card refresh may parse
 * them for presentation/hints but never executes them.
 */
object ProjectStartContractResolver {

    enum class Source {
        DECLARED_PROJECT_RUN,
        RENDER_YAML,
        PROCFILE,
        PACKAGE_START_SCRIPT,
        PYPROJECT_SCRIPT,
        ENTRYPOINT_FALLBACK,
    }

    data class Contract(
        val command: String,
        val source: Source,
        val requiresShell: Boolean,
        val explicitPort: Int? = null,
        val requiresPortEnvironment: Boolean = false,
    )

    fun resolve(
        declaredRun: String?,
        renderYaml: String?,
        procfile: String?,
        packageJson: String?,
        pyprojectToml: String?,
        fallbackEntrypoint: String?,
    ): Contract? {
        declaredRun?.trim()?.takeIf { it.isNotBlank() }?.let { command ->
            val safeEntrypoint = fallbackEntrypoint
                ?.let(EmbeddedPythonEntrypointPolicy::safeRelativePath)
            if (safeEntrypoint != null && isDirectPythonRun(command, safeEntrypoint)) {
                return Contract(
                    command = command,
                    source = Source.DECLARED_PROJECT_RUN,
                    requiresShell = false,
                )
            }
            return commandContract(command, Source.DECLARED_PROJECT_RUN)
        }

        renderStartCommand(renderYaml)?.let { command ->
            return commandContract(command, Source.RENDER_YAML)
        }

        procfileWebCommand(procfile)?.let { command ->
            return commandContract(command, Source.PROCFILE)
        }

        packageStartCommand(packageJson)?.let { script ->
            return commandContract(
                command = "npm start",
                source = Source.PACKAGE_START_SCRIPT,
                portProbeText = script,
            )
        }

        pyprojectSingleScript(pyprojectToml)?.let { command ->
            return commandContract(command, Source.PYPROJECT_SCRIPT)
        }

        val safeEntrypoint = fallbackEntrypoint
            ?.let(EmbeddedPythonEntrypointPolicy::safeRelativePath)
            ?: return null
        return Contract(
            command = "python " + shellQuote(safeEntrypoint),
            source = Source.ENTRYPOINT_FALLBACK,
            requiresShell = false,
        )
    }

    internal fun renderStartCommand(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lines = text.lineSequence().toList()
        var inWebService = false
        var webServiceIndent = -1
        var fallback: String? = null
        for (raw in lines) {
            val line = raw.substringBefore('#').trimEnd()
            if (line.isBlank()) continue
            val indent = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val trimmed = line.trim()
            val typeMatch = Regex("""^-?\s*type\s*:\s*([^\s#]+)""", RegexOption.IGNORE_CASE)
                .find(trimmed)
            if (typeMatch != null) {
                val type = unquote(typeMatch.groupValues[1]).lowercase()
                inWebService = type == "web"
                webServiceIndent = indent
                continue
            }
            if (inWebService && indent <= webServiceIndent && trimmed.startsWith("-")) {
                inWebService = false
            }
            val start = Regex("""^startCommand\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::unquote)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (start != null) {
                if (inWebService) return start
                if (fallback == null) fallback = start
            }
        }
        return fallback
    }

    internal fun procfileWebCommand(text: String?): String? = text
        ?.lineSequence()
        ?.map { it.substringBefore('#').trim() }
        ?.firstOrNull { it.startsWith("web:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    internal fun packageStartCommand(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return root.optJSONObject("scripts")
            ?.optString("start")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    internal fun pyprojectSingleScript(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val root = runCatching { Toml.parse(text) }.getOrNull() ?: return null
        if (root.hasErrors()) return null
        val standard = scriptNames(tableAt(root, "project", "scripts"))
        if (standard.size == 1) return standard.single()
        if (standard.size > 1) return null
        val poetry = scriptNames(tableAt(root, "tool", "poetry", "scripts"))
        return poetry.singleOrNull()
    }

    private fun tableAt(root: TomlTable, vararg path: String): TomlTable? {
        var current: TomlTable = root
        for (segment in path) {
            current = (runCatching { current.get(segment) }.getOrNull() as? TomlTable)
                ?: return null
        }
        return current
    }

    private fun scriptNames(table: TomlTable?): List<String> {
        if (table == null) return emptyList()
        return table.keySet()
            .filter { PythonCliLaunchResolver.isSafeCommandName(it) }
            .filter { table.get(it) is String }
            .sorted()
    }

    private fun isDirectPythonRun(command: String, entrypoint: String): Boolean {
        val tokens = command.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.size != 2) return false
        val interpreter = tokens[0].substringAfterLast('/').lowercase()
        if (interpreter != "python" && interpreter != "python3") return false
        val argument = unquote(tokens[1])
        return EmbeddedPythonEntrypointPolicy.safeRelativePath(argument) == entrypoint
    }

    private fun commandContract(
        command: String,
        source: Source,
        portProbeText: String = command,
    ): Contract = Contract(
        command = command,
        source = source,
        requiresShell = true,
        explicitPort = explicitPort(portProbeText),
        requiresPortEnvironment = Regex("""(^|[^A-Za-z0-9_])\$\{?PORT\}?([^A-Za-z0-9_]|$)""")
            .containsMatchIn(portProbeText),
    )

    internal fun explicitPort(command: String): Int? {
        val patterns = listOf(
            Regex("""(?:^|\s)PORT=(\d{1,5})(?:\s|$)""", RegexOption.IGNORE_CASE),
            Regex("""--port(?:=|\s+)(\d{1,5})(?:\s|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:^|\s)-p\s+(\d{1,5})(?:\s|$)"""),
            Regex("""(?:0\.0\.0\.0|127\.0\.0\.1|localhost|\[?::\]?):(\d{1,5})(?:\s|$)""", RegexOption.IGNORE_CASE),
        )
        return patterns.asSequence()
            .mapNotNull { regex -> regex.find(command)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .firstOrNull { it in 1..65535 }
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}


internal object InternalRuntimePortAllocator {
    fun choose(preferredPorts: Collection<Int>): Int {
        preferredPorts
            .asSequence()
            .filter { it in 1..65535 }
            .distinct()
            .forEach { port ->
                if (available(port)) return port
            }
        java.net.ServerSocket(
            0,
            1,
            java.net.InetAddress.getByName("127.0.0.1"),
        ).use { socket ->
            return socket.localPort
        }
    }

    private fun available(port: Int): Boolean = runCatching {
        java.net.ServerSocket(
            port,
            1,
            java.net.InetAddress.getByName("127.0.0.1"),
        ).use { true }
    }.getOrDefault(false)
}
