package com.siftalpha.studio.siftalphax

/**
 * Frozen runtime facts used by alpha45 lock compatibility evaluation.
 *
 * This deliberately does not read ambient process environment variables. The values are explicit
 * SiftAlpha Runtime facts so the same lock produces the same selection decision.
 */
data class EmbeddedPythonRuntimeCompatibilityContextV1(
    val implementationName: String = "cpython",
    val platformPythonImplementation: String = "CPython",
    val pythonVersion: String = "3.14",
    val pythonFullVersion: String = "3.14.7",
    val sysPlatform: String = "android",
    val platformMachine: String = "aarch64",
    val osName: String = "posix",
)

enum class EmbeddedPythonCompatibilityStateV1 {
    MATCH,
    NO_MATCH,
    UNSUPPORTED,
}

data class EmbeddedPythonCompatibilityResultV1(
    val state: EmbeddedPythonCompatibilityStateV1,
    val detail: String? = null,
) {
    val matches: Boolean
        get() = state == EmbeddedPythonCompatibilityStateV1.MATCH
}

object EmbeddedPythonRuntimeCompatibilityV1 {
    fun requiresPython(
        specifier: String,
        context: EmbeddedPythonRuntimeCompatibilityContextV1 =
            EmbeddedPythonRuntimeCompatibilityContextV1(),
    ): EmbeddedPythonCompatibilityResultV1 {
        val runtime = ReleaseVersion.parse(context.pythonFullVersion)
            ?: return unsupported("runtime python_full_version is not a stable numeric release")

        val clauses = specifier.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (clauses.isEmpty()) return unsupported("requires-python is empty")

        for (clause in clauses) {
            val parsed = Specifier.parse(clause)
                ?: return unsupported("unsupported requires-python clause: " + clause)
            if (!parsed.matches(runtime)) {
                return noMatch("requires-python clause does not match: " + clause)
            }
        }
        return match()
    }

    fun marker(
        marker: String,
        context: EmbeddedPythonRuntimeCompatibilityContextV1 =
            EmbeddedPythonRuntimeCompatibilityContextV1(),
    ): EmbeddedPythonCompatibilityResultV1 {
        if (marker.isBlank()) return unsupported("marker is blank")
        return try {
            val parser = MarkerParser(marker, context)
            val value = parser.parse()
            if (value) match() else noMatch("marker evaluated false")
        } catch (failure: UnsupportedExpression) {
            unsupported(failure.message.orEmpty())
        }
    }

    private fun match() =
        EmbeddedPythonCompatibilityResultV1(EmbeddedPythonCompatibilityStateV1.MATCH)

    private fun noMatch(detail: String) =
        EmbeddedPythonCompatibilityResultV1(
            EmbeddedPythonCompatibilityStateV1.NO_MATCH,
            detail,
        )

    private fun unsupported(detail: String) =
        EmbeddedPythonCompatibilityResultV1(
            EmbeddedPythonCompatibilityStateV1.UNSUPPORTED,
            detail,
        )

    private data class ReleaseVersion(
        val parts: List<Int>,
    ) : Comparable<ReleaseVersion> {
        override fun compareTo(other: ReleaseVersion): Int {
            val size = maxOf(parts.size, other.parts.size)
            for (index in 0 until size) {
                val left = parts.getOrElse(index) { 0 }
                val right = other.parts.getOrElse(index) { 0 }
                if (left != right) return left.compareTo(right)
            }
            return 0
        }

        fun startsWith(prefix: ReleaseVersion): Boolean {
            if (prefix.parts.size > parts.size) return false
            return prefix.parts.indices.all { parts[it] == prefix.parts[it] }
        }

        companion object {
            fun parse(value: String): ReleaseVersion? {
                val text = value.trim()
                if (!text.matches(Regex("[0-9]+(?:\\.[0-9]+){0,3}"))) return null
                val parts = text.split('.').map { component ->
                    component.toIntOrNull() ?: return null
                }
                return ReleaseVersion(parts)
            }
        }
    }

