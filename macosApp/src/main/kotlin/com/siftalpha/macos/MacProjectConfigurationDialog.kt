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
            val pending = controller.pendingEnvironmentConfiguration(projectId)
            val keys = controller.configuredEnvironmentKeys(projectId).sorted()
            val message = buildString {
                append("项目环境变量（安全存储于 macOS Keychain）\n\n")
                if (pending.isNotEmpty()) {
                    append("当前运行缺少：\n")
                    pending.forEach { append("• ").append(it.name).append("  待配置\n") }
                    append('\n')
                }
                if (keys.isEmpty()) {
                    append("当前没有通过 SiftAlpha 配置环境变量。")
                } else {
                    append("已配置：\n")
                    keys.forEach { append("• ").append(it).append("  ✓\n") }
                }
                append("\n敏感值不会写入项目源码，也不会显示在运行日志中。")
            }
            val options = buildList {
                if (pending.isNotEmpty()) add("补齐缺失配置")
                add("添加 / 更新")
                if (keys.isNotEmpty()) add("删除")
                add("关闭")
            }.toTypedArray()
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
            if (choice < 0 || choice == options.lastIndex) return
            when (options[choice]) {
                "补齐缺失配置" -> showRequired(parent, controller, projectId)
                "添加 / 更新" -> addOrUpdate(parent, controller, projectId)
                "删除" -> remove(parent, controller, projectId, keys)
            }
        }
    }

    fun showRequired(
        parent: Component,
        controller: MacProductController,
        projectId: String,
    ): Boolean {
        val requirements = controller.pendingEnvironmentConfiguration(projectId)
        if (requirements.isEmpty()) return true

        val fields = linkedMapOf<MacProjectConfigurationRequirement, JTextField>()
        val panel = JPanel(GridLayout(0, 1, 0, 6)).apply {
            add(JLabel("这个项目还需要以下运行配置："))
            requirements.forEach { requirement ->
                add(JLabel(requirement.name))
                val field: JTextField = if (requirement.sensitive) JPasswordField() else JTextField()
                fields[requirement] = field
                add(field)
            }
            add(JLabel("值将安全保存到 macOS Keychain，并只注入当前项目。"))
        }

        val answer = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "补齐项目配置",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
        )
        if (answer != JOptionPane.OK_OPTION) return false

        for ((requirement, field) in fields) {
            val value = when (field) {
                is JPasswordField -> {
                    val chars = field.password
                    try {
                        String(chars)
                    } finally {
                        chars.fill('\u0000')
                    }
                }
                else -> field.text
            }
            if (value.isBlank()) {
                JOptionPane.showMessageDialog(
                    parent,
                    requirement.name + " 不能为空。",
                    "项目配置",
                    JOptionPane.WARNING_MESSAGE,
                )
                return false
            }
            if (!controller.saveEnvironmentValue(projectId, requirement.name, value)) {
                JOptionPane.showMessageDialog(
                    parent,
                    requirement.name + " 保存失败。请检查 macOS Keychain。",
                    "项目配置",
                    JOptionPane.ERROR_MESSAGE,
                )
                return false
            }
        }
        return controller.pendingEnvironmentConfiguration(projectId).isEmpty()
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
