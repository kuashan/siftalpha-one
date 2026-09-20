package com.siftalpha.studio.runtime

import java.security.MessageDigest
import kotlin.math.max

data class ResultSourceHints(
    val tabular: Boolean = false,
    val charting: Boolean = false,
    val json: Boolean = false,
    val csv: Boolean = false,
    val markdown: Boolean = false,
) {
    val any: Boolean get() = tabular || charting || json || csv || markdown
}

object ResultSourceHintInspector {
    fun inspect(source: String?): ResultSourceHints {
        val text = source.orEmpty()
        if (text.isBlank()) return ResultSourceHints()
        return ResultSourceHints(
            tabular = listOf("pandas", "DataFrame", "tabulate(", "to_markdown(", "pivot_table(").any { it in text },
            charting = listOf("matplotlib", "pyplot", "plt.", "plotly", "altair", "seaborn", ".plot(").any { it in text },
            json = listOf("json.dumps(", "json.dump(", "to_json(").any { it in text },
            csv = listOf("to_csv(", "csv.writer(", "csv.DictWriter(").any { it in text },
            markdown = listOf("markdown", "to_markdown(").any { it in text },
        )
    }
}

data class ExtractedProgramOutput(
    val text: String,
    val stderr: String = "",
    val sourcePath: String? = null,
)

object RuntimeProgramOutputExtractor {
    private val metadataLine = Regex(
        """^(?:STARTED_AT|EXITED_AT|STATE|EXIT_CODE|LOG_BYTES|COMMAND|DEPENDENCY_SOURCE)=.*$""",
    )

    fun extract(
        stdout: String,
        stderr: String = "",
        explicitSourcePath: String? = null,
    ): ExtractedProgramOutput {
        val sanitizedStdout = ResultTextSanitizer.sanitize(stdout)
        val sanitizedStderr = ResultTextSanitizer.sanitize(stderr)
        val discoveredSource = sanitizedStdout.lineSequence()
            .firstNotNullOfOrNull { line ->
                when {
                    line.startsWith("SIFTALPHA_LAUNCH_ENTRYPOINT=") ->
                        line.substringAfter('=').trim().takeIf { it.isNotBlank() }
                    line.startsWith("SIFTALPHA_X_ENTRYPOINT=") ->
                        line.substringAfter('=').trim()
                            .substringAfterLast("/workspace/")
                            .takeIf { it.isNotBlank() && !it.startsWith("/") }
                    else -> null
                }
            }
        val sourcePath = explicitSourcePath?.trim()?.takeIf { it.isNotBlank() } ?: discoveredSource

        val filtered = sanitizedStdout.lineSequence()
            .filterNot(::isRuntimeEnvelopeLine)
            .joinToString("\n")
            .trim()

        val safeStderr = sanitizedStderr.trim()
            .takeIf { it.isNotBlank() && !filtered.contains(it) }
            .orEmpty()

        return ExtractedProgramOutput(
            text = filtered,
            stderr = safeStderr,
            sourcePath = sourcePath,
        )
    }

    private fun isRuntimeEnvelopeLine(raw: String): Boolean {
        val line = raw.trim()
        if (line.isBlank()) return false
        if (line.startsWith("SIFTALPHA_")) return true
        if (metadataLine.matches(line)) return true
        return line in setOf(
            "=== SiftAlpha Studio Runtime ===",
            "=== SiftAlpha Project Log ===",
            "=== SiftAlpha Runtime Diagnostic ===",
            "--- state ---",
        )
    }
}

data class ResultMetric(val label: String, val value: String)

data class ResultTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

data class ResultChartSeries(
    val name: String,
    val values: List<Double?>,
)

data class ResultChart(
    val title: String?,
    val xLabels: List<String>,
    val series: List<ResultChartSeries>,
)

sealed class AdaptiveResultBlock {
    data class Text(val text: String) : AdaptiveResultBlock()
    data class Metrics(val items: List<ResultMetric>) : AdaptiveResultBlock()
    data class Table(val table: ResultTable) : AdaptiveResultBlock()
    data class Chart(val chart: ResultChart) : AdaptiveResultBlock()
}

