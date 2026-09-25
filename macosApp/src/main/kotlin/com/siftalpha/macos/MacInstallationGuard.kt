package com.siftalpha.macos

import java.io.File

enum class MacInstallationState {
    DEVELOPMENT,
    INSTALLED_IN_APPLICATIONS,
    APP_TRANSLOCATION,
    OUTSIDE_APPLICATIONS,
}

data class MacInstallationAssessment(
    val state: MacInstallationState,
    val allowed: Boolean,
    val bundlePath: String? = null,
    val detail: String? = null,
)

object MacInstallationGuard {
    private const val APP_BUNDLE_MARKER = ".app/Contents/MacOS/"
    private const val APPLICATIONS_ROOT = "/Applications/"

    fun assess(
        commandPath: String? = ProcessHandle.current().info().command().orElse(null),
    ): MacInstallationAssessment {
        val raw = commandPath?.trim().orEmpty()
        if (raw.isBlank()) {
            return MacInstallationAssessment(
                state = MacInstallationState.DEVELOPMENT,
                allowed = true,
                detail = "launcher path unavailable; treating as development launch",
            )
        }

        val markerIndex = raw.indexOf(APP_BUNDLE_MARKER)
        if (markerIndex < 0) {
            return MacInstallationAssessment(
                state = MacInstallationState.DEVELOPMENT,
                allowed = true,
                detail = "not running from a packaged .app bundle",
            )
        }

        val bundlePath = raw.substring(0, markerIndex + ".app".length)
        val normalized = File(bundlePath).absoluteFile.normalize().path

        if (normalized.contains("/AppTranslocation/")) {
            return MacInstallationAssessment(
                state = MacInstallationState.APP_TRANSLOCATION,
                allowed = false,
                bundlePath = normalized,
                detail = "macOS App Translocation is active",
            )
        }

        if (!normalized.startsWith(APPLICATIONS_ROOT)) {
            return MacInstallationAssessment(
                state = MacInstallationState.OUTSIDE_APPLICATIONS,
                allowed = false,
                bundlePath = normalized,
                detail = "SiftAlpha X must run from /Applications",
            )
        }

        return MacInstallationAssessment(
            state = MacInstallationState.INSTALLED_IN_APPLICATIONS,
            allowed = true,
            bundlePath = normalized,
            detail = "installed application bundle",
        )
    }

    fun userMessage(assessment: MacInstallationAssessment): String = when (assessment.state) {
        MacInstallationState.APP_TRANSLOCATION ->
            """
            SiftAlpha X 当前正在 macOS 的 App Translocation 临时路径中运行。

            请关闭当前应用，重新打开安装 DMG，将 “SiftAlpha X” 拖入“应用程序”文件夹，
            然后从 /Applications/SiftAlpha X.app 启动。

            为避免运行环境、权限和更新状态不稳定，本次不会继续启动项目功能。
            """.trimIndent()

        MacInstallationState.OUTSIDE_APPLICATIONS ->
            """
            SiftAlpha X 当前没有从“应用程序”文件夹启动。

            请关闭当前应用，打开安装 DMG，将 “SiftAlpha X” 拖入“应用程序”文件夹，
            然后从 /Applications/SiftAlpha X.app 启动。

            当前检测路径：
            ${assessment.bundlePath.orEmpty()}
            """.trimIndent()

        else -> ""
    }
}
