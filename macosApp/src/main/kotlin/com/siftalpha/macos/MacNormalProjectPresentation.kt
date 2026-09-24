package com.siftalpha.macos

import com.siftalpha.core.lifecycle.ProjectLifecycleState
import com.siftalpha.studio.container.ComposeProjectPlanStatus
import com.siftalpha.studio.runtime.RuntimeKind

enum class MacNormalPrimaryAction {
    PREPARE,
    RUN,
    STOP,
    OPEN_RESULT,
    NONE,
}

data class MacNormalProjectPresentation(
    val statusLabel: String,
    val title: String,
    val detail: String,
    val primaryAction: MacNormalPrimaryAction,
    val primaryLabel: String?,
    val primaryEnabled: Boolean,
    val resultAvailable: Boolean,
    val resultUrl: String?,
    val showSecondaryStop: Boolean,
    val runtimeLabel: String,
    val locationLabel: String,
    val errorDetail: String? = null,
)

object MacNormalProjectPresentationPolicy {
    fun resolve(view: MacProductProjectView): MacNormalProjectPresentation {
        val isCompose = view.project.isCompose
        val runtimeLabel = if (isCompose) {
            "Compose"
        } else {
            when (view.project.runtime) {
                RuntimeKind.PYTHON -> "Python"
                RuntimeKind.NODE_JS -> "Node.js"
                null -> "未知"
                else -> view.project.runtime?.id ?: "未知"
            }
        }
        val location = if (isCompose) {
            "容器运行环境"
        } else {
            when (view.project.runtime) {
                RuntimeKind.PYTHON -> "SiftAlpha X 托管环境"
                RuntimeKind.NODE_JS -> "Mac 主机运行环境"
                else -> "SiftAlpha X"
            }
        }
        val resultUrl = view.resultUrl
        val hasResult = !resultUrl.isNullOrBlank()
        val error = view.lastError

        if (!error.isNullOrBlank()) {
            val canRetry = view.workflow.lifecycle != ProjectLifecycleState.RUNNING
            return MacNormalProjectPresentation(
                statusLabel = "需要处理",
                title = "上一步没有完成",
                detail = friendlyError(error),
                primaryAction = if (canRetry) retryAction(view) else MacNormalPrimaryAction.STOP,
                primaryLabel = if (canRetry) retryLabel(view) else "停止",
                primaryEnabled = true,
                resultAvailable = hasResult,
                resultUrl = resultUrl,
                showSecondaryStop = hasResult &&
                    view.workflow.lifecycle == ProjectLifecycleState.RUNNING,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
                errorDetail = error,
            )
        }

        if (isCompose) {
            val composePlan = view.project.composePlan
            if (composePlan?.status == ComposeProjectPlanStatus.INVALID_MANIFEST) {
                return MacNormalProjectPresentation(
                    statusLabel = "需要处理",
                    title = "Compose 项目需要修复",
                    detail = composePlan.issues.firstOrNull() ?: "Compose 配置无法生成安全运行计划。",
                    primaryAction = MacNormalPrimaryAction.NONE,
                    primaryLabel = null,
                    primaryEnabled = false,
                    resultAvailable = false,
                    resultUrl = null,
                    showSecondaryStop = false,
                    runtimeLabel = runtimeLabel,
                    locationLabel = location,
                )
            }
            val advice = view.containerAdvice
            if (
                !view.workflow.environmentReady &&
                advice != null &&
                advice.state != MacContainerAdviceState.READY
            ) {
                val option = advice.suggestedOptions.firstOrNull()
                val detail = buildString {
                    append(advice.detail)
                    if (!option.isNullOrBlank()) append(" 建议：" + option + "。")
                    advice.warnings.firstOrNull()?.let { append(" " + it) }
                }
                return MacNormalProjectPresentation(
                    statusLabel = "需要容器环境",
                    title = advice.title,
                    detail = detail,
                    primaryAction = MacNormalPrimaryAction.NONE,
                    primaryLabel = null,
                    primaryEnabled = false,
                    resultAvailable = false,
                    resultUrl = null,
                    showSecondaryStop = false,
                    runtimeLabel = runtimeLabel,
                    locationLabel = location,
                )
            }
        }

        if (!isCompose && view.project.plan.status == MacProjectPlanStatus.BLOCKED) {
            return MacNormalProjectPresentation(
                statusLabel = "需要处理",
                title = "项目还不能准备",
                detail = friendlyPlanIssue(view.project.plan.issues.firstOrNull()),
                primaryAction = MacNormalPrimaryAction.NONE,
                primaryLabel = null,
                primaryEnabled = false,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )
        }

        if (hasResult && view.workflow.lifecycle == ProjectLifecycleState.RUNNING) {
            return MacNormalProjectPresentation(
                statusLabel = "结果可用",
                title = "结果已准备好",
                detail = "项目仍在运行，可以打开结果，也可以随时停止。",
                primaryAction = MacNormalPrimaryAction.OPEN_RESULT,
                primaryLabel = "打开结果",
                primaryEnabled = true,
                resultAvailable = true,
                resultUrl = resultUrl,
                showSecondaryStop = true,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )
        }

        return when (view.workflow.lifecycle) {
            ProjectLifecycleState.ENVIRONMENT_NOT_PREPARED -> MacNormalProjectPresentation(
                statusLabel = "未准备",
                title = "项目还没准备好",
                detail = if (isCompose) {
                    "SiftAlpha X 会准备 Compose 所需镜像和构建内容，但不会在准备阶段启动服务。"
                } else {
                    "SiftAlpha X 会准备运行环境并安装项目需要的依赖。"
                },
                primaryAction = MacNormalPrimaryAction.PREPARE,
                primaryLabel = "准备项目",
                primaryEnabled = true,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.PREPARING -> MacNormalProjectPresentation(
                statusLabel = "准备中",
                title = "正在准备项目",
                detail = if (isCompose) {
                    "SiftAlpha X 正在拉取或构建当前项目需要的容器镜像。"
                } else {
                    "SiftAlpha X 正在准备运行环境和依赖。"
                },
                primaryAction = MacNormalPrimaryAction.STOP,
                primaryLabel = "停止",
                primaryEnabled = true,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.READY_TO_RUN,
            ProjectLifecycleState.STOPPED,
            -> MacNormalProjectPresentation(
                statusLabel = if (view.workflow.lifecycle == ProjectLifecycleState.STOPPED) "已停止" else "已准备",
                title = "可以运行",
                detail = if (isCompose) {
                    "容器镜像和项目运行条件已经准备完成。"
                } else {
                    "环境和项目依赖已经准备完成。"
                },
                primaryAction = MacNormalPrimaryAction.RUN,
                primaryLabel = "运行",
                primaryEnabled = true,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.STARTING -> MacNormalProjectPresentation(
                statusLabel = "启动中",
                title = "正在启动",
                detail = "SiftAlpha X 正在启动项目。",
                primaryAction = MacNormalPrimaryAction.STOP,
                primaryLabel = "停止",
                primaryEnabled = true,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.RUNNING -> MacNormalProjectPresentation(
                statusLabel = "运行中",
                title = "项目正在运行",
                detail = "SiftAlpha X 正在等待可展示的结果。",
                primaryAction = MacNormalPrimaryAction.STOP,
                primaryLabel = "停止",
                primaryEnabled = true,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.STOPPING -> MacNormalProjectPresentation(
                statusLabel = "停止中",
                title = "正在停止项目",
                detail = "SiftAlpha X 正在结束当前项目的活动。",
                primaryAction = MacNormalPrimaryAction.NONE,
                primaryLabel = "停止中…",
                primaryEnabled = false,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.RUN_FAILED -> MacNormalProjectPresentation(
                statusLabel = "运行失败",
                title = "项目没有成功运行",
                detail = "可以重试；如果仍然失败，开发者模式会提供完整诊断信息。",
                primaryAction = MacNormalPrimaryAction.RUN,
                primaryLabel = "重新运行",
                primaryEnabled = true,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.NEEDS_CONFIGURATION -> MacNormalProjectPresentation(
                statusLabel = "需要配置",
                title = "还需要完成配置",
                detail = "项目存在运行前必须完成的配置。",
                primaryAction = MacNormalPrimaryAction.NONE,
                primaryLabel = null,
                primaryEnabled = false,
                resultAvailable = false,
                resultUrl = null,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )

            ProjectLifecycleState.DETECTING,
            ProjectLifecycleState.CHECKING,
            ProjectLifecycleState.CLEANING,
            ProjectLifecycleState.RECOVERING,
            -> MacNormalProjectPresentation(
                statusLabel = "处理中",
                title = "正在更新项目状态",
                detail = "SiftAlpha X 正在确认项目的最新状态。",
                primaryAction = MacNormalPrimaryAction.NONE,
                primaryLabel = null,
                primaryEnabled = false,
                resultAvailable = hasResult,
                resultUrl = resultUrl,
                showSecondaryStop = false,
                runtimeLabel = runtimeLabel,
                locationLabel = location,
            )
        }
    }

    private fun retryAction(view: MacProductProjectView): MacNormalPrimaryAction =
        if (view.workflow.environmentReady) MacNormalPrimaryAction.RUN else MacNormalPrimaryAction.PREPARE

    private fun retryLabel(view: MacProductProjectView): String =
        if (view.workflow.environmentReady) "重新运行" else "重新准备"

    private fun friendlyPlanIssue(issue: String?): String = when {
        issue.isNullOrBlank() -> "SiftAlpha X 无法确认这个项目需要的运行环境。"
        issue.contains("ambiguous", ignoreCase = true) ->
            "项目同时包含多个根级运行环境标记，需要先明确主要运行方式。"
        issue.contains("package manager", ignoreCase = true) ->
            "这个项目当前使用的 Node.js 包管理方式还不受支持。"
        issue.contains("supported runtime", ignoreCase = true) ->
            "这个项目当前使用的运行环境还不受支持。"
        else -> "SiftAlpha X 暂时无法为这个项目生成安全的运行计划。"
    }

    private fun friendlyError(error: String): String = when {
        error.contains("container runtime", ignoreCase = true) ||
            error.contains("Compose", ignoreCase = true) ->
            "当前容器环境还不能完成这一步，请检查容器环境后刷新项目。"
        error.contains("managed Python", ignoreCase = true) ->
            "SiftAlpha X 的 Python 运行环境暂时不可用。"
        error.contains("operation is busy", ignoreCase = true) ->
            "这个项目正在执行另一项操作。"
        error.contains("cancel", ignoreCase = true) ->
            "操作已经停止，可以重新开始。"
        error.contains("source", ignoreCase = true) ||
            error.contains("wheel", ignoreCase = true) ->
            "项目包含当前无法直接安装的依赖。"
        else -> "SiftAlpha X 没有完成这一步，可以重新尝试。"
    }
}
