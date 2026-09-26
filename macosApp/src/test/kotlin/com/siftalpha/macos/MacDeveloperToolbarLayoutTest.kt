package com.siftalpha.macos

import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacDeveloperToolbarLayoutTest {
    private val buttonWidths = listOf(88, 70, 150, 60, 60, 90, 60, 90, 90, 90, 92, 88, 90)

    @Test
    fun normalWidthUsesTwoRowsAndNarrowWidthUsesThreeRows() {
        assertEquals(2, MacDeveloperToolbarLayoutPolicy.rowsForWidth(650, buttonWidths))
        assertEquals(3, MacDeveloperToolbarLayoutPolicy.rowsForWidth(430, buttonWidths))
    }

    @Test
    fun veryNarrowWidthStillProducesRowsWithoutZeroColumns() {
        val rows = MacDeveloperToolbarLayoutPolicy.rowRanges(1, buttonWidths)

        assertEquals(buttonWidths.size, rows.sumOf { it.count() })
        assertTrue(rows.all { it.count() >= 1 })
    }

    @Test
    fun wrapLayoutKeepsAllButtonsAndReflowsWithoutChangingOrder() {
        val panel = JPanel(MacDeveloperToolbarWrapLayout())
        buttonWidths.forEachIndexed { index, width ->
            panel.add(JButton(index.toString()).apply { preferredSize = Dimension(width, 30) })
        }

        panel.setSize(650, 100)
        panel.doLayout()
        assertEquals(buttonWidths.size, panel.componentCount)
        assertEquals(2, panel.components.map { it.y }.distinct().size)
        assertTrue(
            panel.components.toList().zipWithNext().all { (first, second) ->
                first.x <= second.x || first.y < second.y
            },
        )

        panel.setSize(430, 160)
        panel.doLayout()
        assertEquals(3, panel.components.map { it.y }.distinct().size)
        assertEquals(buttonWidths.size, panel.componentCount)
    }
}