data class AdaptiveResultSection(
    val title: String?,
    val blocks: List<AdaptiveResultBlock>,
)

data class AdaptiveResultDocument(
    val projectName: String,
    val sourcePath: String?,
    val sections: List<AdaptiveResultSection>,
    val rawText: String,
    val stderr: String,
    val sourceHints: ResultSourceHints,
) {
    val tableCount: Int = sections.sumOf { section -> section.blocks.count { it is AdaptiveResultBlock.Table } }
    val chartCount: Int = sections.sumOf { section -> section.blocks.count { it is AdaptiveResultBlock.Chart } }
    val metricGroupCount: Int = sections.sumOf { section -> section.blocks.count { it is AdaptiveResultBlock.Metrics } }

    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (metricGroupCount > 0) parts += metricGroupCount.toString() + " metric group(s)"
            if (tableCount > 0) parts += tableCount.toString() + " table(s)"
            if (chartCount > 0) parts += chartCount.toString() + " chart(s)"
            if (parts.isEmpty()) parts += sections.size.coerceAtLeast(1).toString() + " section(s)"
            return parts.joinToString(" · ")
        }
}

object AdaptiveResultAnalyzer {
    private val separatorLine = Regex("""^\s*[=\-_*]{6,}\s*$""")
    private val bracketHeading = Regex("""^\s*\[(?:BEST|DETAIL|RESULT|SUMMARY|REPORT|\*)\]\s*(.+?)\s*$""")
    private val markdownHeading = Regex("""^\s*#{1,4}\s+(.+?)\s*$""")
    private val keyValue = Regex("""^\s*([^:：]{1,80})\s*[:：]\s*(.+?)\s*$""")
    private val multiSpace = Regex("""\s{2,}""")
    private val dateLike = Regex("""^\d{4}[-/]\d{1,2}[-/]\d{1,2}(?:[ T].*)?$""")
    private val numberToken = Regex("""^[+\-]?(?:\d+(?:\.\d+)?|\.\d+)(?:[eE][+\-]?\d+)?%?$""")

    fun analyze(
        projectName: String,
        extracted: ExtractedProgramOutput,
        sourceText: String? = null,
    ): AdaptiveResultDocument {
        val clean = ResultTextSanitizer.sanitize(extracted.text).trim()
        val hints = ResultSourceHintInspector.inspect(sourceText)
        val sections = splitSections(clean.lineSequence().toList())
            .mapNotNull { section ->
                val blocks = analyzeLines(section.lines, section.title, hints)
                blocks.takeIf { it.isNotEmpty() }?.let { AdaptiveResultSection(section.title, blocks) }
            }
            .ifEmpty {
                listOf(
                    AdaptiveResultSection(
                        title = null,
                        blocks = listOf(AdaptiveResultBlock.Text(clean.ifBlank { "No program output." })),
                    ),
                )
            }
        return AdaptiveResultDocument(
            projectName = projectName,
            sourcePath = extracted.sourcePath,
            sections = sections,
            rawText = clean,
            stderr = extracted.stderr,
            sourceHints = hints,
        )
    }

    private data class PendingSection(val title: String?, val lines: List<String>)
    private data class ParsedTable(val table: ResultTable, val nextIndex: Int)
    private data class ParsedMetrics(val metrics: List<ResultMetric>, val nextIndex: Int)

    private fun splitSections(lines: List<String>): List<PendingSection> {
        if (lines.isEmpty()) return emptyList()
        val result = mutableListOf<PendingSection>()
        var title: String? = null
        val buffer = mutableListOf<String>()

        fun flush() {
            val trimmed = trimBlankEdges(buffer)
            if (trimmed.isNotEmpty() || title != null) result += PendingSection(title, trimmed)
            buffer.clear()
        }

        var index = 0
        while (index < lines.size) {
            if (
                index + 2 < lines.size &&
                separatorLine.matches(lines[index]) &&
                lines[index + 1].isNotBlank() &&
                separatorLine.matches(lines[index + 2])
            ) {
                flush()
                title = normalizeHeading(lines[index + 1])
                index += 3
                continue
            }
            val directHeading = bracketHeading.matchEntire(lines[index])
                ?.groupValues?.getOrNull(1)?.trim()
                ?: markdownHeading.matchEntire(lines[index])
                    ?.groupValues?.getOrNull(1)?.trim()
            if (directHeading != null) {
                flush()
                title = directHeading
                index += 1
                continue
            }
            buffer += lines[index]
            index += 1
        }
        flush()
        return result
    }

