package com.siftalpha.studio.runtime

import java.net.URI
import java.util.LinkedHashSet

enum class RichResultType {
    LINK_LIST,
}

data class RichResultItem(
    val label: String,
    val url: String,
)

data class RichResultDocument(
    val type: RichResultType,
    val items: List<RichResultItem>,
) {
    val isEmpty: Boolean
        get() = items.isEmpty()
}

/**
 * Removes terminal transport controls without applying terminal-screen semantics such as cursor
 * movement, scrollback or line overwrites. Result detection needs the visible text while preserving
 * the source line structure used by the strong link-list contract.
 */
object ResultTextSanitizer {
    private const val ESC = '\u001B'
    private const val BEL = '\u0007'
    private const val ST = '\\'

    fun sanitize(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character != ESC) {
                when {
                    character == '\r' -> result.append('\n')
                    character == '\n' || character == '\t' || character.code >= 0x20 ->
                        result.append(character)
                }
                index += 1
                continue
            }

            if (index + 1 >= value.length) {
                index += 1
                continue
            }

            when (value[index + 1]) {
                '[' -> {
                    // CSI: skip until the final byte in the 0x40..0x7e range.
                    index += 2
                    while (index < value.length) {
                        val code = value[index].code
                        index += 1
                        if (code in 0x40..0x7e) break
                    }
                }
                ']' -> {
                    // OSC, including OSC 8 hyperlinks: remove the control sequence but keep the
                    // visible label/text that follows it. Both BEL and ESC-backslash terminators
                    // are accepted.
                    index += 2
                    while (index < value.length) {
                        if (value[index] == BEL) {
                            index += 1
                            break
                        }
                        if (
                            value[index] == ESC &&
                            index + 1 < value.length &&
                            value[index + 1] == ST
                        ) {
                            index += 2
                            break
                        }
                        index += 1
                    }
                }
                else -> {
                    // Two-byte ESC sequences (for example save/restore cursor).
                    index += 2
                }
            }
        }
        return result.toString()
    }
}

object RichResultUrlPolicy {
    private const val MAX_URL_LENGTH = 4096

    fun validate(raw: String): String? {
        val candidate = raw.trim()
        if (candidate.isBlank() || candidate.length > MAX_URL_LENGTH) return null
        if (candidate.any { it.isWhitespace() || it.code < 0x20 }) return null

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        if (uri.port < -1 || uri.port > 65535) return null
        return candidate
    }
}

/**
 * Alpha42 intentionally accepts only an explicit, line-oriented result format. This prevents
 * ordinary help text, Web startup logs and proxy examples from becoming user-facing results.
 */
object RichResultParser {
    const val MAX_ITEMS = 200
    const val MAX_INSPECTED_LINES = 4096
    const val MAX_INPUT_CHARS = 512 * 1024

    private val strongLinkLine = Regex("""^\s*\[\+\]\s*(.+?)\s*:\s*(\S+)\s*$""")

    fun parse(output: String): RichResultDocument? {
        val sanitized = ResultTextSanitizer.sanitize(output.take(MAX_INPUT_CHARS))
        val items = mutableListOf<RichResultItem>()
        val seenUrls = LinkedHashSet<String>()

        sanitized.lineSequence()
            .take(MAX_INSPECTED_LINES)
            .forEach { line ->
                if (items.size >= MAX_ITEMS) return@forEach
                val match = strongLinkLine.matchEntire(line) ?: return@forEach
                val label = match.groupValues[1].trim()
                val candidate = match.groupValues[2].trim().trimEnd('.', ',', ';')
                val url = RichResultUrlPolicy.validate(candidate) ?: return@forEach
                if (label.isBlank() || !seenUrls.add(url)) return@forEach
                items += RichResultItem(label = label, url = url)
            }

        return items
            .takeIf { it.isNotEmpty() }
            ?.let { RichResultDocument(type = RichResultType.LINK_LIST, items = it) }
    }
}