    private data class Specifier(
        val operator: String,
        val release: ReleaseVersion,
        val wildcard: Boolean,
    ) {
        fun matches(runtime: ReleaseVersion): Boolean = when (operator) {
            "==" -> if (wildcard) runtime.startsWith(release) else runtime == release
            "!=" -> if (wildcard) !runtime.startsWith(release) else runtime != release
            ">=" -> runtime >= release
            "<=" -> runtime <= release
            ">" -> runtime > release
            "<" -> runtime < release
            "~=" -> {
                if (wildcard) {
                    false
                } else {
                    val upper = compatibleUpperBound(release)
                    upper != null && runtime >= release && runtime < upper
                }
            }
            else -> false
        }

        private fun compatibleUpperBound(base: ReleaseVersion): ReleaseVersion? {
            if (base.parts.size < 2) return null
            val pivot = base.parts.size - 2
            val upper = base.parts.take(pivot + 1).toMutableList()
            upper[pivot] = upper[pivot] + 1
            return ReleaseVersion(upper)
        }

        companion object {
            private val pattern =
                Regex("^(~=|==|!=|>=|<=|>|<)\\s*([0-9]+(?:\\.[0-9]+){0,3})(\\.\\*)?$")

            fun parse(value: String): Specifier? {
                val match = pattern.matchEntire(value.trim()) ?: return null
                val operator = match.groupValues[1]
                val release = ReleaseVersion.parse(match.groupValues[2]) ?: return null
                val wildcard = match.groupValues[3].isNotEmpty()
                if (wildcard && operator !in setOf("==", "!=")) return null
                if (operator == "~=" && release.parts.size < 2) return null
                return Specifier(operator, release, wildcard)
            }
        }
    }

    private class MarkerParser(
        source: String,
        private val context: EmbeddedPythonRuntimeCompatibilityContextV1,
    ) {
        private val tokenizer = MarkerTokenizer(source)
        private var current: Token = tokenizer.next()

        fun parse(): Boolean {
            val value = parseOr()
            if (current.type != TokenType.END) {
                fail("unexpected marker token: " + current.text)
            }
            return value
        }

        private fun parseOr(): Boolean {
            var value = parseAnd()
            while (current.type == TokenType.OR) {
                advance()
                val right = parseAnd()
                value = value || right
            }
            return value
        }

        private fun parseAnd(): Boolean {
            var value = parseFactor()
            while (current.type == TokenType.AND) {
                advance()
                val right = parseFactor()
                value = value && right
            }
            return value
        }

        private fun parseFactor(): Boolean {
            if (current.type == TokenType.LEFT_PAREN) {
                advance()
                val value = parseOr()
                expect(TokenType.RIGHT_PAREN)
                return value
            }
            return parseComparison()
        }

        private fun parseComparison(): Boolean {
            val left = parseOperand()
            val operator = when (current.type) {
                TokenType.EQUAL -> "=="
                TokenType.NOT_EQUAL -> "!="
                TokenType.LESS -> "<"
                TokenType.LESS_EQUAL -> "<="
                TokenType.GREATER -> ">"
                TokenType.GREATER_EQUAL -> ">="
                TokenType.IN -> "in"
                TokenType.NOT -> {
                    advance()
                    expect(TokenType.IN)
                    "not in"
                }
                else -> fail("marker comparison operator expected")
            }
            if (current.type != TokenType.NOT) advance()
            val right = parseOperand()
            return compare(left, operator, right)
        }

        private fun parseOperand(): Operand {
            val token = current
            return when (token.type) {
                TokenType.STRING -> {
                    advance()
                    Operand(token.text, versionSemantic = false)
                }
                TokenType.IDENTIFIER -> {
                    advance()
                    resolveVariable(token.text)
                }
                else -> fail("marker operand expected")
            }
        }

        private fun resolveVariable(name: String): Operand = when (name) {
            "implementation_name" -> Operand(context.implementationName, false)
            "platform_python_implementation" -> Operand(context.platformPythonImplementation, false)
            "python_version" -> Operand(context.pythonVersion, true)
            "python_full_version" -> Operand(context.pythonFullVersion, true)
            "sys_platform" -> Operand(context.sysPlatform, false)
            "platform_machine" -> Operand(context.platformMachine, false)
            "os_name" -> Operand(context.osName, false)
            else -> fail("unsupported marker variable: " + name)
        }

        private fun compare(left: Operand, operator: String, right: Operand): Boolean {
            if (operator == "in") return right.value.contains(left.value)
            if (operator == "not in") return !right.value.contains(left.value)

            if (left.versionSemantic || right.versionSemantic) {
                val leftVersion = ReleaseVersion.parse(left.value)
                    ?: fail("version marker operand is not a stable numeric release: " + left.value)
                val rightVersion = ReleaseVersion.parse(right.value)
                    ?: fail("version marker operand is not a stable numeric release: " + right.value)
                val comparison = leftVersion.compareTo(rightVersion)
                return applyComparison(comparison, operator)
            }

            return when (operator) {
                "==" -> left.value == right.value
                "!=" -> left.value != right.value
                else -> fail("relational marker operator is only supported for Python versions")
            }
        }

        private fun applyComparison(comparison: Int, operator: String): Boolean = when (operator) {
            "==" -> comparison == 0
            "!=" -> comparison != 0
            "<" -> comparison < 0
            "<=" -> comparison <= 0
            ">" -> comparison > 0
            ">=" -> comparison >= 0
            else -> fail("unsupported marker comparison operator: " + operator)
        }

        private fun expect(type: TokenType) {
            if (current.type != type) fail("expected " + type.name + " but found " + current.text)
            advance()
        }

        private fun advance() {
            current = tokenizer.next()
        }

        private fun fail(message: String): Nothing = throw UnsupportedExpression(message)
    }