    private fun normalizeHeading(value: String): String =
        bracketHeading.matchEntire(value)?.groupValues?.getOrNull(1)?.trim() ?: value.trim()

    private fun analyzeLines(
        lines: List<String>,
        sectionTitle: String?,
        hints: ResultSourceHints,
    ): List<AdaptiveResultBlock> {
        val blocks = mutableListOf<AdaptiveResultBlock>()
        var index = 0
        while (index < lines.size) {
            if (lines[index].isBlank()) {
                index += 1
                continue
            }

            val ascii = parseAsciiTable(lines, index)
            if (ascii != null) {
                blocks += AdaptiveResultBlock.Table(ascii.table)
                chartFromTable(ascii.table, sectionTitle, hints)?.let { blocks += AdaptiveResultBlock.Chart(it) }
                index = ascii.nextIndex
                continue
            }

            val delimited = parseDelimitedTable(lines, index)
            if (delimited != null) {
                blocks += AdaptiveResultBlock.Table(delimited.table)
                chartFromTable(delimited.table, sectionTitle, hints)?.let { blocks += AdaptiveResultBlock.Chart(it) }
                index = delimited.nextIndex
                continue
            }

            val inline = parseInlineMetrics(lines[index])
            if (inline != null) {
                blocks += AdaptiveResultBlock.Metrics(inline)
                index += 1
                continue
            }

            val metricRun = parseMetricRun(lines, index)
            if (metricRun != null) {
                blocks += AdaptiveResultBlock.Metrics(metricRun.metrics)
                index = metricRun.nextIndex
                continue
            }

            val textLines = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) {
                if (
                    parseAsciiTable(lines, index) != null ||
                    parseDelimitedTable(lines, index) != null ||
                    parseInlineMetrics(lines[index]) != null ||
                    parseMetricRun(lines, index) != null
                ) break
                textLines += lines[index]
                index += 1
            }
            if (textLines.isNotEmpty()) {
                blocks += AdaptiveResultBlock.Text(textLines.joinToString("\n").trimEnd())
            } else {
                index += 1
            }
        }
        return blocks
    }

    private fun parseAsciiTable(lines: List<String>, start: Int): ParsedTable? {
        if (start + 2 >= lines.size) return null
        val header = splitColumns(lines[start])
        if (header.size < 2 || !separatorLine.matches(lines[start + 1])) return null

        val rows = mutableListOf<List<String>>()
        var index = start + 2
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || separatorLine.matches(line)) break
            if (bracketHeading.matches(line) || markdownHeading.matches(line)) break
            val cells = splitColumns(line)
            if (cells.size < 2) break
            rows += normalizeRow(cells, header.size)
            index += 1
        }
        if (rows.size < 2) return null
        return ParsedTable(ResultTable(header, rows), index)
    }

    private fun splitColumns(line: String): List<String> =
        line.trim().split(multiSpace).map { it.trim() }.filter { it.isNotBlank() }

    private fun normalizeRow(cells: List<String>, headerSize: Int): List<String> {
        if (cells.size == headerSize) return cells
        if (cells.size > headerSize) {
            val extra = cells.size - headerSize
            return listOf(cells.take(extra + 1).joinToString(" ")) + cells.drop(extra + 1)
        }
        return cells + List(headerSize - cells.size) { "" }
    }

    private fun parseDelimitedTable(lines: List<String>, start: Int): ParsedTable? {
        val delimiter = when {
            lines[start].count { it == '\t' } >= 1 -> '\t'
            lines[start].count { it == ',' } >= 1 -> ','
            else -> return null
        }
        val header = splitDelimited(lines[start], delimiter)
        if (header.size < 2 || header.size > 16) return null

        val rows = mutableListOf<List<String>>()
        var index = start + 1
        while (index < lines.size) {
            if (lines[index].isBlank()) break
            val cells = splitDelimited(lines[index], delimiter)
            if (cells.size != header.size) break
            rows += cells
            index += 1
        }
        if (rows.size < 2) return null
        return ParsedTable(ResultTable(header, rows), index)
    }

    private fun splitDelimited(line: String, delimiter: Char): List<String> =
        line.split(delimiter).map { it.trim().trim('"') }

    private fun parseInlineMetrics(line: String): List<ResultMetric>? {
        if ('|' !in line) return null
        val metrics = line.split('|').mapNotNull { piece ->
            val match = keyValue.matchEntire(piece.trim()) ?: return@mapNotNull null
            ResultMetric(match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        return metrics.takeIf { it.size >= 2 }
    }

    private fun parseMetricRun(lines: List<String>, start: Int): ParsedMetrics? {
        val metrics = mutableListOf<ResultMetric>()
        var index = start
        while (index < lines.size) {
            val match = keyValue.matchEntire(lines[index]) ?: break
            val label = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            if (label.length > 80 || value.isBlank()) break
            metrics += ResultMetric(label, value)
            index += 1
        }
        return metrics.takeIf { it.size >= 2 }?.let { ParsedMetrics(it, index) }
    }

    private fun chartFromTable(
        table: ResultTable,
        title: String?,
        hints: ResultSourceHints,
    ): ResultChart? {
        if (table.rows.size < 3 || table.headers.size < 2) return null
        val firstHeader = table.headers.first().trim().lowercase()
        if (firstHeader in setOf("rank", "排名", "序号", "id", "#")) return null

        val xValues = table.rows.map { it.getOrNull(0).orEmpty() }
        val xLooksTemporal = xValues.count { dateLike.matches(it.trim()) } * 5 >= table.rows.size * 4
        val xLooksNumeric = xValues.count { parseNumber(it) != null } * 5 >= table.rows.size * 4
        if (!xLooksTemporal && !xLooksNumeric) return null

        val series = mutableListOf<ResultChartSeries>()
        val maxSeries = if (hints.charting) 4 else 3
        for (column in 1 until table.headers.size) {
            val parsed = table.rows.map { row -> parseNumber(row.getOrNull(column).orEmpty()) }
            if (parsed.count { it != null } * 5 < table.rows.size * 4) continue
            series += ResultChartSeries(table.headers[column], parsed)
            if (series.size >= maxSeries) break
        }
        if (series.isEmpty()) return null
        return ResultChart(title = title, xLabels = xValues, series = series)
    }

    private fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim().replace(",", "").replace("¥", "").replace("\$", "")
        if (!numberToken.matches(cleaned)) return null
        val percent = cleaned.endsWith('%')
        return cleaned.removeSuffix("%").toDoubleOrNull()?.let { value ->
            if (percent) value / 100.0 else value
        }
    }

    private fun trimBlankEdges(lines: List<String>): List<String> {
        var first = 0
        var last = lines.size
        while (first < last && lines[first].isBlank()) first += 1
        while (last > first && lines[last - 1].isBlank()) last -= 1
        return lines.subList(first, last).toList()
    }
}

