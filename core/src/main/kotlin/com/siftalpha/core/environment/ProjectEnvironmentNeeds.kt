package com.siftalpha.core.environment

import com.siftalpha.studio.runtime.RuntimeKind

/**
 * Platform-independent description of what a project needs（项目环境需求）.
 *
 * This model intentionally does not know how Android（安卓）, macOS（苹果） or Windows（微软）
 * satisfies the requirements. It contains no Embedded CPython（内嵌 CPython）, Alpine（Alpine
 * 环境）, Termux（外部终端）, Docker（容器） or host-process provider choice.
 */
data class ProjectEnvironmentNeeds(
    val primaryRuntime: RuntimeKind?,
    val supplementalRuntimes: List<RuntimeKind> = emptyList(),
    val directDependencyCount: Int = 0,
    val pythonRequiresVersion: String? = null,
    val pythonOptionalDependencyGroups: List<String> = emptyList(),
    val viteComponentCount: Int = 0,
    val hasBlockingIssues: Boolean = false,
) {
    init {
        require(directDependencyCount >= 0) { "directDependencyCount must not be negative" }
        require(viteComponentCount >= 0) { "viteComponentCount must not be negative" }
    }

    val requiresPython: Boolean
        get() = primaryRuntime == RuntimeKind.PYTHON ||
            RuntimeKind.PYTHON in supplementalRuntimes

    val requiresNode: Boolean
        get() = primaryRuntime == RuntimeKind.NODE_JS ||
            RuntimeKind.NODE_JS in supplementalRuntimes

    val requiresNodeBuild: Boolean
        get() = viteComponentCount > 0 && requiresNode
}

/**
 * Provider-neutral preparation phases（提供者中立的准备阶段）.
 *
 * Platform adapters decide how to execute each phase. For example, ACQUIRE_RUNTIME（获取运行时）
 * may mean Embedded R（内部运行时） on Android（安卓） and a host-runtime probe on macOS（苹果）.
 */
enum class EnvironmentPreparationStep {
    VALIDATE_PLAN,
    ACQUIRE_RUNTIME,
    CREATE_ENVIRONMENT,
    NODE_INSTALL,
    NODE_BUILD,
    PYTHON_INSTALL,
    VERIFY_ENVIRONMENT,
    COMMIT_ENVIRONMENT,
}

/**
 * Shared policy for deriving environment work from project needs（从项目需求推导环境工作）.
 */
object ProjectEnvironmentNeedPolicy {
    fun preparationSteps(needs: ProjectEnvironmentNeeds): List<EnvironmentPreparationStep> {
        if (needs.hasBlockingIssues || needs.primaryRuntime == null) {
            return listOf(EnvironmentPreparationStep.VALIDATE_PLAN)
        }

        return buildList {
            add(EnvironmentPreparationStep.VALIDATE_PLAN)
            add(EnvironmentPreparationStep.ACQUIRE_RUNTIME)
            add(EnvironmentPreparationStep.CREATE_ENVIRONMENT)

            if (needs.requiresNode) {
                add(EnvironmentPreparationStep.NODE_INSTALL)
                if (
                    needs.primaryRuntime != RuntimeKind.NODE_JS ||
                    needs.viteComponentCount > 0
                ) {
                    add(EnvironmentPreparationStep.NODE_BUILD)
                }
            }

            if (needs.primaryRuntime == RuntimeKind.PYTHON) {
                add(EnvironmentPreparationStep.PYTHON_INSTALL)
            }

            add(EnvironmentPreparationStep.VERIFY_ENVIRONMENT)
            add(EnvironmentPreparationStep.COMMIT_ENVIRONMENT)
        }
    }

    fun pythonInstallExtras(needs: ProjectEnvironmentNeeds): List<String> = buildList {
        if (
            needs.primaryRuntime == RuntimeKind.PYTHON &&
            needs.viteComponentCount > 0 &&
            "web" in needs.pythonOptionalDependencyGroups
        ) {
            add("web")
        }
    }
}
