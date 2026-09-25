package com.siftalpha.macos

import java.awt.Component
import java.awt.GridLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField

object MacProjectConfigurationDialog {
    fun show(
        parent: Component,
        controller: MacProductController,
        projectId: String,
    ) {
        while (true) {
            val keys = controller.configuredEnvironmentKeys(projectId).sorted()
            val message = buildString {
                append("项目环境变量（安全存储于 macOS Keychain）\n\n")
                if (keys.isEmpty()) {
                    append("当前没有通过 SiftAlpha 配置环境变量。")
                } else {
                    append("已配置：\n")
                    keys.forEach { append("• ").append(it).append("  ✓\n") }
                }
                append("\n安全值不会在此窗口或运行日志中显示。")
            }
            val options = if (keys.isEmpty()) {
                arrayOf("添加 / 更新", "关闭")
            } else {
                arrayOf("添加 / 更新", "删除", "关闭")
            }
            val choice = JOptionPane.showOptionDialog(
                parent,
                message,
                "项目配置",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options.last(),
            )
            when {
                choice < 0 || choice == options.lastIndex -> return
                choice == 0 -> addOrUpdate(parent, controller, projectId)
                choice == 1 && keys.isNotEmpty() -> remove(parent, controller, projectId, keys)
            }
        }
    }

    private fun addOrUpdate(
        parent: Component,
        controller: MacProductController,
        projectId: String,
    ) {
        val name = JTextField()
        val value = JPasswordField()
        val panel = JPanel(GridLayout(0, 1, 0, 6)).apply {
            add(JLabel("变量名，例如 OPENAI_API_KEY"))
            add(name)
            add(JLabel("值"))
            add(value)
        }
        val answer = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "添加 / 更新项目配置",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
        )
        if (answer != JOptionPane.OK_OPTION) return
        val password = value.password
        try {
            val success = controller.saveEnvironmentValue(
                projectId = projectId,
                name = name.text,
                value = String(password),
            )
            if (!success) {
                JOptionPane.showMessageDialog(
                    parent,
                    "保存失败。请检查变量名或 macOS Keychain。",
                    "项目配置",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun remove(
        parent: Component,
        controller: MacProductController,
        projectId: String,
        keys: List<String>,
    ) {
        val selected = JOptionPane.showInputDialog(
            parent,
            "选择要删除的配置项：",
            "删除项目配置",
            JOptionPane.PLAIN_MESSAGE,
            null,
            keys.toTypedArray(),
            keys.first(),
        ) as? String ?: return
        controller.clearEnvironmentValue(projectId, selected)
    }
}
