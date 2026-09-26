package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessState
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.LayoutManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

internal object MacDeveloperToolbarLayoutPolicy {
    fun rowRanges(
        availableWidth: Int,
        preferredWidths: List<Int>,
        horizontalGap: Int = 8,
    ): List<IntRange> {
        if (preferredWidths.isEmpty()) return emptyList()

        val width = availableWidth.coerceAtLeast(1)
        val gap = horizontalGap.coerceAtLeast(0)
        val rows = mutableListOf<IntRange>()
        var rowStart = 0
        var rowWidth = 0

        preferredWidths.forEachIndexed { index, rawPreferredWidth ->
            val preferredWidth = rawPreferredWidth.coerceAtLeast(1)
            val nextWidth = if (index == rowStart) {
                preferredWidth
            } else {
                rowWidth + gap + preferredWidth
            }
            if (index > rowStart && nextWidth > width) {
                rows += rowStart until index
                rowStart = index
                rowWidth = preferredWidth
            } else {
                rowWidth = nextWidth
            }
        }
        rows += rowStart until preferredWidths.size
        return rows
    }

    fun rowsForWidth(
        availableWidth: Int,
        preferredWidths: List<Int>,
        horizontalGap: Int = 8,
    ): Int = rowRanges(availableWidth, preferredWidths, horizontalGap).size
}

internal class MacDeveloperToolbarWrapLayout(
    private val horizontalGap: Int = 8,
    private val verticalGap: Int = 8,
) : LayoutManager {
    override fun addLayoutComponent(name: String?, component: Component?) = Unit

    override fun removeLayoutComponent(component: Component?) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension {
        val preferredWidths = preferredWidths(parent)
        if (preferredWidths.isEmpty()) return Dimension()
        val targetWidth = preferredWidths
            .take(7)
            .sum() + horizontalGap * (minOf(7, preferredWidths.size) - 1).coerceAtLeast(0)
        val rows = MacDeveloperToolbarLayoutPolicy.rowRanges(targetWidth, preferredWidths, horizontalGap)
        val rowHeight = parent.components.maxOf { it.preferredSize.height }
        val insets = parent.insets
        return Dimension(
            targetWidth + insets.left + insets.right,
            rows.size * rowHeight + (rows.size - 1).coerceAtLeast(0) * verticalGap +
                insets.top + insets.bottom,
        )
    }

    override fun minimumLayoutSize(parent: Container): Dimension {
        val insets = parent.insets
        val component = parent.components.firstOrNull() ?: return Dimension()
        return Dimension(
            component.minimumSize.width + insets.left + insets.right,
            component.minimumSize.height + insets.top + insets.bottom,
        )
    }

    override fun layoutContainer(parent: Container) {
        val components = parent.components
        if (components.isEmpty()) return

        val insets = parent.insets
        val availableWidth = (parent.width - insets.left - insets.right).coerceAtLeast(1)
        val preferredWidths = preferredWidths(parent)
        val rows = MacDeveloperToolbarLayoutPolicy.rowRanges(
            availableWidth = availableWidth,
            preferredWidths = preferredWidths,
            horizontalGap = horizontalGap,
        )
        var y = insets.top
        rows.forEach { range ->
            val rowHeight = range.maxOf { components[it].preferredSize.height }
            val naturalRowWidth = range.sumOf { preferredWidths[it] } +
                horizontalGap * (range.count() - 1).coerceAtLeast(0)
            var x = insets.left + (availableWidth - naturalRowWidth).coerceAtLeast(0)
            range.forEach { index ->
                val component = components[index]
                val componentWidth = preferredWidths[index]
                    .coerceAtMost(availableWidth)
                    .coerceAtLeast(1)
                component.setBounds(x, y, componentWidth, rowHeight)
                x += componentWidth + horizontalGap
            }
            y += rowHeight + verticalGap
        }
    }

    private fun preferredWidths(parent: Container): List<Int> =
        parent.components.map { it.preferredSize.width.coerceAtLeast(1) }
}

