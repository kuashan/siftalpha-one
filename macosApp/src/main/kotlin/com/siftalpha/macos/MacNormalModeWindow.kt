package com.siftalpha.macos

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.ActionListener
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

private enum class ProjectFilter(val label: String) {
    ALL("全部"),
    PYTHON("Python"),
    NODE("Node.js"),
    COMPOSE("Compose"),
}

private class RoundedPanel(
    private val fill: Color,
    private val radius: Int = 18,
) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = fill
            g.fillRoundRect(0, 0, width, height, radius, radius)
        } finally {
            g.dispose()
        }
        super.paintComponent(graphics)
    }
}

private class ProductButton(
    text: String,
    private val fill: Color,
    private val textColor: Color,
) : JButton(text) {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        foreground = textColor
        font = MacDesignTokens.bodyFont.deriveFont(Font.BOLD)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(10, 18, 10, 18)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val color = when {
                !isEnabled -> MacDesignTokens.surfaceSubtle
                model.isPressed -> fill.darker()
                else -> fill
            }
            g.color = color
            g.fillRoundRect(0, 0, width, height, 14, 14)
        } finally {
            g.dispose()
        }
        super.paintComponent(graphics)
    }
}

private class StatusPill : JLabel("", SwingConstants.CENTER) {
    init {
        isOpaque = true
        border = BorderFactory.createEmptyBorder(4, 9, 4, 9)
        font = MacDesignTokens.smallFont.deriveFont(Font.BOLD)
    }

    fun setStatus(label: String, running: Boolean, warning: Boolean) {
        text = label
        when {
            warning -> {
                background = Color(0xFF, 0xF3, 0xE8)
                foreground = MacDesignTokens.warning
            }
            running -> {
                background = Color(0xEC, 0xF7, 0xF1)
                foreground = MacDesignTokens.success
            }
            else -> {
                background = MacDesignTokens.surfaceSubtle
                foreground = MacDesignTokens.muted
            }
        }
    }
}

private class ProjectCellRenderer : DefaultListCellRenderer() {
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
        val item = value as? MacProductProjectView
        if (item != null) {
            val p = MacNormalProjectPresentationPolicy.resolve(item)
            val runtime = p.runtimeLabel
            label.text = "<html><b>" + escape(item.project.name) + "</b><br>" +
                "<span style='color:#64748B'>" + escape(runtime) + " · " +
                escape(p.statusLabel) + "</span></html>"
        }
        label.font = MacDesignTokens.bodyFont
        label.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
        label.background = if (isSelected) Color(0xEE, 0xED, 0xFF) else MacDesignTokens.surface
        label.foreground = MacDesignTokens.foreground
        return label
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

class MacNormalModeWindow(
    private val controller: MacProductController,
) {
    private val frame = JFrame("SiftAlpha X")
    private val projectModel = DefaultListModel<MacProductProjectView>()
    private val projectList = JList(projectModel)
    private val searchField = JTextField()
    private val filterBox = JComboBox(DefaultComboBoxModel(ProjectFilter.entries.toTypedArray()))

    private val workspaceTitle = JLabel("选择一个项目")
    private val runtimePill = StatusPill()
    private val statePill = StatusPill()
    private val statusTitle = JLabel("还没有选择项目")
    private val statusDetail = JLabel("导入一个项目后，SiftAlpha X 会告诉你下一步应该做什么。")
    private val primaryButton = ProductButton("导入项目", MacDesignTokens.primary, Color.WHITE)
    private val openButton = ProductButton("打开结果", MacDesignTokens.surfaceSubtle, MacDesignTokens.foreground)
    private val refreshButton = ProductButton("刷新", MacDesignTokens.surfaceSubtle, MacDesignTokens.foreground)
    private val secondaryStopButton = ProductButton("停止", MacDesignTokens.surfaceSubtle, MacDesignTokens.foreground)
    private val resultPanel = RoundedPanel(MacDesignTokens.surface, 16)
    private val resultTitle = JLabel("结果")
    private val resultDetail = JLabel("项目运行后，可展示的结果会出现在这里。")
    private val locationLabel = JLabel("运行位置：—")
    private val rootLabel = JLabel("项目位置：尚未选择")
    private val emptyHint = JLabel("还没有项目")
    private val importButton = ProductButton("导入项目", MacDesignTokens.primary, Color.WHITE)
    private val moreButton = ProductButton("更多", MacDesignTokens.surfaceSubtle, MacDesignTokens.foreground)

    @Volatile
    private var selectedProjectId: String? = null
    @Volatile
    private var refreshInFlight = false

    fun show(): JFrame {
        configureFrame()
        refreshProjects()
        return frame.apply { isVisible = true }
    }

    private fun configureFrame() {
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.minimumSize = Dimension(1080, 680)
        frame.preferredSize = Dimension(1180, 760)
        frame.contentPane.background = MacDesignTokens.background
        frame.layout = BorderLayout()
        frame.add(buildTopBar(), BorderLayout.NORTH)

        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildSidebar(),
            buildWorkspace(),
        ).apply {
            dividerLocation = 310
            resizeWeight = 0.0
            border = null
            background = MacDesignTokens.background
        }
        frame.add(split, BorderLayout.CENTER)
        frame.pack()
        frame.setLocationRelativeTo(null)

        Timer(900) { refreshLiveState() }.start()
    }