    private data class Operand(
        val value: String,
        val versionSemantic: Boolean,
    )

    private class MarkerTokenizer(
        private val source: String,
    ) {
        private var index = 0

        fun next(): Token {
            skipWhitespace()
            if (index >= source.length) return Token(TokenType.END, "")

            val ch = source[index]
            if (ch == '(') {
                index++
                return Token(TokenType.LEFT_PAREN, "(")
            }
            if (ch == ')') {
                index++
                return Token(TokenType.RIGHT_PAREN, ")")
            }
            if (ch == '\'' || ch == '"') return stringToken(ch)

            for ((operator, type) in operators) {
                if (source.startsWith(operator, index)) {
                    index += operator.length
                    return Token(type, operator)
                }
            }

            if (ch.isLetter() || ch == '_') {
                val start = index
                index++
                while (
                    index < source.length &&
                    (source[index].isLetterOrDigit() || source[index] == '_')
                ) {
                    index++
                }
                val word = source.substring(start, index)
                return Token(
                    when (word) {
                        "and" -> TokenType.AND
                        "or" -> TokenType.OR
                        "in" -> TokenType.IN
                        "not" -> TokenType.NOT
                        else -> TokenType.IDENTIFIER
                    },
                    word,
                )
            }

            throw UnsupportedExpression("unsupported marker character: " + ch)
        }

        private fun stringToken(quote: Char): Token {
            index++
            val value = StringBuilder()
            while (index < source.length) {
                val ch = source[index++]
                if (ch == quote) return Token(TokenType.STRING, value.toString())
                if (ch == '\\') {
                    if (index >= source.length) {
                        throw UnsupportedExpression("unterminated marker escape")
                    }
                    val escaped = source[index++]
                    when (escaped) {
                        '\\', '\'', '"' -> value.append(escaped)
                        else -> throw UnsupportedExpression("unsupported marker escape")
                    }
                } else {
                    value.append(ch)
                }
            }
            throw UnsupportedExpression("unterminated marker string")
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private companion object {
            val operators = listOf(
                "==" to TokenType.EQUAL,
                "!=" to TokenType.NOT_EQUAL,
                "<=" to TokenType.LESS_EQUAL,
                ">=" to TokenType.GREATER_EQUAL,
                "<" to TokenType.LESS,
                ">" to TokenType.GREATER,
            )
        }
    }

    private data class Token(
        val type: TokenType,
        val text: String,
    )

    private enum class TokenType {
        IDENTIFIER,
        STRING,
        EQUAL,
        NOT_EQUAL,
        LESS,
        LESS_EQUAL,
        GREATER,
        GREATER_EQUAL,
        IN,
        NOT,
        AND,
        OR,
        LEFT_PAREN,
        RIGHT_PAREN,
        END,
    }

    private class UnsupportedExpression(message: String) : IllegalArgumentException(message)
}