object AdaptiveResultHtmlRenderer {
    fun render(document: AdaptiveResultDocument): String {
        val body = buildString {
            append("<header class=\"hero\"><div><span class=\"eyebrow\">SiftAlpha Local Result</span>")
            append("<h1>").append(escape(document.projectName)).append("</h1>")
            append("<p class=\"summary\">").append(escape(document.summary)).append("</p>")
            document.sourcePath?.let {
                append("<p class=\"source\">Source: <code>").append(escape(it)).append("</code></p>")
            }
            append("</div><span class=\"badge\">local · 127.0.0.1</span></header>")

            document.sections.forEach { section ->
                append("<section class=\"section\">")
                section.title?.let { append("<h2>").append(escape(it)).append("</h2>") }
                section.blocks.forEach { block ->
                    when (block) {
                        is AdaptiveResultBlock.Text -> renderText(block.text, this)
                        is AdaptiveResultBlock.Metrics -> renderMetrics(block.items, this)
                        is AdaptiveResultBlock.Table -> renderTable(block.table, this)
                        is AdaptiveResultBlock.Chart -> renderChart(block.chart, this)
                    }
                }
                append("</section>")
            }

            if (document.stderr.isNotBlank()) {
                append("<details class=\"diagnostic\"><summary>Program stderr / warnings</summary><pre>")
                append(escape(document.stderr))
                append("</pre></details>")
            }

            append("<details class=\"raw\"><summary>Raw program output</summary><pre>")
            append(escape(document.rawText))
            append("</pre></details>")
        }

        return """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>__TITLE__ · SiftAlpha Result</title>
<style>
:root{color-scheme:dark;background:#0f1217;color:#e8edf4;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans SC",sans-serif}
*{box-sizing:border-box}body{margin:0;background:#0f1217;color:#e8edf4}.page{max-width:1100px;margin:0 auto;padding:20px 16px 48px}
.hero{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;padding:18px 18px 16px;background:#181d25;border:1px solid #303844;border-radius:14px;margin-bottom:16px}
.eyebrow{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#8fb7d9}h1{font-size:25px;margin:5px 0 4px}.summary,.source{margin:4px 0;color:#aab6c5;font-size:13px}
.badge{white-space:nowrap;padding:6px 9px;border-radius:999px;background:#153527;color:#9be0b8;font-size:12px}
.section{background:#181d25;border:1px solid #303844;border-radius:14px;padding:16px;margin:0 0 14px;overflow:hidden}.section h2{font-size:19px;margin:0 0 13px}
pre{white-space:pre-wrap;word-break:break-word;background:#10141a;border:1px solid #2b323d;border-radius:10px;padding:12px;overflow:auto;color:#dfe7f0;line-height:1.48}
.metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:9px;margin:8px 0 12px}.metric{padding:11px;border-radius:10px;background:#111821;border:1px solid #293440}.metric .label{font-size:12px;color:#8fa0b3}.metric .value{font-size:18px;font-weight:650;margin-top:4px;word-break:break-word}
.table-wrap{overflow:auto;margin:8px 0 12px;border:1px solid #2c3540;border-radius:10px}table{width:100%;border-collapse:collapse;font-size:13px;min-width:560px}th,td{text-align:left;padding:9px 10px;border-bottom:1px solid #27303a;white-space:nowrap}th{position:sticky;top:0;background:#202733;color:#cfe3f5}tr:last-child td{border-bottom:0}
.chart{margin:10px 0 14px;padding:10px;background:#10151c;border:1px solid #29333e;border-radius:10px}.chart-title{font-size:13px;color:#a9bad0;margin:0 0 8px}.legend{display:flex;flex-wrap:wrap;gap:9px;font-size:12px;color:#aebdce;margin-top:6px}.legend span:before{content:"";display:inline-block;width:10px;height:3px;border-radius:3px;background:var(--c);margin-right:5px;vertical-align:middle}
svg{width:100%;height:auto;display:block}.axis{stroke:#4a5666;stroke-width:1}.grid{stroke:#26303b;stroke-width:1}.line{fill:none;stroke-width:2}
details{background:#151a21;border:1px solid #2c3440;border-radius:10px;padding:10px 12px;margin:12px 0}summary{cursor:pointer;color:#b9c7d6}code{color:#b8d7ef}
a{color:#84c7ff;text-decoration:none}a:hover{text-decoration:underline}
@media(max-width:600px){.hero{flex-direction:column}.badge{align-self:flex-start}.page{padding:12px 10px 34px}.section{padding:12px}h1{font-size:22px}}
</style>
</head>
<body><main class="page">__BODY__</main></body>
</html>"""
            .replace("__TITLE__", escape(document.projectName))
            .replace("__BODY__", body)
    }

