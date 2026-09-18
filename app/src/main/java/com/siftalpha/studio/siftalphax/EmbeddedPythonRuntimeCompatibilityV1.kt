package com.siftalpha.studio.siftalphax

data class EmbeddedPythonRuntimeCompatibilityContextV1(
    val implementationName: String = "cpython",
    val implementationVersion: String = "3.14.7",
    val platformPythonImplementation: String = "CPython",
    val pythonVersion: String = "3.14",
    val pythonFullVersion: String = "3.14.7",
    val sysPlatform: String = "android",
    val platformSystem: String = "Android",
    val platformMachine: String = "aarch64",
    val osName: String = "posix",
    val extra: String = "",
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
    ): EmbeddedPythonCompatibilityResultV1 =
        if (runCatching {
                EmbeddedPythonRequirementParserV1.versionMatches(
                    specifier,
                    context.pythonFullVersion,
                )
            }.getOrDefault(false)
        ) {
            match()
        } else {
            noMatch("requires-python does not match " + context.pythonFullVersion)
        }

    fun marker(
        marker: String,
        context: EmbeddedPythonRuntimeCompatibilityContextV1 =
            EmbeddedPythonRuntimeCompatibilityContextV1(),
    ): EmbeddedPythonCompatibilityResultV1 {
        if (marker.isBlank()) return unsupported("marker is blank")
        return try {
            if (MarkerParser(marker, context).parse()) match() else noMatch("marker evaluated false")
        } catch (failure: UnsupportedExpression) {
            unsupported(failure.message.orEmpty())
        }
    }

    private fun match() = EmbeddedPythonCompatibilityResultV1(EmbeddedPythonCompatibilityStateV1.MATCH)
    private fun noMatch(detail: String) =
        EmbeddedPythonCompatibilityResultV1(EmbeddedPythonCompatibilityStateV1.NO_MATCH, detail)
    private fun unsupported(detail: String) =
        EmbeddedPythonCompatibilityResultV1(EmbeddedPythonCompatibilityStateV1.UNSUPPORTED, detail)

    private class MarkerParser(
        source: String,
        private val context: EmbeddedPythonRuntimeCompatibilityContextV1,
    ) {
        private val tokenizer = MarkerTokenizer(source)
        private var current = tokenizer.next()

        fun parse(): Boolean {
            val value = parseOr()
            if (current.type != TokenType.END) fail("unexpected marker token: " + current.text)
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
                TokenType.EQUAL -> "==".also { advance() }
                TokenType.NOT_EQUAL -> "!=".also { advance() }
                TokenType.LESS -> "<".also { advance() }
                TokenType.LESS_EQUAL -> "<=".also { advance() }
                TokenType.GREATER -> ">".also { advance() }
                TokenType.GREATER_EQUAL -> ">=".also { advance() }
                TokenType.COMPATIBLE -> "~=".also { advance() }
                TokenType.IN -> "in".also { advance() }
                TokenType.NOT -> {
                    advance()
                    expect(TokenType.IN)
                    "not in"
                }
                else -> fail("marker comparison operator expected")
            }
            val right = parseOperand()
            return compare(left, operator, right)
        }

        private fun parseOperand(): Operand {
            val token = current
            return when (token.type) {
                TokenType.STRING -> {
                    advance()
                    Operand(token.text, false)
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
            "implementation_version" -> Operand(context.implementationVersion, true)
            "platform_python_implementation" -> Operand(context.platformPythonImplementation, false)
            "python_version" -> Operand(context.pythonVersion, true)
            "python_full_version" -> Operand(context.pythonFullVersion, true)
            "sys_platform" -> Operand(context.sysPlatform, false)
            "platform_system" -> Operand(context.platformSystem, false)
            "platform_machine" -> Operand(context.platformMachine, false)
            "os_name" -> Operand(context.osName, false)
            "extra" -> Operand(context.extra, false)
            else -> fail("unsupported marker variable: $name")
        }

        private fun compare(left: Operand, operator: String, right: Operand): Boolean {
            if (operator == "in") return right.value.contains(left.value)
            if (operator == "not in") return !right.value.contains(left.value)

            if (left.versionSemantic) {
                return EmbeddedPythonRequirementParserV1.versionMatches(
                    operator + right.value,
                    left.value,
                )
            }
            if (right.versionSemantic) {
                val inverted = when (operator) {
                    "<" -> ">"
                    "<=" -> ">="
                    ">" -> "<"
                    ">=" -> "<="
                    else -> operator
                }
                return EmbeddedPythonRequirementParserV1.versionMatches(
                    inverted + left.value,
                    right.value,
                )
            }

            return when (operator) {
                "==" -> left.value == right.value
                "!=" -> left.value != right.value
                else -> fail("relational marker operator requires version operands")
            }
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

    private data class Operand(val value: String, val versionSemantic: Boolean)

    private class MarkerTokenizer(private val source: String) {
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
                val start = index++
                while (
                    index < source.length &&
                    (source[index].isLetterOrDigit() || source[index] == '_')
                ) index++
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
            fail("unsupported marker character: $ch")
        }

        private fun stringToken(quote: Char): Token {
            index++
            val value = StringBuilder()
            while (index < source.length) {
                val ch = source[index++]
                if (ch == quote) return Token(TokenType.STRING, value.toString())
                if (ch == '\\') {
                    if (index >= source.length) fail("unterminated marker escape")
                    val escaped = source[index++]
                    if (escaped !in listOf('\\', '\'', '"')) fail("unsupported marker escape")
                    value.append(escaped)
                } else {
                    value.append(ch)
                }
            }
            fail("unterminated marker string")
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun fail(message: String): Nothing = throw UnsupportedExpression(message)

        private companion object {
            val operators = listOf(
                "==" to TokenType.EQUAL,
                "!=" to TokenType.NOT_EQUAL,
                "<=" to TokenType.LESS_EQUAL,
                ">=" to TokenType.GREATER_EQUAL,
                "~=" to TokenType.COMPATIBLE,
                "<" to TokenType.LESS,
                ">" to TokenType.GREATER,
            )
        }
    }

    private data class Token(val type: TokenType, val text: String)

    private enum class TokenType {
        IDENTIFIER, STRING, EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
        COMPATIBLE, IN, NOT, AND, OR, LEFT_PAREN, RIGHT_PAREN, END,
    }

    private class UnsupportedExpression(message: String) : IllegalArgumentException(message)
}
