package com.siftalpha.core.environment

import com.siftalpha.studio.runtime.RuntimeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEnvironmentNeedsTest {
    @Test
    fun purePythonNeedsDoNotChooseAPlatformBackend() {
        val needs = ProjectEnvironmentNeeds(
            primaryRuntime = RuntimeKind.PYTHON,
            directDependencyCount = 2,
            pythonRequiresVersion = ">=3.14",
        )

        assertTrue(needs.requiresPython)
        assertFalse(needs.requiresNode)
        assertEquals(
            listOf(
                EnvironmentPreparationStep.VALIDATE_PLAN,
                EnvironmentPreparationStep.ACQUIRE_RUNTIME,
                EnvironmentPreparationStep.CREATE_ENVIRONMENT,
                EnvironmentPreparationStep.PYTHON_INSTALL,
                EnvironmentPreparationStep.VERIFY_ENVIRONMENT,
                EnvironmentPreparationStep.COMMIT_ENVIRONMENT,
            ),
            ProjectEnvironmentNeedPolicy.preparationSteps(needs),
        )
    }

    @Test
    fun pythonWithSupplementalNodeButNoViteDoesNotInventNodePreparation() {
        val needs = ProjectEnvironmentNeeds(
            primaryRuntime = RuntimeKind.PYTHON,
            supplementalRuntimes = listOf(RuntimeKind.NODE_JS),
            viteComponentCount = 0,
        )

        assertTrue(needs.requiresNode)
        assertFalse(needs.requiresNodeBuild)
        assertEquals(
            listOf(
                EnvironmentPreparationStep.VALIDATE_PLAN,
                EnvironmentPreparationStep.ACQUIRE_RUNTIME,
                EnvironmentPreparationStep.CREATE_ENVIRONMENT,
                EnvironmentPreparationStep.PYTHON_INSTALL,
                EnvironmentPreparationStep.VERIFY_ENVIRONMENT,
                EnvironmentPreparationStep.COMMIT_ENVIRONMENT,
            ),
            ProjectEnvironmentNeedPolicy.preparationSteps(needs),
        )
    }

    @Test
    fun pythonWithViteRequiresNodeWorkBeforePythonInstall() {
        val needs = ProjectEnvironmentNeeds(
            primaryRuntime = RuntimeKind.PYTHON,
            supplementalRuntimes = listOf(RuntimeKind.NODE_JS),
            pythonOptionalDependencyGroups = listOf("dev", "web"),
            viteComponentCount = 1,
        )

        val steps = ProjectEnvironmentNeedPolicy.preparationSteps(needs)
        val nodeInstall = steps.indexOf(EnvironmentPreparationStep.NODE_INSTALL)
        val nodeBuild = steps.indexOf(EnvironmentPreparationStep.NODE_BUILD)
        val pythonInstall = steps.indexOf(EnvironmentPreparationStep.PYTHON_INSTALL)

        assertTrue(needs.requiresNode)
        assertTrue(needs.requiresNodeBuild)
        assertTrue(nodeInstall in 0 until nodeBuild)
        assertTrue(nodeBuild in 0 until pythonInstall)
        assertEquals(listOf("web"), ProjectEnvironmentNeedPolicy.pythonInstallExtras(needs))
    }

    @Test
    fun plainNodeProjectNeedsInstallWithoutInventingBuildStep() {
        val needs = ProjectEnvironmentNeeds(
            primaryRuntime = RuntimeKind.NODE_JS,
            viteComponentCount = 0,
        )

        assertEquals(
            listOf(
                EnvironmentPreparationStep.VALIDATE_PLAN,
                EnvironmentPreparationStep.ACQUIRE_RUNTIME,
                EnvironmentPreparationStep.CREATE_ENVIRONMENT,
                EnvironmentPreparationStep.NODE_INSTALL,
                EnvironmentPreparationStep.VERIFY_ENVIRONMENT,
                EnvironmentPreparationStep.COMMIT_ENVIRONMENT,
            ),
            ProjectEnvironmentNeedPolicy.preparationSteps(needs),
        )
    }

    @Test
    fun blockingDetectionStopsBeforeProviderSelectionOrPreparation() {
        val needs = ProjectEnvironmentNeeds(
            primaryRuntime = RuntimeKind.PYTHON,
            hasBlockingIssues = true,
        )

        assertEquals(
            listOf(EnvironmentPreparationStep.VALIDATE_PLAN),
            ProjectEnvironmentNeedPolicy.preparationSteps(needs),
        )
    }

    @Test
    fun environmentNeedsContainNoPlatformProviderChoice() {
        val properties = ProjectEnvironmentNeeds::class.java.declaredFields.map { it.name }

        assertFalse(properties.any { "android" in it.lowercase() })
        assertFalse(properties.any { "mac" in it.lowercase() })
        assertFalse(properties.any { "windows" in it.lowercase() })
        assertFalse(properties.any { "backend" in it.lowercase() })
        assertFalse(properties.any { "provider" in it.lowercase() })
    }
}
