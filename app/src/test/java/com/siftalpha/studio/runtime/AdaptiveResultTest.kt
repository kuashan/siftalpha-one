package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveResultTest {

    @Test
    fun externalRuntimeEnvelopeIsRemovedButProgramOutputIsPreserved() {
        val extracted = RuntimeProgramOutputExtractor.extract(
            """
                SIFTALPHA_EXTERNAL_ACTIVITY_OPERATION=logs
                === SiftAlpha Project Log ===
                --- state ---
                STATE=EXITED
                EXIT_CODE=0
                === SiftAlpha Studio Runtime ===
                STARTED_AT=2026-09-20 12:59:10
                SIFTALPHA_LAUNCH_KIND=PYTHON_FILE
                SIFTALPHA_LAUNCH_ENTRYPOINT=run_all_strategies.py
                SIFTALPHA_LAUNCH_ARGUMENT_COUNT=3
                发现 16 个策略文件
                标的: SH 600519 | K线: 2000 | 资金: 1,000,000
                SIFTALPHA_PROCESS_EXIT=0
                EXITED_AT=2026-09-20 12:59:41
            """.trimIndent(),
        )

        assertEquals("run_all_strategies.py", extracted.sourcePath)
        assertTrue(extracted.text.contains("发现 16 个策略文件"))
        assertTrue(extracted.text.contains("标的: SH 600519"))
        assertFalse(extracted.text.contains("SIFTALPHA_"))
        assertFalse(extracted.text.contains("STATE=EXITED"))
    }

    @Test
    fun easyTdxStyleOutputBecomesSectionsMetricsAndTable() {
        val extracted = ExtractedProgramOutput(
            text = """
                标的: SH 600519 | K线: 2000 | 资金: 1,000,000

                ================================================================================
                [BEST] 最佳策略: bollinger_breakout
                ================================================================================
                总收益率:   227.84%
                年化收益:   16.15%
                最大回撤:   43.46%
                夏普比率:   0.63

                ================================================================================
                [*] 策略绩效排名
                ================================================================================
                  排名  策略                     总收益率    最大回撤
                -----------------------------------------------------
                   1  bollinger_breakout       227.84%    43.46%
                   2  cci_breakout             136.41%    38.38%
                   3  bias_reversal            126.07%    34.47%
            """.trimIndent(),
            sourcePath = "run_all_strategies.py",
        )

        val doc = AdaptiveResultAnalyzer.analyze(
            projectName = "easy_tdx-main",
            extracted = extracted,
            sourceText = "import pandas as pd\nimport matplotlib.pyplot as plt",
        )

        assertTrue(doc.sections.size >= 2)
        assertTrue(doc.metricGroupCount >= 2)
        assertTrue(doc.tableCount >= 1)
        assertEquals("run_all_strategies.py", doc.sourcePath)
        assertTrue(doc.sourceHints.tabular)
        assertTrue(doc.sourceHints.charting)
    }

    @Test
    fun timeSeriesCsvGetsAChartWithoutInventingData() {
        val doc = AdaptiveResultAnalyzer.analyze(
            projectName = "series",
            extracted = ExtractedProgramOutput(
                text = """
                    date,equity,drawdown
                    2026-01-01,100,0
                    2026-01-02,102,-0.01
                    2026-01-03,105,-0.02
                    2026-01-04,103,-0.03
                """.trimIndent(),
            ),
        )

        assertEquals(1, doc.tableCount)
        assertEquals(1, doc.chartCount)
    }

    @Test
    fun nonSeriesRankingTableDoesNotBecomeAChart() {
        val doc = AdaptiveResultAnalyzer.analyze(
            projectName = "ranking",
            extracted = ExtractedProgramOutput(
                text = """
                    排名  策略      收益率
                    ---------------------
                    1    alpha     10%
                    2    beta      8%
                    3    gamma     7%
                """.trimIndent(),
            ),
        )

        assertEquals(1, doc.tableCount)
        assertEquals(0, doc.chartCount)
    }

    @Test
    fun htmlRendererEscapesProgramMarkup() {
        val doc = AdaptiveResultAnalyzer.analyze(
            projectName = "safe",
            extracted = ExtractedProgramOutput("<script>alert(1)</script>"),
        )
        val html = AdaptiveResultHtmlRenderer.render(doc)

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertNotNull(html)
    }
}
