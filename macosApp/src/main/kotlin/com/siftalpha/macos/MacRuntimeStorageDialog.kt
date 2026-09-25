package com.siftalpha.macos

import com.siftalpha.core.storage.RuntimeStorageKind
import com.siftalpha.core.storage.RuntimeStorageSnapshot
import java.awt.Component
import javax.swing.JOptionPane

object MacRuntimeStorageDialog {
    fun show(parent: Component, controller: MacProductController) {
        while (true) {
            val snapshot = runCatching { controller.runtimeStorageSnapshot() }
                .getOrElse { error ->
                    JOptionPane.showMessageDialog(
                        parent,
                        error.message ?: "无法读取运行空间。",
                        "运行空间管理",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    return
                }
            val options = if (snapshot.cleanableBytes > 0L) {
                arrayOf("清理可安全项", "关闭")
            } else {
                arrayOf("关闭")
            }
            val selected = JOptionPane.showOptionDialog(
                parent,
                summary(snapshot),
                "运行空间管理",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options.last(),
            )
            if (snapshot.cleanableBytes <= 0L || selected != 0) return

            val confirm = JOptionPane.showConfirmDialog(
                parent,
                "将清理未关联且已确认未运行的项目环境，以及非当前版本的托管工具链。\n" +
                    "不会删除项目源代码、当前项目环境、当前容器工具链或 VM 运行状态。\n\n" +
                    "预计可释放：" + formatBytes(snapshot.cleanableBytes),
                "确认清理",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
            if (confirm != JOptionPane.OK_OPTION) return

            val after = runCatching { controller.cleanSafeRuntimeStorage() }
                .getOrElse { error ->
                    JOptionPane.showMessageDialog(
                        parent,
                        error.message ?: "运行空间清理失败。",
                        "清理失败",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    return
                }
            JOptionPane.showMessageDialog(
                parent,
                "清理完成。\n当前占用：" + formatBytes(after.totalBytes),
                "运行空间管理",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }
    }

    private fun summary(snapshot: RuntimeStorageSnapshot): String {
        fun total(kind: RuntimeStorageKind): Long =
            snapshot.entries.filter { it.kind == kind }.sumOf { it.sizeBytes }

        val warnings = snapshot.entries
            .filter { it.kind == RuntimeStorageKind.ORPHAN_PROJECT_DATA && !it.cleanable }
            .mapNotNull { it.detail }
            .distinct()

        return buildString {
            append("SiftAlpha 运行空间\n\n")
            append("总占用：").append(formatBytes(snapshot.totalBytes)).append('\n')
            append("项目环境：").append(formatBytes(total(RuntimeStorageKind.PROJECT_ENVIRONMENT))).append('\n')
            append("未关联环境：").append(formatBytes(total(RuntimeStorageKind.ORPHAN_PROJECT_DATA))).append('\n')
            append("共享缓存：").append(formatBytes(total(RuntimeStorageKind.SHARED_CACHE))).append('\n')
            append("托管工具链：").append(formatBytes(total(RuntimeStorageKind.MANAGED_TOOLCHAIN))).append('\n')
            append("平台运行状态：").append(formatBytes(total(RuntimeStorageKind.PLATFORM_RUNTIME_STATE))).append('\n')
            append("\n可安全清理：").append(formatBytes(snapshot.cleanableBytes))
            if (warnings.isNotEmpty()) {
                append("\n\n为避免误删，以下内容已保留：\n")
                warnings.take(4).forEach { append("• ").append(it).append('\n') }
            }
        }.trimEnd()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return bytes.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        return "%.1f %s".format(value, units[unit.coerceAtLeast(0)])
    }
}