    private fun renderText(text: String, out: StringBuilder) {
        out.append("<pre>").append(autoLink(text)).append("</pre>")
    }

    private fun renderMetrics(items: List<ResultMetric>, out: StringBuilder) {
        out.append("<div class=\"metric-grid\">")
        items.forEach { item ->
            out.append("<div class=\"metric\"><div class=\"label\">")
                .append(escape(item.label))
                .append("</div><div class=\"value\">")
                .append(escape(item.value))
                .append("</div></div>")
        }
        out.append("</div>")
    }

    private fun renderTable(table: ResultTable, out: StringBuilder) {
        out.append("<div class=\"table-wrap\"><table><thead><tr>")
        table.headers.forEach { out.append("<th>").append(escape(it)).append("</th>") }
        out.append("</tr></thead><tbody>")
        table.rows.forEach { row ->
            out.append("<tr>")
            table.headers.indices.forEach { index ->
                out.append("<td>").append(escape(row.getOrNull(index).orEmpty())).append("</td>")
            }
            out.append("</tr>")
        }
        out.append("</tbody></table></div>")
    }

    private fun renderChart(chart: ResultChart, out: StringBuilder) {
        val allValues = chart.series.flatMap { it.values }.filterNotNull()
        if (allValues.isEmpty()) return
        var minimum = allValues.minOrNull() ?: return
        var maximum = allValues.maxOrNull() ?: return
        if (minimum == maximum) {
            val pad = max(1.0, kotlin.math.abs(minimum) * .05)
            minimum -= pad
            maximum += pad
        }
        val width = 820.0
        val height = 270.0
        val left = 48.0
        val right = 16.0
        val top = 18.0
        val bottom = 34.0
        val plotWidth = width - left - right
        val plotHeight = height - top - bottom
        val colors = listOf("#69b7ff", "#7de3a1", "#ffc86b", "#d59cff")

        out.append("<div class=\"chart\">")
        chart.title?.let { out.append("<div class=\"chart-title\">").append(escape(it)).append("</div>") }
        out.append("<svg viewBox=\"0 0 820 270\" role=\"img\" aria-label=\"chart\">")
        for (grid in 0..4) {
            val y = top + plotHeight * grid / 4.0
            out.append("<line class=\"grid\" x1=\"").append(left)
                .append("\" y1=\"").append(format(y))
                .append("\" x2=\"").append(width - right)
                .append("\" y2=\"").append(format(y)).append("\"/>")
        }
        out.append("<line class=\"axis\" x1=\"").append(left).append("\" y1=\"")
            .append(top + plotHeight).append("\" x2=\"").append(width - right)
            .append("\" y2=\"").append(top + plotHeight).append("\"/>")

        chart.series.forEachIndexed { seriesIndex, series ->
            val points = mutableListOf<String>()
            val count = max(1, series.values.size - 1)
            series.values.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                val x = left + plotWidth * index / count.toDouble()
                val ratio = (value - minimum) / (maximum - minimum)
                val y = top + plotHeight * (1.0 - ratio)
                points += format(x) + "," + format(y)
            }
            if (points.size >= 2) {
                out.append("<polyline class=\"line\" stroke=\"")
                    .append(colors[seriesIndex % colors.size])
                    .append("\" points=\"").append(points.joinToString(" ")).append("\"/>")
            }
        }

