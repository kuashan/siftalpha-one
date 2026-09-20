package com.siftalpha.studio.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonCliRequirementInspectorTest {

    @Test
    fun detectsRequiredArgparsePositionalsInDeclarationOrder() {
        val source = """
            import argparse

            parser = argparse.ArgumentParser()
            parser.add_argument("MARKET")
            parser.add_argument("CODE", help="instrument code")
            args = parser.parse_args()
        """.trimIndent()

        val result = PythonCliRequirementInspector.inspect(source, "main.py")

        assertEquals(listOf("MARKET", "CODE"), result.map { it.name })
        assertTrue(result.all { it.required })
        assertTrue(result.all { it.kind == PythonCliArgumentKind.POSITIONAL })
        assertEquals(listOf(4, 5), result.mapNotNull { it.evidence?.lineNumber })
    }

    @Test
    fun detectsRequiredValuedOptionButSkipsOptionalOption() {
        val source = """
            import argparse
            parser = argparse.ArgumentParser()
            parser.add_argument("--market", required=True)
            parser.add_argument("--code")
            args = parser.parse_args()
        """.trimIndent()

        val result = PythonCliRequirementInspector.inspect(source)

        assertEquals(listOf("--market"), result.map { it.token })
        assertEquals(PythonCliArgumentKind.OPTION, result.single().kind)
    }

    @Test
    fun skipsBooleanActionBecauseItNeedsAFlagContractNotAValueField() {
        val source = """
            import argparse
            parser = argparse.ArgumentParser()
            parser.add_argument("--force", action="store_true", required=True)
            args = parser.parse_args()
        """.trimIndent()

        assertTrue(PythonCliRequirementInspector.inspect(source).isEmpty())
    }

    @Test
    fun skipsOptionalOrRepeatedPositionalsUntilARepeatedValueEditorExists() {
        val source = """
            import argparse
            parser = argparse.ArgumentParser()
            parser.add_argument("maybe", nargs="?")
            parser.add_argument("many", nargs="+")
            args = parser.parse_args()
        """.trimIndent()

        assertTrue(PythonCliRequirementInspector.inspect(source).isEmpty())
    }

    @Test
    fun requiresArgumentParserAndParseArgsEvidence() {
        val source = "helper.add_argument(\"MARKET\")"
        assertTrue(PythonCliRequirementInspector.inspect(source).isEmpty())
    }
}
