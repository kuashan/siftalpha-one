package com.siftalpha.macos

import java.awt.Color
import java.awt.Font

object MacDesignTokens {
    val background = Color(0xF7, 0xF8, 0xFC)
    val surface = Color.WHITE
    val surfaceSubtle = Color(0xEF, 0xF2, 0xF8)
    val foreground = Color(0x11, 0x18, 0x27)
    val muted = Color(0x47, 0x55, 0x69)
    val primary = Color(0x4F, 0x46, 0xE5)
    val primaryPressed = Color(0x43, 0x38, 0xCA)
    val success = Color(0x16, 0x7A, 0x4B)
    val warning = Color(0xB3, 0x5C, 0x00)
    val error = Color(0xB5, 0x3A, 0x49)
    val border = Color(0xDC, 0xE2, 0xEC)

    val titleFont: Font = Font("SansSerif", Font.BOLD, 24)
    val headingFont: Font = Font("SansSerif", Font.BOLD, 18)
    val bodyFont: Font = Font("SansSerif", Font.PLAIN, 14)
    val smallFont: Font = Font("SansSerif", Font.PLAIN, 12)
}
