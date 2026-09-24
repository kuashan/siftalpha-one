package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import com.siftalpha.studio.platform.PlatformCapability
import com.siftalpha.studio.platform.PlatformCapabilitySnapshot
import com.siftalpha.studio.platform.StandardPlatformCapabilities
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

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
        capability.id + " = " + snapshot.availabilityOf(capability)
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
    val managed = MacManagedPythonRuntime.locate()
    println(
        "SIFTALPHA_MANAGED_PYTHON=" +
            if (managed?.available == true) {
                "AVAILABLE|" + managed.pythonExecutable.absolutePath + "|" + managed.version().orEmpty()
            } else {
                "UNAVAILABLE"
            },
    )
}

private fun createWindow(
    capabilitySnapshot: PlatformCapabilitySnapshot,
    discovery: List<MacHostToolSnapshot>,
): JFrame {
    val listModel = DefaultListModel<String>()
    val fs = MacProjectFilesystem()
    val managedPython = MacManagedPythonRuntime.locate()
    val processControl = MacProjectProcessControl()
    val environmentManager = MacProjectEnvironmentManager(processControl, managedPython)
    val coordinator = MacProjectWorkflowCoordinator(processControl, environmentManager)

    var currentProject: MacImportedProject? = null
    var currentSnapshot: MacProjectSnapshot? = null
    var currentPlan: MacProjectEnvironmentPlan? = null

    fun showLines(lines: List<String>) {
        listModel.clear()
        lines.forEach(listModel::addElement)
    }

    fun statusLines(projectId: String): List<String> {
        val status = coordinator.status(projectId)
        return listOf(
            "SIFTALPHA_M4_LIFECYCLE=" + status.lifecycle,
            "SIFTALPHA_M4_ENV_READY=" + status.environmentReady,
            "SIFTALPHA_M4_PROCESS_STATE=" + status.processState,
            "SIFTALPHA_M4_WEB_URL=" + status.webEndpoint?.url.orEmpty(),
        )
    }

    capabilityLines(capabilitySnapshot).forEach(listModel::addElement)
    discovery.forEach { tool ->
        listModel.addElement(
            tool.kind.id + " = " + tool.availability +
                (tool.version?.let { version -> " — " + version } ?: ""),
        )
    }
    listModel.addElement(
        "managed_python = " +
            if (managedPython?.available == true) {
                "AVAILABLE — " + managedPython.version()
            } else {
                "UNAVAILABLE"
            },
    )

    val title = JLabel("SiftAlpha X", SwingConstants.CENTER).apply {
        border = BorderFactory.createEmptyBorder(20, 20, 8, 20)
    }
    val subtitle = JLabel(
        "M4 Project Workflow — Prepare + Run + Observe + Stop/Restart",
        SwingConstants.CENTER,
    ).apply {
        border = BorderFactory.createEmptyBorder(0, 20, 16, 20)
    }
    val workflowStatus = JLabel("M4.2: import a project")

    val prepareButton = JButton("Prepare").apply { isEnabled = false }
    val runButton = JButton("Run").apply { isEnabled = false }
    val logsButton = JButton("Logs").apply { isEnabled = false }
    val stopButton = JButton("Stop").apply { isEnabled = false }
    val restartButton = JButton("Restart").apply { isEnabled = false }
    val openWebButton = JButton("Open Web").apply { isEnabled = false }

    val importButton = JButton("Import Project Folder").apply {
        addActionListener {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
                dialogTitle = "Import SiftAlpha X Project"
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                workflowStatus.text = "M4.2: detecting..."
                isEnabled = false
                Thread {
                    val outcome = runCatching {
                        val project = fs.importDirectory(chooser.selectedFile)
                        val projectSnapshot = MacProjectSnapshotBuilder(fs).build(project)
                        val plan = MacProjectWorkflowPlanner.plan(
                            snapshot = projectSnapshot,
                            hostTools = discovery,
                            managedPythonExecutable = managedPython
                                ?.takeIf { it.available }
                                ?.pythonExecutable
                                ?.absolutePath,
                        )
                        Triple(project, projectSnapshot, plan)
                    }
                    SwingUtilities.invokeLater {
                        outcome.onSuccess { result ->
                            currentProject = result.first
                            currentSnapshot = result.second
                            currentPlan = result.third
                            coordinator.attach(
                                MacWorkflowContext(
                                    project = result.first,
                                    snapshot = result.second,
                                    plan = result.third,
                                ),
                            )
                            showLines(result.third.diagnosticLines())
                            workflowStatus.text = "M4.2: " + result.third.status
                            prepareButton.isEnabled =
                                result.third.status != MacProjectPlanStatus.BLOCKED
                            logsButton.isEnabled = true
                            stopButton.isEnabled = true
                        }.onFailure { error ->
                            workflowStatus.text = "M4.2: FAIL — " +
                                (error.message ?: error.javaClass.simpleName)
                        }
                        isEnabled = true
                    }
                }.start()
            }
        }
    }

    prepareButton.addActionListener {
        val project = currentProject ?: return@addActionListener
        prepareButton.isEnabled = false
        workflowStatus.text = "M4.2: PREPARING"
        Thread {
            val result = coordinator.prepare(project.projectId)
            SwingUtilities.invokeLater {
                showLines(
                    result.lines +
                        listOf("DETAIL=" + result.detail.orEmpty()) +
                        statusLines(project.projectId),
                )
                workflowStatus.text =
                    if (result.success) "M4.2: READY_TO_RUN" else "M4.2: PREPARE_FAILED"
                prepareButton.isEnabled = true
                runButton.isEnabled = result.success
                restartButton.isEnabled = result.success
                logsButton.isEnabled = true
                stopButton.isEnabled = true
            }
        }.start()
    }

    runButton.addActionListener {
        val project = currentProject ?: return@addActionListener
        workflowStatus.text = "M4.2: STARTING"
        Thread {
            val started = coordinator.start(project.projectId)
            Thread.sleep(250)
            SwingUtilities.invokeLater {
                showLines(statusLines(project.projectId) + coordinator.logs(project.projectId).lines())
                workflowStatus.text =
                    if (started) "M4.2: " + coordinator.status(project.projectId).lifecycle
                    else "M4.2: RUN_FAILED"
                openWebButton.isEnabled = coordinator.webEndpoint(project.projectId) != null
                stopButton.isEnabled = true
                logsButton.isEnabled = true
            }
        }.start()
    }

    logsButton.addActionListener {
        val project = currentProject ?: return@addActionListener
        Thread {
            val endpoint = coordinator.webEndpoint(project.projectId)
            val lines = statusLines(project.projectId) +
                listOf("SIFTALPHA_M4_WEB_URL=" + endpoint?.url.orEmpty()) +
                coordinator.logs(project.projectId).lines()
            SwingUtilities.invokeLater {
                showLines(lines)
                workflowStatus.text = "M4.2: " + coordinator.status(project.projectId).lifecycle
                openWebButton.isEnabled = endpoint != null
            }
        }.start()
    }

    stopButton.addActionListener {
        val project = currentProject ?: return@addActionListener
        workflowStatus.text = "M4.2: STOPPING"
        Thread {
            val stopped = coordinator.stop(project.projectId)
            SwingUtilities.invokeLater {
                showLines(statusLines(project.projectId) + coordinator.logs(project.projectId).lines())
                workflowStatus.text = if (stopped) "M4.2: STOPPED" else "M4.2: STOP_FAILED"
                openWebButton.isEnabled = false
                runButton.isEnabled = true
            }
        }.start()
    }

    restartButton.addActionListener {
        val project = currentProject ?: return@addActionListener
        workflowStatus.text = "M4.2: RESTARTING"
        Thread {
            val restarted = coordinator.restart(project.projectId)
            Thread.sleep(250)
            SwingUtilities.invokeLater {
                showLines(statusLines(project.projectId) + coordinator.logs(project.projectId).lines())
                workflowStatus.text =
                    if (restarted) "M4.2: " + coordinator.status(project.projectId).lifecycle
                    else "M4.2: RESTART_FAILED"
                openWebButton.isEnabled = coordinator.webEndpoint(project.projectId) != null
            }
        }.start()
    }

    openWebButton.addActionListener {
        val project = currentProject ?: return@addActionListener
        val endpoint = coordinator.webEndpoint(project.projectId) ?: return@addActionListener
        if (Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().browse(URI(endpoint.url)) }
        }
    }

    val controls = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
        add(importButton)
        add(prepareButton)
        add(runButton)
        add(logsButton)
        add(openWebButton)
        add(stopButton)
        add(restartButton)
    }

    val content = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(12, 20, 20, 20)
        add(JScrollPane(JList(listModel)), BorderLayout.CENTER)
        add(controls, BorderLayout.SOUTH)
    }

    return JFrame("SiftAlpha X").apply {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        minimumSize = Dimension(980, 560)
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

    val capabilitySnapshot = MacPlatformCapabilities.snapshot()
    val discovery = MacHostRuntimeDiscovery().discoverAll()
    if ("--probe" in args) {
        printProbe(capabilitySnapshot, discovery)
        return
    }
    if ("--m3-process-probe" in args) {
        val result = MacM3SelfTest.run()
        println("SIFTALPHA_M3_PROCESS_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M3_PROCESS=" + line) }
        if (!result.passed) error("M3 process probe failed")
        return
    }
    if ("--m4-plan-probe" in args) {
        val result = MacM4PlanSelfTest.run()
        println("SIFTALPHA_M4_PLAN_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M4_PLAN=" + line) }
        if (!result.passed) error("M4 plan probe failed")
        return
    }
    if ("--m4-workflow-probe" in args) {
        val result = MacM42SelfTest.run()
        println("SIFTALPHA_M42_WORKFLOW_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M42=" + line) }
        if (!result.passed) error("M4.2 workflow probe failed")
        return
    }

    SwingUtilities.invokeLater {
        runCatching {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        }
        createWindow(capabilitySnapshot, discovery).isVisible = true
    }
}
