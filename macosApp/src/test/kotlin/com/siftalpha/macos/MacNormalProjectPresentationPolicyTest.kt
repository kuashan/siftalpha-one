package com.siftalpha.macos

import com.siftalpha.core.lifecycle.ProjectLifecycleState
import com.siftalpha.core.process.ProjectProcessState
import com.siftalpha.studio.runtime.RuntimeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacNormalProjectPresentationPolicyTest {
    private fun view(
        lifecycle: ProjectLifecycleState,
        resultUrl: String? = null,
        environmentReady: Boolean = lifecycle != ProjectLifecycleState.ENVIRONMENT_NOT_PREPARED,
    ): MacProductProjectView {
        val imported = MacImportedProject(
            projectId = "macos:test",
            root = com.siftalpha.core.filesystem.ProjectFileEntry(
                id = "/tmp/test",
                name = "test",
                relativePath = ".",
                depth = 0,
                isDirectory = true,
            ),
            canonicalRootPath = "/tmp/test",
        )
        val needs = com.siftalpha.core.environment.ProjectEnvironmentNeeds(
            primaryRuntime = RuntimeKind.PYTHON,
        )
        val plan = MacProjectEnvironmentPlan(
            projectId = imported.projectId,
            status = MacProjectPlanStatus.READY_TO_PREPARE,
            needs = needs,
            preparationSteps = emptyList(),
            runtimeExecutables = mapOf(RuntimeKind.PYTHON to "/python3"),
            issues = emptyList(),
        )
        return MacProductProjectView(
            project = MacProductProject(
                imported = imported,
                snapshot = MacProjectSnapshot(
                    imported.projectId,
                    imported.canonicalRootPath,
                    listOf("main.py"),
                    null,
                    null,
                    null,
                ),
                plan = plan,
            ),
            workflow = MacWorkflowStatus(
                projectId = imported.projectId,
                lifecycle = lifecycle,
                environmentReady = environmentReady,
                processState = if (lifecycle == ProjectLifecycleState.RUNNING) {
                    ProjectProcessState.RUNNING
                } else {
                    ProjectProcessState.STOPPED
                },
                operation = null,
                webEndpoint = resultUrl?.let {
                    MacProjectWebEndpoint(it, MacProjectWebEndpoint.Source.LOG_OUTPUT)
                },
            ),
            lastError = null,
        )
    }

    @Test
    fun notPreparedMapsToPrepare() {
        assertEquals(
            MacNormalPrimaryAction.PREPARE,
            MacNormalProjectPresentationPolicy.resolve(
                view(ProjectLifecycleState.ENVIRONMENT_NOT_PREPARED, environmentReady = false),
            ).primaryAction,
        )
    }

    @Test
    fun readyMapsToRun() {
        assertEquals(
            MacNormalPrimaryAction.RUN,
            MacNormalProjectPresentationPolicy.resolve(
                view(ProjectLifecycleState.READY_TO_RUN),
            ).primaryAction,
        )
    }

    @Test
    fun runningWithResultMapsToOpenAndKeepsStop() {
        val result = MacNormalProjectPresentationPolicy.resolve(
            view(ProjectLifecycleState.RUNNING, "http://127.0.0.1:8765"),
        )
        assertEquals(MacNormalPrimaryAction.OPEN_RESULT, result.primaryAction)
        assertTrue(result.showSecondaryStop)
    }
}
