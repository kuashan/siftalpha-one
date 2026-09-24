package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import com.siftalpha.studio.platform.PlatformCapability
import com.siftalpha.studio.platform.PlatformCapabilitySnapshot
import com.siftalpha.studio.platform.StandardPlatformCapabilities
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
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
 * macOS Platform Adapter（苹果平台适配层） capability publication.
 *
 * Host runtime discovery is provided separately by MacHostRuntimeDiscovery.
 */
object MacPlatformCapabilities {
    val publishedCapabilities: List<PlatformCapability> = listOf(
        StandardPlatformCapabilities.HOST_PROCESS_EXECUTION,
        StandardPlatformCapabilities.SECURE_SECRET_STORAGE,
        StandardPlatformCapabilities.CONTAINER_RUNTIME,
    )

    fun snapshot(): PlatformCapabilitySnapshot =
        PlatformCapabilitySnapshot(
            publishedCapabilities.associateWith { capability ->
                if (capability == StandardPlatformCapabilities.HOST_PROCESS_EXECUTION) {
                    CapabilityAvailability.AVAILABLE
                } else {
                    CapabilityAvailability.UNKNOWN
                }
            },
        )
}

private fun capabilityLines(snapshot: PlatformCapabilitySnapshot): List<String> =
    MacPlatformCapabilities.publishedCapabilities.map { capability ->
        "${capability.id} = ${snapshot.availabilityOf(capability)}"
    }

private fun printProbe(
    snapshot: PlatformCapabilitySnapshot,
    discovery: List<MacHostToolSnapshot>,
) {
    println("SIFTALPHA_MACOS_HOST=READY")
    println("SIFTALPHA_CORE_LOAD=PASS")
    capabilityLines(snapshot).forEach { line ->
        println("SIFTALPHA_CAPABILITY=" + line)
    }
    discovery.forEach { tool ->
        println(
            "SIFTALPHA_HOST_TOOL=" +
                listOf(
                    tool.kind.id,
                    tool.availability.name,
                    tool.executablePath.orEmpty(),
                    tool.version.orEmpty(),
                ).joinToString("|"),
        )
    }
}

private fun createWindow(
    snapshot: PlatformCapabilitySnapshot,
    discovery: List<MacHostToolSnapshot>,
): JFrame {
    val listModel = DefaultListModel<String>()
    capabilityLines(snapshot).forEach(listModel::addElement)
    discovery.forEach { tool ->
        listModel.addElement(
            tool.kind.id + " = " + tool.availability +
                (tool.version?.let { version -> " — " + version } ?: ""),
        )
    }

    val title = JLabel("SiftAlpha X", SwingConstants.CENTER).apply {
        border = BorderFactory.createEmptyBorder(20, 20, 8, 20)
    }
    val subtitle = JLabel(
        "M3 Host Runtime Provider — runtime discovery + project process control",
        SwingConstants.CENTER,
    ).apply {
        border = BorderFactory.createEmptyBorder(0, 20, 16, 20)
    }

    val selfTestStatus = JLabel("Process self-test: not run")
    val selfTestButton = JButton("Run M3 Process Self-Test").apply {
        addActionListener {
            isEnabled = false
            selfTestStatus.text = "Process self-test: running..."
            Thread {
                val result = MacM3SelfTest.run()
                SwingUtilities.invokeLater {
                    selfTestStatus.text = if (result.passed) {
                        "Process self-test: PASS"
                    } else {
                        "Process self-test: FAIL — " + result.lines.joinToString("; ")
                    }
                    isEnabled = true
                }
            }.start()
        }
    }
    val controls = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
        add(selfTestButton)
        add(selfTestStatus)
    }

    val content = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(12, 20, 20, 20)
        add(JScrollPane(JList(listModel)), BorderLayout.CENTER)
        add(controls, BorderLayout.SOUTH)
    }

    return JFrame("SiftAlpha X").apply {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        minimumSize = Dimension(760, 480)
        layout = BorderLayout()
        add(title, BorderLayout.NORTH)
        add(subtitle, BorderLayout.CENTER)
        add(content, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(null)
    }
}

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "SiftAlpha X")

    val snapshot = MacPlatformCapabilities.snapshot()
    val discovery = MacHostRuntimeDiscovery().discoverAll()
    if ("--probe" in args) {
        printProbe(snapshot, discovery)
        return
    }
    if ("--m3-process-probe" in args) {
        val result = MacM3SelfTest.run()
        println("SIFTALPHA_M3_PROCESS_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M3_PROCESS=" + line) }
        if (!result.passed) error("M3 process probe failed")
        return
    }

    SwingUtilities.invokeLater {
        runCatching {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        }
        createWindow(snapshot, discovery).isVisible = true
    }
}
