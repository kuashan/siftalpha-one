package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import com.siftalpha.studio.platform.PlatformCapability
import com.siftalpha.studio.platform.PlatformCapabilitySnapshot
import com.siftalpha.studio.platform.StandardPlatformCapabilities
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

/**
 * M2 macOS Platform Adapter（苹果平台适配层） capability publication.
 *
 * M2 intentionally does not probe host runtimes. Actual Python / Node.js / Bun / Git / container
 * discovery belongs to M3 Host Runtime Provider（主机运行提供者）.
 */
object MacPlatformCapabilities {
    val publishedCapabilities: List<PlatformCapability> = listOf(
        StandardPlatformCapabilities.HOST_PROCESS_EXECUTION,
        StandardPlatformCapabilities.SECURE_SECRET_STORAGE,
        StandardPlatformCapabilities.CONTAINER_RUNTIME,
    )

    fun snapshot(): PlatformCapabilitySnapshot =
        PlatformCapabilitySnapshot(
            publishedCapabilities.associateWith { CapabilityAvailability.UNKNOWN },
        )
}

private fun capabilityLines(snapshot: PlatformCapabilitySnapshot): List<String> =
    MacPlatformCapabilities.publishedCapabilities.map { capability ->
        "${capability.id} = ${snapshot.availabilityOf(capability)}"
    }

private fun printProbe(snapshot: PlatformCapabilitySnapshot) {
    println("SIFTALPHA_MACOS_HOST=READY")
    println("SIFTALPHA_CORE_LOAD=PASS")
    capabilityLines(snapshot).forEach { line ->
        println("SIFTALPHA_CAPABILITY=$line")
    }
}

private fun createWindow(snapshot: PlatformCapabilitySnapshot): JFrame {
    val listModel = DefaultListModel<String>()
    capabilityLines(snapshot).forEach(listModel::addElement)

    val title = JLabel("SiftAlpha", SwingConstants.CENTER).apply {
        border = BorderFactory.createEmptyBorder(20, 20, 8, 20)
    }
    val subtitle = JLabel(
        "M2 macOS Host Skeleton — Core capability snapshot",
        SwingConstants.CENTER,
    ).apply {
        border = BorderFactory.createEmptyBorder(0, 20, 16, 20)
    }

    val content = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(12, 20, 20, 20)
        add(JScrollPane(JList(listModel)), BorderLayout.CENTER)
    }

    return JFrame("SiftAlpha").apply {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        minimumSize = Dimension(640, 420)
        layout = BorderLayout()
        add(title, BorderLayout.NORTH)
        add(subtitle, BorderLayout.CENTER)
        add(content, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(null)
    }
}

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "SiftAlpha")

    val snapshot = MacPlatformCapabilities.snapshot()
    if ("--probe" in args) {
        printProbe(snapshot)
        return
    }

    SwingUtilities.invokeLater {
        runCatching {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        }
        createWindow(snapshot).isVisible = true
    }
}
