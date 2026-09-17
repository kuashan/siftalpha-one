package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeArgumentParserTest {

    @Test
    fun parsesWhitespaceSeparatedArguments() {
        assertEquals(
            RuntimeArgumentParser.Result.Success(listOf("--help")),
            RuntimeArgumentParser.parse("--help"),
        )
    }

    @Test
    fun parsesQuotedArgumentWithSpaces() {
        assertEquals(
            RuntimeArgumentParser.Result.Success(listOf("--name", "john smith")),
            RuntimeArgumentParser.parse("""--name "john smith""""),
        )
    }

    @Test
    fun preservesEmptyQuotedArgument() {
        assertEquals(
            RuntimeArgumentParser.Result.Success(listOf("")),
            RuntimeArgumentParser.parse("\"\""),
        )
    }

    @Test
    fun treatsShellMetacharactersAsPlainArgumentCharacters() {
        assertEquals(
            RuntimeArgumentParser.Result.Success(listOf("abc", ";", "echo", "hacked")),
            RuntimeArgumentParser.parse("abc ; echo hacked"),
        )
    }

    @Test
    fun supportsBackslashEscapingWithoutShellExpansion() {
        assertEquals(
            RuntimeArgumentParser.Result.Success(listOf("\$HOME", "\$(command)")),
            RuntimeArgumentParser.parse("\$HOME \$(command)"),
        )
    }

    @Test
    fun rejectsUnclosedQuotes() {
        val result = RuntimeArgumentParser.parse("""--name "john""")

        assertEquals(
            RuntimeArgumentParser.Result.Invalid(RuntimeArgumentParser.Reason.UNCLOSED_QUOTE),
            result,
        )
    }

    @Test
    fun rejectsTooManyArguments() {
        val result = RuntimeArgumentParser.parse((1..65).joinToString(" ") { "x" })

        assertEquals(
            RuntimeArgumentParser.Result.Invalid(RuntimeArgumentParser.Reason.TOO_MANY_ARGUMENTS),
            result,
        )
    }

    @Test
    fun rejectsOverlongArgument() {
        val result = RuntimeArgumentParser.parse("x".repeat(4097))

        assertEquals(
            RuntimeArgumentParser.Result.Invalid(RuntimeArgumentParser.Reason.ARGUMENT_TOO_LONG),
            result,
        )
    }

    @Test
    fun rejectsIncompleteEscape() {
        assertEquals(
            RuntimeArgumentParser.Result.Invalid(RuntimeArgumentParser.Reason.INCOMPLETE_ESCAPE),
            RuntimeArgumentParser.parse("abc\\"),
        )
    }
}