class MacDeveloperModeWindow(
    private val controller: MacProductController,
    private val initialProjectId: String? = null,
    private val onReturnToNormal: () -> Unit,
) {
    private val frame = JFrame("SiftAlpha X — 开发者模式")
    private val projectModel = DefaultListModel<MacProductProject>()
    private val projectList = JList(projectModel)
    private val factsArea = diagnosticArea()
    private val stdoutArea = diagnosticArea()
    private val stderrArea = diagnosticArea()
    private val combinedArea = diagnosticArea()
    private val environmentArea = diagnosticArea()

    private val importButton = JButton("导入项目")
    private val prepareButton = JButton("准备")
    private val installEnvironmentButton = JButton("安装/修复环境")
    private val runButton = JButton("运行")
    private val stopButton = JButton("停止")
    private val restartButton = JButton("重新运行")
    private val refreshButton = JButton("刷新")
    private val configureButton = JButton("项目配置")
    private val cleanButton = JButton("清理环境")
    private val storageButton = JButton("运行空间")
    private val removeButton = JButton("移除项目")
    private val copyButton = JButton("复制日志")
    private val normalButton = JButton("普通模式")
    private val emptyProjectHint = JLabel("尚未导入项目，请点击“导入项目”开始。")

    private val refreshTimer = Timer(900) { refreshLiveState() }

    @Volatile
    private var selectedProjectId: String? = initialProjectId

    @Volatile
    private var refreshInFlight = false

    fun show(): JFrame {
        configureFrame()
        refreshProjects(initialProjectId)
        refreshTimer.start()
        return frame.apply { isVisible = true }
    }

    private fun configureFrame() {
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.minimumSize = Dimension(1100, 700)
        frame.preferredSize = Dimension(1240, 800)
        frame.contentPane.background = MacDesignTokens.background
        frame.layout = BorderLayout()
        frame.add(buildToolbar(), BorderLayout.NORTH)

        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildProjectList(),
            buildDiagnostics(),
        ).apply {
            dividerLocation = 280
            resizeWeight = 0.0
            border = null
        }
        frame.add(split, BorderLayout.CENTER)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent?) {
                returnToNormal()
            }
        })
    }

    private fun buildToolbar(): JPanel = JPanel(BorderLayout()).apply {
        background = MacDesignTokens.surface
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, MacDesignTokens.border),
            BorderFactory.createEmptyBorder(10, 14, 10, 14),
        )

        add(JLabel("开发者模式").apply {
            font = MacDesignTokens.headingFont
            foreground = MacDesignTokens.foreground
        }, BorderLayout.WEST)

        val actions = JPanel(MacDeveloperToolbarWrapLayout()).apply {
            isOpaque = false
            add(importButton)
            add(prepareButton)
            add(installEnvironmentButton)
            add(runButton)
            add(stopButton)
            add(restartButton)
            add(refreshButton)
            add(configureButton)
            add(cleanButton)
            add(storageButton)
            add(removeButton)
            add(copyButton)
            add(normalButton)
        }
        add(actions, BorderLayout.CENTER)

        importButton.addActionListener { importProject() }
        prepareButton.addActionListener { runOperation { id -> controller.prepare(id).success } }
        installEnvironmentButton.addActionListener {
            runOperation(controller::installRecommendedContainerAndPrepare)
        }
        runButton.addActionListener { runStartWithConfiguration() }
        stopButton.addActionListener { runOperation(controller::stop) }
        restartButton.addActionListener { runOperation(controller::restart) }
        refreshButton.addActionListener { refreshSelectedProject() }
        configureButton.addActionListener {
            selectedProjectId?.let { id ->
                MacProjectConfigurationDialog.show(frame, controller, id)
            }
        }
        cleanButton.addActionListener { clearSelectedEnvironment() }
        storageButton.addActionListener { MacRuntimeStorageDialog.show(frame, controller) }
        removeButton.addActionListener { removeSelectedProject() }
        copyButton.addActionListener { copyCombinedLogs() }
        normalButton.addActionListener { returnToNormal() }
    }

    private fun buildProjectList(): JPanel = JPanel(BorderLayout()).apply {
        background = MacDesignTokens.surface
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, MacDesignTokens.border)

        add(JLabel("项目").apply {
            font = MacDesignTokens.headingFont
            foreground = MacDesignTokens.foreground
            border = BorderFactory.createEmptyBorder(14, 14, 10, 14)
        }, BorderLayout.NORTH)

        projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        projectList.background = MacDesignTokens.surface
        projectList.fixedCellHeight = 54
        projectList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus,
                ) as JLabel
                val project = value as? MacProductProject
                if (project != null) {
                    val state = controller.view(project.projectId)?.workflow?.lifecycle?.name ?: "UNKNOWN"
                    val runtime = if (project.isCompose) "compose" else (project.runtime?.id ?: "未知")
                    label.text = "<html><b>" + escape(project.name) + "</b><br>" +
                        "<span style='color:#64748B'>" +
                        escape(runtime) + " · " + escape(state) +
                        "</span></html>"
                }
                label.font = MacDesignTokens.bodyFont
                label.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
                return label
            }
        }
        projectList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                selectedProjectId = projectList.selectedValue?.projectId
                refreshSelectedAsync()
            }
        }
        add(JScrollPane(projectList).apply { border = null }, BorderLayout.CENTER)

        emptyProjectHint.horizontalAlignment = SwingConstants.CENTER
        emptyProjectHint.font = MacDesignTokens.smallFont
        emptyProjectHint.foreground = MacDesignTokens.muted
        emptyProjectHint.border = BorderFactory.createEmptyBorder(10, 10, 14, 10)
        add(emptyProjectHint, BorderLayout.SOUTH)
    }

    private fun buildDiagnostics(): JPanel = JPanel(BorderLayout()).apply {
        background = MacDesignTokens.background
        border = BorderFactory.createEmptyBorder(16, 18, 18, 18)

        val header = JLabel("共享运行状态").apply {
            font = MacDesignTokens.titleFont
            foreground = MacDesignTokens.foreground
            border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
        }
        add(header, BorderLayout.NORTH)

        val body = JPanel(GridLayout(1, 2, 12, 0)).apply {
            isOpaque = false
        }

        body.add(JScrollPane(factsArea).apply {
            border = BorderFactory.createTitledBorder("项目 / 运行时 / Web 信息")
        })

        val tabs = JTabbedPane().apply {
            addTab("环境准备", JScrollPane(environmentArea))
            addTab("综合日志", JScrollPane(combinedArea))
            addTab("标准输出 stdout", JScrollPane(stdoutArea))
            addTab("标准错误 stderr", JScrollPane(stderrArea))
        }
        body.add(tabs)
        add(body, BorderLayout.CENTER)
    }

    private fun importProject() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            dialogTitle = "导入 SiftAlpha X 项目"
        }
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return

        importButton.isEnabled = false
        Thread {
            val outcome = runCatching { controller.importProject(chooser.selectedFile) }
            SwingUtilities.invokeLater {
                importButton.isEnabled = true
                outcome.onSuccess { project ->
                    selectedProjectId = project.projectId
                    refreshProjects(project.projectId)
                }.onFailure { error ->
                    JOptionPane.showMessageDialog(
                        frame,
                        "无法导入这个项目。\n" + (error.message ?: "未知错误"),
                        "导入失败",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }.start()
    }

    private fun refreshProjects(preferredProjectId: String? = selectedProjectId) {
        val projects = controller.projects()
        projectModel.clear()
        projects.forEach(projectModel::addElement)
        emptyProjectHint.isVisible = projects.isEmpty()

        val target = preferredProjectId
        if (target != null) {
            val index = (0 until projectModel.size())
                .firstOrNull { projectModel.get(it).projectId == target }
            if (index != null) {
                projectList.selectedIndex = index
                selectedProjectId = target
            }
        }
        if (projectList.selectedIndex < 0 && projectModel.size() > 0) {
            projectList.selectedIndex = 0
            selectedProjectId = projectModel.get(0).projectId
        }
        refreshSelectedAsync()
    }

    private fun refreshSelectedProject() {
        val id = selectedProjectId ?: return
        refreshButton.isEnabled = false
        Thread {
            runCatching { controller.refreshProject(id) }
            SwingUtilities.invokeLater {
                refreshButton.isEnabled = true
                refreshProjects(id)
            }
        }.start()
    }

    private fun refreshLiveState() {
        if (!frame.isVisible || refreshInFlight) return
        refreshSelectedAsync()
    }

    private fun refreshSelectedAsync() {
        if (refreshInFlight) return
        val id = selectedProjectId
        if (id == null) {
            render(null)
            return
        }
        refreshInFlight = true
        Thread {
            val view = controller.developerView(id)
            SwingUtilities.invokeLater {
                refreshInFlight = false
                if (selectedProjectId == id) {
                    render(view)
                    projectList.repaint()
                }
            }
        }.start()
    }

    private fun render(view: MacDeveloperProjectView?) {
        if (view == null) {
            factsArea.text = "尚未导入或选择项目。\n请点击“导入项目”开始。"
            stdoutArea.text = ""
            stderrArea.text = ""
            combinedArea.text = ""
            environmentArea.text = ""
            setActionAvailability(null)
            return
        }

        val operation = view.workflow.operation
        factsArea.text = buildString {
            appendLine("项目 ID: " + view.project.projectId)
            appendLine("项目名称: " + view.project.name)
            appendLine("项目位置: " + view.project.imported.canonicalRootPath)
            appendLine()
            appendLine(
                "运行时 Runtime: " +
                    if (view.project.isCompose) "compose" else (view.project.runtime?.id ?: "未解析"),
            )
            appendLine("运行时版本: " + (view.runtimeVersion ?: "—"))
            if (view.project.isCompose) {
                appendLine("容器 Provider: " + (view.containerProvider ?: "—"))
                appendLine("Compose Project: " + (view.composeProjectName ?: "—"))
                appendLine(
                    "容器建议: " +
                        (view.containerAdvice?.title ?: "—") +
                        (view.containerAdvice?.detail?.let { " / " + it } ?: ""),
                )
                view.containerAdvice?.suggestedOptions.orEmpty().forEach { option ->
                    appendLine("  建议选项: " + option)
                }
                view.containerAdvice?.warnings.orEmpty().forEach { warning ->
                    appendLine("  资源提示: " + warning)
                }
                view.containerInstallPlan?.let { plan ->
                    appendLine("环境补齐方案: " + plan.kind)
                    appendLine("  标题: " + plan.title)
                    appendLine("  说明: " + plan.detail)
                    appendLine("  组件: " + plan.components.joinToString(", "))
                    plan.sideEffects.forEach { effect ->
                        appendLine("  影响: " + effect)
                    }
                }
                view.containerInstallProgress?.let { progress ->
                    appendLine("环境补齐阶段: " + progress.phase)
                    appendLine("环境补齐状态: " + progress.message)
                }
                if (view.containerServices.isNotEmpty()) {
                    appendLine("服务 Services:")
                    view.containerServices.forEach { service ->
                        appendLine("  - " + service.name + ": " + service.state)
                    }
                } else {
                    appendLine("服务 Services: —")
                }
            }
            appendLine("环境已就绪: " + view.workflow.environmentReady)
            appendLine("环境代际 Generation: " + (view.environment?.generation ?: "—"))
            appendLine("环境位置: " + (view.environment?.root?.absolutePath ?: "—"))
            appendLine("入口 Entrypoint: " + (view.entrypoint ?: "—"))
            appendLine()
            appendLine("生命周期 Lifecycle: " + view.workflow.lifecycle)
            appendLine("进程状态: " + view.workflow.processState)
            appendLine(
                "当前操作: " + (
                    operation?.let {
                        it.action.name + " / " + it.phase.name + " / generation=" + it.generation
                    } ?: "NONE"
                ),
            )
            appendLine("项目归属 PID: " + view.ownedPids.sorted().joinToString(", ").ifBlank { "—" })
            appendLine()
            appendLine("Web URL: " + (view.resultUrl ?: "—"))
            appendLine("Web 发现来源: " + (view.webSource ?: "—"))
            appendLine("端点状态: " + view.endpointState)
            appendLine()
            appendLine("日志已截断: " + view.logsTruncated)
            appendLine("最近错误: " + (view.lastError ?: "—"))
        }

        environmentArea.text = buildString {
            val plan = view.containerInstallPlan
            val progress = view.containerInstallProgress
            if (plan == null && progress == null) {
                appendLine("当前没有待执行的环境补齐方案。")
            }
            plan?.let {
                appendLine("PLAN=" + it.kind)
                appendLine("TITLE=" + it.title)
                appendLine("DETAIL=" + it.detail)
                appendLine("COMPONENTS=" + it.components.joinToString(", "))
                it.sideEffects.forEach { effect -> appendLine("SIDE_EFFECT=" + effect) }
                appendLine()
            }
            progress?.let {
                appendLine("PHASE=" + it.phase)
                appendLine("MESSAGE=" + it.message)
                it.logLines.forEach(::appendLine)
                appendLine()
            }
            view.lastError?.takeIf(String::isNotBlank)?.let { error ->
                appendLine("LAST_ERROR=" + error)
                appendLine()
            }
            if (view.project.isCompose) {
                val composeDiagnostics = view.combinedLogs
                    .lineSequence()
                    .filter { line ->
                        line.contains("compose pull:", ignoreCase = true) ||
                            line.contains("compose build:", ignoreCase = true) ||
                            line.contains("SIFTALPHA_M62_PREPARE=") ||
                            line.contains("PREPARE_DETAIL=")
                    }
                    .toList()
                    .takeLast(120)
                if (composeDiagnostics.isNotEmpty()) {
                    appendLine("=== Compose Prepare Diagnostics ===")
                    composeDiagnostics.forEach(::appendLine)
                    appendLine()
                }
            }
            val rawInstallLog = controller.containerInstallLog(view.project.projectId)
            if (rawInstallLog.isNotBlank()) {
                appendLine("=== Installer Raw Log ===")
                append(rawInstallLog)
            }
        }
        stdoutArea.text = view.stdout
        stderrArea.text = view.stderr
        combinedArea.text = view.combinedLogs
        moveCaretToEnd(environmentArea)
        moveCaretToEnd(stdoutArea)
        moveCaretToEnd(stderrArea)
        moveCaretToEnd(combinedArea)
        setActionAvailability(view)
    }

    private fun setActionAvailability(view: MacDeveloperProjectView?) {
        val selected = view != null
        if (!selected) {
            prepareButton.isEnabled = false
            installEnvironmentButton.isEnabled = false
            runButton.isEnabled = false
            stopButton.isEnabled = false
            restartButton.isEnabled = false
            refreshButton.isEnabled = false
            configureButton.isEnabled = false
            cleanButton.isEnabled = false
            storageButton.isEnabled = true
            removeButton.isEnabled = false
            copyButton.isEnabled = false
            return
        }

        val busy = view!!.workflow.operation != null
        val running = view.workflow.processState == ProjectProcessState.RUNNING
        val environmentInstallActive = view.containerInstallProgress?.active == true
        prepareButton.isEnabled = !busy && !running && !environmentInstallActive
        installEnvironmentButton.isEnabled =
            view.project.isCompose &&
                view.containerInstallPlan != null &&
                !busy &&
                !running &&
                !environmentInstallActive
        runButton.isEnabled = !busy && view.workflow.environmentReady && !running && !environmentInstallActive
        stopButton.isEnabled = running || busy
        restartButton.isEnabled = !busy && view.workflow.environmentReady
        refreshButton.isEnabled = true
        configureButton.isEnabled = !busy && !environmentInstallActive
        cleanButton.isEnabled = !busy && !running && !environmentInstallActive
        storageButton.isEnabled = !busy && !environmentInstallActive
        removeButton.isEnabled = !busy && !running && !environmentInstallActive
        copyButton.isEnabled = view.combinedLogs.isNotBlank()
    }

    private fun runStartWithConfiguration(retriesRemaining: Int = 3) {
        runOperation(controller::start) { id, success ->
            if (
                !success &&
                retriesRemaining > 0 &&
                controller.pendingEnvironmentConfiguration(id).isNotEmpty() &&
                MacProjectConfigurationDialog.showRequired(frame, controller, id)
            ) {
                runStartWithConfiguration(retriesRemaining - 1)
            }
        }
    }

    private fun runOperation(
        operation: (String) -> Boolean,
    ) = runOperation(operation, null)

    private fun runOperation(
        operation: (String) -> Boolean,
        onComplete: ((String, Boolean) -> Unit)?,
    ) {
        val id = selectedProjectId ?: return
        setButtonsBusy()
        Thread {
            val success = runCatching { operation(id) }.getOrDefault(false)
            SwingUtilities.invokeLater {
                refreshProjects(id)
                onComplete?.invoke(id, success)
            }
        }.start()
    }

    private fun setButtonsBusy() {
        prepareButton.isEnabled = false
        installEnvironmentButton.isEnabled = false
        runButton.isEnabled = false
        stopButton.isEnabled = false
        restartButton.isEnabled = false
        refreshButton.isEnabled = false
        configureButton.isEnabled = false
        cleanButton.isEnabled = false
        storageButton.isEnabled = false
        removeButton.isEnabled = false
    }

    private fun clearSelectedEnvironment() {
        val id = selectedProjectId ?: return
        val name = controller.project(id)?.name ?: "当前项目"
        val answer = JOptionPane.showConfirmDialog(
            frame,
            "清理 $name 的 SiftAlpha 运行环境和项目级容器资源？\n不会删除项目源代码，也不会影响其他项目。",
            "清理项目环境",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (answer != JOptionPane.OK_OPTION) return
        runOperation(controller::clearProjectEnvironment)
    }

    private fun removeSelectedProject() {
        val id = selectedProjectId ?: return
        val name = controller.project(id)?.name ?: "当前项目"
        val answer = JOptionPane.showConfirmDialog(
            frame,
            "从 SiftAlpha X 中移除 $name？\n项目源代码不会被删除；已准备的环境也不会自动删除。",
            "移除项目",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (answer != JOptionPane.OK_OPTION) return
        setButtonsBusy()
        Thread {
            val success = controller.removeProject(id)
            SwingUtilities.invokeLater {
                if (success) {
                    selectedProjectId = null
                    refreshProjects(null)
                } else {
                    JOptionPane.showMessageDialog(
                        frame,
                        controller.view(id)?.lastError ?: "无法移除项目，请先停止项目。",
                        "移除失败",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    refreshProjects(id)
                }
            }
        }.start()
    }

    private fun copyCombinedLogs() {
        val text = combinedArea.text
        if (text.isBlank()) return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    private fun returnToNormal() {
        refreshTimer.stop()
        frame.dispose()
        onReturnToNormal()
    }

    private fun moveCaretToEnd(area: JTextArea) {
        area.caretPosition = area.document.length
    }

    private fun diagnosticArea(): JTextArea = JTextArea().apply {
        isEditable = false
        lineWrap = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = MacDesignTokens.surface
        foreground = MacDesignTokens.foreground
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
