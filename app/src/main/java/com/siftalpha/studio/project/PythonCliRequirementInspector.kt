package com.siftalpha.studio.project

/**
 * High-confidence static inspection for conventional Python argparse declarations.
 *
 * This is deliberately not a Python parser. It recognizes only literal add_argument(...) calls
 * in files that also contain ArgumentParser + parse_args evidence. Dynamic argument construction,
 * custom parser wrappers and complex nargs/action contracts are left to runtime diagnostics.
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
    private val callStart = Regex("""\badd_argument\s*\(""")
    private val safePositionalName = Regex("""^[A-Za-z_][A-Za-z0-9_.-]*$""")
    private val safeOptionName = Regex("""^--?[A-Za-z0-9][A-Za-z0-9_-]*$""")
    private val simpleString = Regex("""^(?:[rRuUbBfF]{0,2})?([\'\"])(.*)\1$""", RegexOption.DOT_MATCHES_ALL)

    fun inspect(
        source: String,
        filePath: String? = null,
    ): List<PythonCliRequirement> {
        if (
            "ArgumentParser" !in source ||
            "parse_args" !in source ||
            "add_argument" !in source
        ) {
            return emptyList()
        }

        val result = linkedMapOf<String, PythonCliRequirement>()
        callStart.findAll(source).forEach { match ->
            val openParen = source.indexOf('(', match.range.first)
            if (openParen < 0) return@forEach
            val closeParen = findMatchingParen(source, openParen) ?: return@forEach
            val body = source.substring(openParen + 1, closeParen)
            val parts = splitTopLevel(body)
            if (parts.isEmpty()) return@forEach

            val literalNames = mutableListOf<String>()
            for (part in parts) {
                if (topLevelEqualsIndex(part) >= 0) break
                val value = parseSimpleString(part.trim()) ?: break
                literalNames += value
            }
            if (literalNames.isEmpty()) return@forEach

            val keywords = parts
                .mapNotNull { part ->
                    val equals = topLevelEqualsIndex(part)
                    if (equals <= 0) null else {
                        val key = part.substring(0, equals).trim()
                        val value = part.substring(equals + 1).trim()
                        key to value
                    }
                }
                .toMap()

            val optionLike = literalNames.any { it.startsWith("-") }
            val requirement = if (optionLike) {
                val options = literalNames.filter { safeOptionName.matches(it) }
                if (options.isEmpty()) return@forEach
                // Boolean/count/help actions need a different UI contract than a value field.
                val action = keywords["action"]?.let(::parseSimpleString)
                if (action != null && action != "store") return@forEach
                val required = keywords["required"]?.trim() == "True"
                if (!required) return@forEach
                val token = options
                    .sortedWith(compareByDescending<String> { it.startsWith("--") }.thenByDescending { it.length })
                    .first()
                PythonCliRequirement(
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
                val nargs = keywords["nargs"]?.let(::parseSimpleString)
                // Keep the first contract intentionally scalar. '?'/'*' are optional and '+' or
                // numeric nargs need a repeated-value editor, so they are left to runtime fallback.
                if (nargs != null) return@forEach
                PythonCliRequirement(
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
            result.putIfAbsent(requirement.token, requirement)
        }
        return result.values.toList()
    }

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
