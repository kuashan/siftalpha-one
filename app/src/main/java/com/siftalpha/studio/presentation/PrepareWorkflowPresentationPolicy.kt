package com.siftalpha.studio.presentation

import com.siftalpha.studio.runtime.EnvironmentBackend
import com.siftalpha.studio.runtime.EnvironmentCompatibilityResolutionState
import com.siftalpha.studio.runtime.ProjectEnvironmentResolution

/**
 * Presentation-only policy for Normal Mode project preparation.
 *
 * It never changes Environment Detection / Plan / Compatibility / Prepare / Verification facts.
 * It only translates those existing facts into a small product-facing phase model.
 */
enum class PrepareWorkflowPhase {
    DETECTING,
    PLAN_READY,
    COMPATIBILITY_CONFIRMED,
    COMPATIBILITY_FALLBACK,
    PREPARING_RUNTIME,
    INSTALLING_DEPENDENCIES,
    VERIFYING,
    READY,
    BLOCKED,
    FAILED,
}

data class PrepareWorkflowFacts(
    val phase: PrepareWorkflowPhase,
    val primaryRuntimeId: String?,
    val directDependencyCount: Int,
    val resolvedPackageCount: Int?,
    val blockingIssueCount: Int,
    val usesInternalAlpineFallback: Boolean,
)

object PrepareWorkflowPresentationPolicy {

    fun fromResolution(resolution: ProjectEnvironmentResolution): PrepareWorkflowFacts {
        val plan = resolution.plan
        val blocking = plan.detection.blockingIssues.size
        val fallback =
            resolution.state == EnvironmentCompatibilityResolutionState.EMBEDDED_CPYTHON_INCOMPATIBLE &&
                plan.supports(EnvironmentBackend.INTERNAL_ALPINE)
        val phase = when {
            blocking > 0 || !plan.readyToPrepare -> PrepareWorkflowPhase.BLOCKED
            fallback -> PrepareWorkflowPhase.COMPATIBILITY_FALLBACK
            resolution.state == EnvironmentCompatibilityResolutionState.EMBEDDED_CPYTHON_RESOLVED ->
                PrepareWorkflowPhase.COMPATIBILITY_CONFIRMED
            else -> PrepareWorkflowPhase.PLAN_READY
        }
        return PrepareWorkflowFacts(
            phase = phase,
            primaryRuntimeId = plan.detection.primaryRuntime?.id,
            directDependencyCount = plan.detection.directDependencyCount,
            resolvedPackageCount = resolution.embeddedDependencyCount,
            blockingIssueCount = blocking,
            usesInternalAlpineFallback = fallback,
        )
    }

    fun fromProgressStage(stage: String): PrepareWorkflowPhase = when (stage.trim().uppercase()) {
        "CREATE_OR_REUSE_VENV",
        "PREPARE_NODE_TOOLCHAIN",
        "SYNC_NODE_WORKSPACE",
        -> PrepareWorkflowPhase.PREPARING_RUNTIME

        "INSTALL_REQUIREMENTS",
        "INSTALL_PYPROJECT",
        "INSTALL_NODE_DEPENDENCIES",
        "PREPARE_NODE_COMPONENTS",
        -> PrepareWorkflowPhase.INSTALLING_DEPENDENCIES

        "FINALIZING" -> PrepareWorkflowPhase.VERIFYING
        else -> PrepareWorkflowPhase.PREPARING_RUNTIME
    }

    fun fromInternalProgress(text: String): PrepareWorkflowPhase? {
        val stage = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("SIFTALPHA_X_INTERNAL_PREPARE_STAGE=") }
            ?.substringAfter('=')
            ?.trim()
            ?.uppercase()
            ?: return null
        return when (stage) {
            "PREPARING_RUNTIME",
            "ALPINE_RUNTIME",
            "ALPINE_PYTHON_RUNTIME",
            "ALPINE_PYTHON_RUNTIME_IDENTITY",
            "ALPINE_NODE_RUNTIME",
            "ALPINE_FALLBACK",
            -> PrepareWorkflowPhase.PREPARING_RUNTIME

            "CPYTHON",
            "ALPINE_PROJECT_DEPENDENCIES",
            "ALPINE_VITE_BUILD",
            -> PrepareWorkflowPhase.INSTALLING_DEPENDENCIES

            "VERIFYING" -> PrepareWorkflowPhase.VERIFYING
            else -> null
        }
    }
}
