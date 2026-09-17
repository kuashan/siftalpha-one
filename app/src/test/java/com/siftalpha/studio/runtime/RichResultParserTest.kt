package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichResultParserTest {

    @Test
    fun parsesStrongLinkListAndPreservesOrder() {
        val result = RichResultParser.parse(
            """
            [+] Codeforces: https://codeforces.com/profile/test
            [+] 中文站点: https://example.com/中文
            """.trimIndent(),
        )

        assertEquals(
            listOf("Codeforces", "中文站点"),
            result?.items?.map { it.label },
        )
        assertEquals(
            listOf(
                "https://codeforces.com/profile/test",
                "https://example.com/中文",
            ),
            result?.items?.map { it.url },
        )
    }

    @Test
    fun deduplicatesUrlsWithoutReorderingFirstOccurrence() {
        val result = RichResultParser.parse(
            """
            [+] First label: https://example.com/result
            [+] Duplicate label: https://example.com/result
            [+] Second: http://example.org/item
            """.trimIndent(),
        )

        assertEquals(
            listOf("First label", "Second"),
            result?.items?.map { it.label },
        )
    }

    @Test
    fun stripsAnsiAndOsc8ControlsFromDetectedText() {
        val output =
            "\u001B[32m\u001B]8;;https://example.com\u0007[+] Label: https://example.com\u001B]8;;\u0007\u001B[0m"

        val result = RichResultParser.parse(output)

        assertEquals("Label", result?.items?.single()?.label)
        assertEquals("https://example.com", result?.items?.single()?.url)
        assertTrue(!result!!.items.single().label.contains('\u001B'))
    }

    @Test
    fun rejectsProxyHelpWebLogsAndWeakUrls() {
        val output = """
            --proxy socks5://127.0.0.1:1080
            Server running at http://0.0.0.0:8765
            SIFTALPHA_WEB_URL=http://127.0.0.1:8765
            ordinary help: https://example.com/help
        """.trimIndent()

        assertNull(RichResultParser.parse(output))
    }

    @Test
    fun rejectsNonHttpSchemes() {
        val result = RichResultParser.parse(
            """
            [+] File: file:///tmp/result
            [+] JavaScript: javascript:alert(1)
            [+] Data: data:text/plain,result
            [+] Intent: intent://example
            [+] Content: content://example
            [+] Socks: socks5://127.0.0.1:1080
            """.trimIndent(),
        )

        assertNull(result)
    }

    @Test
    fun capsResultItems() {
        val output = (0 until (RichResultParser.MAX_ITEMS + 25)).joinToString("\n") { index ->
            "[+] Item $index: https://example.com/item/$index"
        }

        assertEquals(RichResultParser.MAX_ITEMS, RichResultParser.parse(output)?.items?.size)
    }

    @Test
    fun unclosedAnsiOrOscControlsCannotPolluteUrl() {
        val result = RichResultParser.parse(
            "\u001B[31m[+] Safe: https://example.com/path\u001B",
        )

        assertEquals("https://example.com/path", result?.items?.single()?.url)
    }
}
