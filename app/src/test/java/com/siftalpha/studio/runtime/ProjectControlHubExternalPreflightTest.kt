package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.ProjectStore
import com.siftalpha.studio.project.V04ProjectGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectControlHubExternalPreflightTest {
    @Test
    fun prepareAndRunAreBlockedByPermissionBeforeExecutorDispatch() {
        val executor = RecordingExecutor()
        val hub = hub(executor, ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED)

        val prepare = hub.prepare(project()) as ProjectControlHub.Result.Rejected
        val run = hub.run(project()) as ProjectControlHub.Result.Rejected

        assertEquals(ProjectControlHub.Failure.EXTERNAL_PREFLIGHT_REQUIRED, prepare.failure)
        assertEquals(ProjectControlHub.Failure.EXTERNAL_PREFLIGHT_REQUIRED, run.failure)
        assertEquals(ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED, prepare.readiness)
        assertEquals(0, executor.prepareCalls)
        assertEquals(0, executor.runCalls)
    }

    @Test
    fun prepareAndRunAreBlockedByBridgeSetupBeforeExecutorDispatch() {
        val executor = RecordingExecutor()
        val hub = hub(executor, ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED)

        val prepare = hub.prepare(project()) as ProjectControlHub.Result.Rejected
        val run = hub.run(project()) as ProjectControlHub.Result.Rejected

        assertEquals(ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED, prepare.readiness)
        assertEquals(ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED, run.readiness)
        assertEquals(0, executor.prepareCalls)
        assertEquals(0, executor.runCalls)
    }

    @Test
    fun readyPreflightDispatchesTheExistingExecutor() {
        val executor = RecordingExecutor()
        val hub = hub(executor, ExternalProviderReadiness.READY)

        assertTrue(hub.prepare(project()) is ProjectControlHub.Result.Completed)
        assertTrue(hub.run(project()) is ProjectControlHub.Result.Dispatched)

        assertEquals(1, executor.prepareCalls)
        assertEquals(1, executor.runCalls)
    }

    private fun hub(
        executor: RecordingExecutor,
        readiness: ExternalProviderReadiness,
    ) = ProjectControlHub(
        selectionReader = { ProjectRuntimeSelection.TERMUX },
        executor = executor,
        externalPreflight = object : ExternalProviderPreflightGate {
            override fun ensureReady() = ExternalProviderPreflightResult(
                readiness = readiness,
                termuxInstalled = readiness != ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
                runCommandPermissionGranted = readiness != ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED,
                allowExternalApps = if (readiness == ExternalProviderReadiness.READY) true else null,
                bridgeResponsive = if (readiness == ExternalProviderReadiness.READY) true else null,
                lastProbeAtEpochMs = 1_000L,
            )
        },
    )

    private fun project() = V04ProjectGateway.RuntimeProject(
        summary = ProjectStore.ProjectSummary(
            name = "alpha",
            description = "",
            entry = "main.py",
            run = "python main.py",
            source = "test",
            documentId = "project-a",
        ),
        folderName = "alpha",
        sourceUrl = null,
        runtimeSelection = ProjectRuntimeExecutionPlanner.select(
            relativePaths = listOf("main.py"),
            declaredType = "python",
        ),
    )

    private class RecordingExecutor : ProjectControlHub.Executor {
        var prepareCalls = 0
        var runCalls = 0

        override fun prepare(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
        ) = ProjectControlHub.Result.Completed(
            action = ProjectControlHub.Action.PREPARE,
            provider = RuntimeOperationProvider.EXTERNAL,
            environmentReady = true,
        ).also { prepareCalls += 1 }

        override fun run(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
            request: ProjectControlHub.RunRequest,
        ) = ProjectControlHub.Result.Dispatched(
            action = ProjectControlHub.Action.RUN,
            provider = RuntimeOperationProvider.EXTERNAL,
            observedState = RuntimeState.STARTING,
        ).also { runCalls += 1 }

        override fun stop(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
        ) = ProjectControlHub.Result.NoOp(
            action = ProjectControlHub.Action.STOP,
            observedState = RuntimeState.STOPPED_BY_USER,
            detail = "test",
        )
    }
}