        val firstLabel = chart.xLabels.firstOrNull().orEmpty()
        val lastLabel = chart.xLabels.lastOrNull().orEmpty()
        out.append("<text x=\"").append(left).append("\" y=\"260\" fill=\"#8392a5\" font-size=\"11\">")
            .append(escape(firstLabel)).append("</text>")
        out.append("<text x=\"804\" y=\"260\" text-anchor=\"end\" fill=\"#8392a5\" font-size=\"11\">")
            .append(escape(lastLabel)).append("</text>")
        out.append("</svg><div class=\"legend\">")
        chart.series.forEachIndexed { index, series ->
            out.append("<span style=\"--c:").append(colors[index % colors.size]).append("\">")
                .append(escape(series.name)).append("</span>")
        }
        out.append("</div></div>")
    }

    private fun autoLink(text: String): String {
        val escaped = escape(text)
        val urlRegex = Regex("""https?://[^\s<]+""")
        return urlRegex.replace(escaped) { match ->
            val url = match.value.trimEnd('.', ',', ';', ')')
            "<a href=\"" + escapeAttribute(url) + "\">" + url + "</a>" + match.value.removePrefix(url)
        }
    }

    private fun format(value: Double): String = "%.2f".format(java.util.Locale.US, value)

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun escapeAttribute(value: String): String = escape(value)
}

object ResultFingerprint {
    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