    private fun buildTopBar(): JPanel = JPanel(BorderLayout()).apply {
        background = MacDesignTokens.surface
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, MacDesignTokens.border),
            BorderFactory.createEmptyBorder(12, 20, 12, 20),
        )

        val brand = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
            isOpaque = false
            add(loadLogo())
            add(JLabel("SiftAlpha X").apply {
                font = MacDesignTokens.headingFont
                foreground = MacDesignTokens.foreground
            })
        }
        add(brand, BorderLayout.WEST)

        moreButton.addActionListener {
            val menu = JPopupMenu()
            menu.add(JMenuItem("刷新项目").apply {
                addActionListener { refreshProjects() }
            })
            menu.add(JMenuItem("开发者模式").apply {
                addActionListener { openDeveloperMode() }
            })
            menu.add(JMenuItem("关于 SiftAlpha X").apply {
                addActionListener {
                    JOptionPane.showMessageDialog(
                        frame,
                        "SiftAlpha X\n让复杂的技术 变成简单的可能",
                        "关于 SiftAlpha X",
                        JOptionPane.INFORMATION_MESSAGE,
                    )
                }
            })
            menu.show(moreButton, 0, moreButton.height)
        }
        add(moreButton, BorderLayout.EAST)
    }

    private fun loadLogo(): JLabel {
        val resource = javaClass.getResource("/siftalpha_logo.png")
        val icon = resource?.let { url ->
            runCatching {
                val image = ImageIO.read(url)
                ImageIcon(image.getScaledInstance(28, 28, java.awt.Image.SCALE_SMOOTH))
            }.getOrNull()
        }
        return JLabel(icon ?: ImageIcon()).apply {
            if (icon == null) {
                text = "S"
                horizontalAlignment = SwingConstants.CENTER
                foreground = MacDesignTokens.primary
                font = MacDesignTokens.headingFont
            }
            preferredSize = Dimension(30, 30)
        }
    }

    private fun buildSidebar(): JPanel = JPanel(BorderLayout()).apply {
        background = MacDesignTokens.surface
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, MacDesignTokens.border)

        val header = JPanel(GridBagLayout()).apply {
            background = MacDesignTokens.surface
            border = BorderFactory.createEmptyBorder(18, 18, 12, 18)
            val c = GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(0, 0, 10, 0)
            }
            add(JLabel("项目").apply {
                font = MacDesignTokens.headingFont
                foreground = MacDesignTokens.foreground
            }, c)
            c.gridy = 1
            searchField.toolTipText = "搜索项目"
            searchField.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MacDesignTokens.border),
                BorderFactory.createEmptyBorder(8, 10, 8, 10),
            )
            add(searchField, c)
            c.gridy = 2
            c.insets = Insets(0, 0, 10, 0)
            filterBox.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ): Component = super.getListCellRendererComponent(
                    list,
                    (value as? ProjectFilter)?.label ?: value,
                    index,
                    isSelected,
                    cellHasFocus,
                )
            }
            add(filterBox, c)
            c.gridy = 3
            c.insets = Insets(0, 0, 0, 0)
            importButton.addActionListener { importProject() }
            add(importButton, c)
        }
        add(header, BorderLayout.NORTH)

        projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        projectList.cellRenderer = ProjectCellRenderer()
        projectList.fixedCellHeight = 58
        projectList.background = MacDesignTokens.surface
        projectList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                selectedProjectId = projectList.selectedValue?.project?.projectId
                renderSelected()
            }
        }
        add(JScrollPane(projectList).apply { border = null }, BorderLayout.CENTER)

        emptyHint.horizontalAlignment = SwingConstants.CENTER
        emptyHint.font = MacDesignTokens.bodyFont
        emptyHint.foreground = MacDesignTokens.muted
        emptyHint.border = BorderFactory.createEmptyBorder(12, 12, 18, 12)
        add(emptyHint, BorderLayout.SOUTH)

        val refreshListener = ActionListener { refreshProjects() }
        searchField.addActionListener(refreshListener)
        filterBox.addActionListener(refreshListener)
    }

    private fun buildWorkspace(): JPanel = JPanel(BorderLayout()).apply {
        background = MacDesignTokens.background
        border = BorderFactory.createEmptyBorder(26, 30, 30, 30)

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(workspaceTitle.apply {
                font = MacDesignTokens.titleFont
                foreground = MacDesignTokens.foreground
            }, BorderLayout.WEST)
            val pills = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
                add(runtimePill)
                add(statePill)
            }
            add(pills, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)

        val center = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            isOpaque = false
            border = BorderFactory.createEmptyBorder(22, 0, 0, 0)
        }

        val statusCard = RoundedPanel(MacDesignTokens.surface).apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(22, 24, 22, 24)
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 180)
            add(statusTitle.apply {
                font = MacDesignTokens.headingFont
                foreground = MacDesignTokens.foreground
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(javax.swing.Box.createVerticalStrut(8))
            add(statusDetail.apply {
                font = MacDesignTokens.bodyFont
                foreground = MacDesignTokens.muted
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(javax.swing.Box.createVerticalStrut(18))
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(primaryButton)
            })
        }
        center.add(statusCard)
        center.add(javax.swing.Box.createVerticalStrut(16))

        resultPanel.layout = BorderLayout()
        resultPanel.border = BorderFactory.createEmptyBorder(18, 22, 18, 22)
        resultPanel.maximumSize = Dimension(Int.MAX_VALUE, 150)
        resultPanel.alignmentX = Component.LEFT_ALIGNMENT
        resultPanel.add(JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            isOpaque = false
            add(resultTitle.apply {
                font = MacDesignTokens.headingFont
                foreground = MacDesignTokens.foreground
            })
            add(javax.swing.Box.createVerticalStrut(6))
            add(resultDetail.apply {
                font = MacDesignTokens.bodyFont
                foreground = MacDesignTokens.muted
            })
        }, BorderLayout.CENTER)
        resultPanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(openButton)
            add(refreshButton)
            add(secondaryStopButton)
        }, BorderLayout.SOUTH)
        center.add(resultPanel)
        center.add(javax.swing.Box.createVerticalStrut(16))

        val details = RoundedPanel(MacDesignTokens.surfaceSubtle, 16).apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
            maximumSize = Dimension(Int.MAX_VALUE, 110)
            alignmentX = Component.LEFT_ALIGNMENT
            add(locationLabel.apply {
                font = MacDesignTokens.bodyFont
                foreground = MacDesignTokens.muted
            })
            add(javax.swing.Box.createVerticalStrut(7))
            add(rootLabel.apply {
                font = MacDesignTokens.smallFont
                foreground = MacDesignTokens.muted
            })
        }
        center.add(details)
        add(center, BorderLayout.CENTER)

        primaryButton.addActionListener { performPrimaryAction() }
        openButton.addActionListener { openResult() }
        refreshButton.addActionListener { refreshSelectedProject() }
        secondaryStopButton.addActionListener { runProjectOperation("正在停止…") { id -> controller.stop(id) } }

        renderSelected()
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
                    refreshProjects(selectProjectId = project.projectId)
                }.onFailure { error ->
                    JOptionPane.showMessageDialog(
                        frame,
                        "无法导入这个项目。\n" + (error.message ?: ""),
                        "导入失败",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }.start()
    }

    private fun refreshProjects(selectProjectId: String? = selectedProjectId) {
        val query = searchField.text.trim().lowercase()
        val filter = filterBox.selectedItem as? ProjectFilter ?: ProjectFilter.ALL
        val previous = selectProjectId

        val views = controller.projects()
            .mapNotNull { controller.view(it.projectId) }
            .filter { view ->
                val nameMatch = query.isBlank() || view.project.name.lowercase().contains(query)
                val filterMatch = when (filter) {
                    ProjectFilter.ALL -> true
                    ProjectFilter.PYTHON -> view.project.runtime?.id == "python"
                    ProjectFilter.NODE -> view.project.runtime?.id == "nodejs"
                    ProjectFilter.COMPOSE -> view.project.isCompose
                }
                nameMatch && filterMatch
            }

        projectModel.clear()
        views.forEach(projectModel::addElement)
        emptyHint.text = if (views.isEmpty()) "还没有项目" else views.size.toString() + " 个项目"

        if (previous != null) {
            val index = (0 until projectModel.size())
                .firstOrNull { projectModel.get(it).project.projectId == previous }
            if (index != null) {
                projectList.selectedIndex = index
                selectedProjectId = previous
            }
        }
        if (projectList.selectedIndex < 0 && projectModel.size() > 0) {
            projectList.selectedIndex = 0
            selectedProjectId = projectModel.get(0).project.projectId
        }
        renderSelected()
    }

    private fun refreshSelectedProject() {
        val id = selectedProjectId ?: return
        refreshButton.isEnabled = false
        Thread {
            runCatching { controller.refreshProject(id) }
            SwingUtilities.invokeLater {
                refreshButton.isEnabled = true
                refreshProjects(selectProjectId = id)
            }
        }.start()
    }

    private fun refreshLiveState() {
        if (refreshInFlight) return
        val id = selectedProjectId ?: return
        refreshInFlight = true
        Thread {
            val view = controller.view(id)
            SwingUtilities.invokeLater {
                refreshInFlight = false
                if (selectedProjectId == id && view != null) {
                    replaceProjectView(view)
                    renderSelected(view)
                }
            }
        }.start()
    }

    private fun replaceProjectView(view: MacProductProjectView) {
        for (i in 0 until projectModel.size()) {
            if (projectModel.get(i).project.projectId == view.project.projectId) {
                projectModel.set(i, view)
                break
            }
        }
    }

    private fun renderSelected(forced: MacProductProjectView? = null) {
        val id = selectedProjectId
        val view = forced ?: id?.let(controller::view)
        if (view == null) {
            workspaceTitle.text = "选择一个项目"
            runtimePill.setStatus("—", running = false, warning = false)
            statePill.setStatus("未选择", running = false, warning = false)
            statusTitle.text = "还没有选择项目"
            statusDetail.text = "导入一个项目后，SiftAlpha X 会告诉你下一步应该做什么。"
            configurePrimary("导入项目", true) { importProject() }
            resultTitle.text = "结果"
            resultDetail.text = "项目运行后，可展示的结果会出现在这里。"
            openButton.isEnabled = false
            refreshButton.isEnabled = false
            secondaryStopButton.isVisible = false
            locationLabel.text = "运行位置：—"
            rootLabel.text = "项目位置：尚未选择"
            return
        }

        val p = MacNormalProjectPresentationPolicy.resolve(view)
        workspaceTitle.text = view.project.name
        runtimePill.setStatus(p.runtimeLabel, running = false, warning = false)
        statePill.setStatus(
            p.statusLabel,
            running = view.workflow.lifecycle == com.siftalpha.core.lifecycle.ProjectLifecycleState.RUNNING,
            warning = p.statusLabel.contains("失败") || p.statusLabel.contains("处理"),
        )
        statusTitle.text = p.title
        statusDetail.text = p.detail

        configurePrimary(p.primaryLabel ?: "暂不可用", p.primaryEnabled) {
            performPrimaryAction()
        }

        if (p.resultAvailable) {
            resultTitle.text = "结果已准备好"
            resultDetail.text = p.resultUrl ?: "可以打开项目结果。"
        } else {
            resultTitle.text = "结果"
            resultDetail.text = "项目运行后，可展示的结果会出现在这里。"
        }
        openButton.isEnabled = p.resultAvailable
        refreshButton.isEnabled = true
        secondaryStopButton.isVisible = p.showSecondaryStop
        secondaryStopButton.isEnabled = p.showSecondaryStop
        locationLabel.text = "运行位置：" + p.locationLabel
        rootLabel.text = "项目位置：" + view.project.imported.canonicalRootPath
    }

    private fun configurePrimary(label: String, enabled: Boolean, action: () -> Unit) {
        primaryButton.text = label
        primaryButton.isEnabled = enabled
        primaryButton.actionListeners.forEach(primaryButton::removeActionListener)
        primaryButton.addActionListener { action() }
    }

    private fun performPrimaryAction() {
        val id = selectedProjectId ?: run {
            importProject()
            return
        }
        val view = controller.view(id) ?: return
        when (MacNormalProjectPresentationPolicy.resolve(view).primaryAction) {
            MacNormalPrimaryAction.PREPARE ->
                runProjectOperation("正在准备项目…") { projectId -> controller.prepare(projectId).success }
            MacNormalPrimaryAction.RUN ->
                runProjectOperation("正在启动项目…") { projectId -> controller.start(projectId) }
            MacNormalPrimaryAction.STOP ->
                runProjectOperation("正在停止项目…") { projectId -> controller.stop(projectId) }
            MacNormalPrimaryAction.OPEN_RESULT -> openResult()
            MacNormalPrimaryAction.NONE -> Unit
        }
    }

    private fun runProjectOperation(message: String, operation: (String) -> Boolean) {
        val id = selectedProjectId ?: return
        statusTitle.text = message
        primaryButton.isEnabled = false
        Thread {
            operation(id)
            SwingUtilities.invokeLater {
                refreshProjects(selectProjectId = id)
            }
        }.start()
    }

    private fun openDeveloperMode() {
        val selected = selectedProjectId
        frame.isVisible = false
        MacDeveloperModeWindow(
            controller = controller,
            initialProjectId = selected,
            onReturnToNormal = {
                frame.isVisible = true
                refreshProjects(selectProjectId = selected)
            },
        ).show()
    }

    private fun openResult() {
        val id = selectedProjectId ?: return
        val url = controller.view(id)?.resultUrl ?: return
        if (!Desktop.isDesktopSupported()) return
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}
