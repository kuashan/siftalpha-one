package com.siftalpha.studio.project

/**
 * High-confidence static inspection for common Python CLI frameworks.
 *
 * This is deliberately not a Python parser. It recognizes only literal declarations whose
 * required/positional semantics are stable enough to expose as a launch field. Dynamic CLI
 * construction and repeated/boolean argument contracts remain fail-closed and are left to
 * runtime diagnostics.
 */
enum class PythonCliArgumentKind {
    POSITIONAL,
    OPTION,
}

data class PythonCliRequirement(
    val name: String,
    val token: String,
    val kind: PythonCliArgumentKind,
    val required: Boolean,
    val source: ConfigurationSource = ConfigurationSource.STATIC_REQUIRED_READ,
    val evidence: ConfigurationEvidence? = null,
)

object PythonCliRequirementInspector {
    private val argparseCallStart = Regex("""\badd_argument\s*\(""")
    private val clickArgumentStart = Regex("""@\s*click\.argument\s*\(""")
    private val typerArgumentStart = Regex(
        """(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*[^=\n]+?=\s*typer\.Argument\s*\(""",
    )
    private val safePositionalName = Regex("""^[A-Za-z_][A-Za-z0-9_.-]*$""")
    private val safeOptionName = Regex("""^--?[A-Za-z0-9][A-Za-z0-9_-]*$""")
    private val simpleString = Regex(
        """^(?:[rRuUbBfF]{0,2})?([\'\"])(.*)\1$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun inspect(
        source: String,
        filePath: String? = null,
    ): List<PythonCliRequirement> {
        val result = linkedMapOf<String, PythonCliRequirement>()
        inspectArgparse(source, filePath).forEach { result.putIfAbsent(it.token, it) }
        inspectClick(source, filePath).forEach { result.putIfAbsent(it.token, it) }
        inspectTyper(source, filePath).forEach { result.putIfAbsent(it.token, it) }
        return result.values.toList()
    }

    private fun inspectArgparse(
        source: String,
        filePath: String?,
    ): List<PythonCliRequirement> {
        if (
            "ArgumentParser" !in source ||
            "parse_args" !in source ||
            "add_argument" !in source
        ) {
            return emptyList()
        }

        val result = mutableListOf<PythonCliRequirement>()
        argparseCallStart.findAll(source).forEach { match ->
            val call = parseCall(source, match.range.first) ?: return@forEach
            val literalNames = literalLeadingArguments(call.parts)
            if (literalNames.isEmpty()) return@forEach
            val keywords = keywordArguments(call.parts)

            val optionLike = literalNames.any { it.startsWith("-") }
            if (optionLike) {
                val options = literalNames.filter { safeOptionName.matches(it) }
                if (options.isEmpty()) return@forEach
                val action = keywords["action"]?.let(::parseSimpleString)
                if (action != null && action != "store") return@forEach
                if (keywords["required"]?.trim() != "True") return@forEach
                val token = options
                    .sortedWith(
                        compareByDescending<String> { it.startsWith("--") }
                            .thenByDescending { it.length },
                    )
                    .first()
                result += PythonCliRequirement(
                    name = token.removePrefix("--").removePrefix("-"),
                    token = token,
                    kind = PythonCliArgumentKind.OPTION,
                    required = true,
                    evidence = ConfigurationEvidence(
                        filePath = filePath,
                        lineNumber = lineNumberAt(source, match.range.first),
                        detail = "argparse required option",
                    ),
                )
            } else {
                if (literalNames.size != 1) return@forEach
                val name = literalNames.single()
                if (!safePositionalName.matches(name)) return@forEach
                // Scalar positional arguments are required by default. Any explicit nargs needs
                // richer cardinality handling and is intentionally left to runtime diagnostics.
                if ("nargs" in keywords) return@forEach
                result += PythonCliRequirement(
                    name = name,
                    token = name,
                    kind = PythonCliArgumentKind.POSITIONAL,
                    required = true,
                    evidence = ConfigurationEvidence(
                        filePath = filePath,
                        lineNumber = lineNumberAt(source, match.range.first),
                        detail = "argparse required positional argument",
                    ),
                )
            }
        }
        return result
    }

    private fun inspectClick(
        source: String,
        filePath: String?,
    ): List<PythonCliRequirement> {
        if ("click" !in source || "@click.argument" !in source) return emptyList()

        val result = mutableListOf<PythonCliRequirement>()
        clickArgumentStart.findAll(source).forEach { match ->
            val call = parseCall(source, match.range.first) ?: return@forEach
            val literalNames = literalLeadingArguments(call.parts)
            if (literalNames.size != 1) return@forEach
            val name = literalNames.single()
            if (!safePositionalName.matches(name)) return@forEach
            val keywords = keywordArguments(call.parts)
            if (keywords["required"]?.trim() == "False") return@forEach
            if ("nargs" in keywords) {
                val nargs = keywords["nargs"]?.trim()?.toIntOrNull()
                if (nargs == null || nargs != 1) return@forEach
            }
            val metavar = keywords["metavar"]?.let(::parseSimpleString)
                ?.takeIf { safePositionalName.matches(it) }
            result += PythonCliRequirement(
                name = name,
                token = metavar ?: name.uppercase(),
                kind = PythonCliArgumentKind.POSITIONAL,
                required = true,
                evidence = ConfigurationEvidence(
                    filePath = filePath,
                    lineNumber = lineNumberAt(source, match.range.first),
                    detail = "Click required positional argument",
                ),
            )
        }
        return result
    }

    private fun inspectTyper(
        source: String,
        filePath: String?,
    ): List<PythonCliRequirement> {
        if ("typer" !in source || "typer.Argument" !in source) return emptyList()

        val result = mutableListOf<PythonCliRequirement>()
        typerArgumentStart.findAll(source).forEach { match ->
            val name = match.groupValues.getOrNull(1).orEmpty()
            if (!safePositionalName.matches(name)) return@forEach
            val openParen = source.indexOf('(', match.range.first)
            if (openParen < 0) return@forEach
            val closeParen = findMatchingParen(source, openParen) ?: return@forEach
            val body = source.substring(openParen + 1, closeParen)
            val parts = splitTopLevel(body)
            val keywords = keywordArguments(parts)
            val first = parts.firstOrNull()?.trim().orEmpty()
            val required = first == "..." || keywords["default"]?.trim() == "..."
            if (!required) return@forEach
            val metavar = keywords["metavar"]?.let(::parseSimpleString)
                ?.takeIf { safePositionalName.matches(it) }
            result += PythonCliRequirement(
                name = name,
                token = metavar ?: name.uppercase(),
                kind = PythonCliArgumentKind.POSITIONAL,
                required = true,
                evidence = ConfigurationEvidence(
                    filePath = filePath,
                    lineNumber = lineNumberAt(source, match.range.first),
                    detail = "Typer required positional argument",
                ),
            )
        }
        return result
    }

    private data class ParsedCall(
        val parts: List<String>,
    )

    private fun parseCall(source: String, start: Int): ParsedCall? {
        val openParen = source.indexOf('(', start)
        if (openParen < 0) return null
        val closeParen = findMatchingParen(source, openParen) ?: return null
        return ParsedCall(splitTopLevel(source.substring(openParen + 1, closeParen)))
    }

    private fun literalLeadingArguments(parts: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (part in parts) {
            if (topLevelEqualsIndex(part) >= 0) break
            val value = parseSimpleString(part.trim()) ?: break
            result += value
        }
        return result
    }

    private fun keywordArguments(parts: List<String>): Map<String, String> =
        parts.mapNotNull { part ->
            val equals = topLevelEqualsIndex(part)
            if (equals <= 0) null else {
                val key = part.substring(0, equals).trim()
                val value = part.substring(equals + 1).trim()
                key to value
            }
        }.toMap()

    private fun findMatchingParen(source: String, start: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var index = start
        while (index < source.length) {
            val ch = source[index]
            if (quote != null) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == quote) {
                    quote = null
                }
                index += 1
                continue
            }
            when (ch) {
                '\'', '"' -> quote = ch
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return index
                    if (depth < 0) return null
                }
            }
            index += 1
        }
        return null
    }

    private fun splitTopLevel(body: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var round = 0
        var square = 0
        var curly = 0
        var quote: Char? = null
        var escaped = false
        body.forEachIndexed { index, ch ->
            if (quote != null) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == quote) {
                    quote = null
                }
                return@forEachIndexed
            }
            when (ch) {
                '\'', '"' -> quote = ch
                '(' -> round += 1
                ')' -> round -= 1
                '[' -> square += 1
                ']' -> square -= 1
                '{' -> curly += 1
                '}' -> curly -= 1
                ',' -> if (round == 0 && square == 0 && curly == 0) {
                    body.substring(start, index).trim().takeIf { it.isNotBlank() }?.let(result::add)
                    start = index + 1
                }
            }
        }
        body.substring(start).trim().takeIf { it.isNotBlank() }?.let(result::add)
        return result
    }

    private fun topLevelEqualsIndex(text: String): Int {
        var round = 0
        var square = 0
        var curly = 0
        var quote: Char? = null
        var escaped = false
        text.forEachIndexed { index, ch ->
            if (quote != null) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == quote) quote = null
                return@forEachIndexed
            }
            when (ch) {
                '\'', '"' -> quote = ch
                '(' -> round += 1
                ')' -> round -= 1
                '[' -> square += 1
                ']' -> square -= 1
                '{' -> curly += 1
                '}' -> curly -= 1
                '=' -> if (round == 0 && square == 0 && curly == 0) return index
            }
        }
        return -1
    }

    private fun parseSimpleString(raw: String): String? {
        val match = simpleString.matchEntire(raw.trim()) ?: return null
        val value = match.groupValues[2]
        if ('\n' in value || '\r' in value || '\u0000' in value) return null
        return value.replace("\\\\", "\\")
    }

    private fun lineNumberAt(source: String, offset: Int): Int =
        source.substring(0, offset.coerceIn(0, source.length)).count { it == '\n' } + 1
}
