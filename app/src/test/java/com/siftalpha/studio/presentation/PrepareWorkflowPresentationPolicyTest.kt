package com.siftalpha.studio.presentation

import com.siftalpha.studio.runtime.EnvironmentBackend
import com.siftalpha.studio.runtime.EnvironmentBuildStep
import com.siftalpha.studio.runtime.EnvironmentCompatibilityResolutionState
import com.siftalpha.studio.runtime.EnvironmentDependencySource
import com.siftalpha.studio.runtime.ProjectEnvironmentDetection
import com.siftalpha.studio.runtime.ProjectEnvironmentPlan
import com.siftalpha.studio.runtime.ProjectEnvironmentResolution
import com.siftalpha.studio.runtime.ProjectRuntimeExecutionPlanner
import com.siftalpha.studio.runtime.RuntimeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareWorkflowPresentationPolicyTest {

    @Test
    fun resolvedEmbeddedPlanIsPresentedAsCompatibilityConfirmed() {
        val resolution = ProjectEnvironmentResolution(
            plan = readyPlan(),
            state = EnvironmentCompatibilityResolutionState.EMBEDDED_CPYTHON_RESOLVED,
            selectedBackend = EnvironmentBackend.EMBEDDED_CPYTHON,
            embeddedDependencyCount = 12,
        )

        val facts = PrepareWorkflowPresentationPolicy.fromResolution(resolution)

        assertEquals(PrepareWorkflowPhase.COMPATIBILITY_CONFIRMED, facts.phase)
        assertEquals(4, facts.directDependencyCount)
        assertEquals(12, facts.resolvedPackageCount)
    }

    @Test
    fun incompatibleEmbeddedPlanUsesPresentationFallbackWithoutChangingPlan() {
        val resolution = ProjectEnvironmentResolution(
            plan = readyPlan(),
            state = EnvironmentCompatibilityResolutionState.EMBEDDED_CPYTHON_INCOMPATIBLE,
            selectedBackend = EnvironmentBackend.INTERNAL_ALPINE,
            detail = "wheel unavailable",
        )

        val facts = PrepareWorkflowPresentationPolicy.fromResolution(resolution)

        assertEquals(PrepareWorkflowPhase.COMPATIBILITY_FALLBACK, facts.phase)
        assertTrue(facts.usesInternalAlpineFallback)
    }

    @Test
    fun progressStagesMapToSimpleUserPhases() {
        assertEquals(
            PrepareWorkflowPhase.INSTALLING_DEPENDENCIES,
            PrepareWorkflowPresentationPolicy.fromProgressStage("INSTALL_REQUIREMENTS"),
        )
        assertEquals(
            PrepareWorkflowPhase.VERIFYING,
            PrepareWorkflowPresentationPolicy.fromProgressStage("FINALIZING"),
        )
    }

    private fun readyPlan(): ProjectEnvironmentPlan = ProjectEnvironmentPlan(
        schemaVersion = 1,
        planId = "plan",
        detection = ProjectEnvironmentDetection(
            projectFingerprint = "fingerprint",
            selection = ProjectRuntimeExecutionPlanner.Selection.Resolved(
                primary = RuntimeKind.PYTHON,
                supplemental = emptyList(),
            ),
            primaryRuntime = RuntimeKind.PYTHON,
            supplementalRuntimes = emptyList(),
            dependencySource = EnvironmentDependencySource.REQUIREMENTS_TXT,
            directDependencyCount = 4,
            pythonRequiresVersion = null,
            pythonOptionalDependencyGroups = emptyList(),
            viteComponentCount = 0,
            declaredEntry = "main.py",
            declaredRun = "python main.py",
            embeddedCpythonEligible = true,
            internalAlpineEligible = true,
            issues = emptyList(),
        ),
        backendCandidates = listOf(
            EnvironmentBackend.EMBEDDED_CPYTHON,
            EnvironmentBackend.INTERNAL_ALPINE,
        ),
        preferredBackend = EnvironmentBackend.EMBEDDED_CPYTHON,
        buildSteps = listOf(
            EnvironmentBuildStep.VALIDATE_PLAN,
            EnvironmentBuildStep.CREATE_ENVIRONMENT,
            EnvironmentBuildStep.PYTHON_INSTALL,
            EnvironmentBuildStep.VERIFY_ENVIRONMENT,
        ),
        pythonInstallExtras = emptyList(),
    )
}
