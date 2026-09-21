package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.ProjectStore
import com.siftalpha.studio.project.V04ProjectGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectControlHubTest {

    @Test
    fun `run reads current project selection and delegates exact project identity`() {
        val project = project("project-a", "alpha")
        val fake = RecordingExecutor()
        var selection = ProjectRuntimeSelection.EMBEDDED_R
        val hub = ProjectControlHub(
            selectionReader = { projectId ->
                assertEquals("project-a", projectId)
                selection
            },
            executor = fake,
        )

        val result = hub.run(
            project,
            ProjectControlHub.RunRequest(webLogDiscoveryAllowed = true, webHintPorts = listOf(8000)),
        )

        assertEquals(ProjectControlHub.Action.RUN, result.action)
        assertEquals("project-a", fake.lastProjectId)
        assertEquals(ProjectRuntimeSelection.EMBEDDED_R, fake.lastSelection)
        assertEquals(true, fake.lastRunRequest?.webLogDiscoveryAllowed)
        assertEquals(listOf(8000), fake.lastRunRequest?.webHintPorts)

        selection = ProjectRuntimeSelection.TERMUX
        hub.run(project)

        assertEquals(ProjectRuntimeSelection.TERMUX, fake.lastSelection)
    }

    @Test
    fun `stop delegates only the requested project and never a neighboring project`() {
        val projectA = project("project-a", "alpha")
        val projectB = project("project-b", "beta")
        val fake = RecordingExecutor()
        val selections = mapOf(
            "project-a" to ProjectRuntimeSelection.EMBEDDED_R,
            "project-b" to ProjectRuntimeSelection.TERMUX,
        )
        val hub = ProjectControlHub(
            selectionReader = { selections.getValue(it) },
            executor = fake,
        )

        hub.stop(projectA)

        assertEquals("project-a", fake.lastProjectId)
        assertEquals(ProjectRuntimeSelection.EMBEDDED_R, fake.lastSelection)
        assertEquals(1, fake.stopCalls)
        assertEquals(0, fake.runCalls)

        hub.stop(projectB)

        assertEquals("project-b", fake.lastProjectId)
        assertEquals(ProjectRuntimeSelection.TERMUX, fake.lastSelection)
        assertEquals(2, fake.stopCalls)
    }

    @Test
    fun `hub does not own a second runtime state machine`() {
        val project = project("project-a", "alpha")
        val fake = RecordingExecutor(
            runResult = ProjectControlHub.Result.Dispatched(
                action = ProjectControlHub.Action.RUN,
                provider = RuntimeOperationProvider.INTERNAL,
                observedState = RuntimeState.RUNNING,
            ),
            stopResult = ProjectControlHub.Result.Dispatched(
                action = ProjectControlHub.Action.STOP,
                provider = RuntimeOperationProvider.INTERNAL,
                observedState = RuntimeState.RUNNING,
            ),
        )
        val hub = ProjectControlHub(
            selectionReader = { ProjectRuntimeSelection.EMBEDDED_R },
            executor = fake,
        )

        val run = hub.run(project)
        val stop = hub.stop(project)

        assertEquals(RuntimeState.RUNNING, (run as ProjectControlHub.Result.Dispatched).observedState)
        assertEquals(RuntimeState.RUNNING, (stop as ProjectControlHub.Result.Dispatched).observedState)
        assertNull(fake.localState)
    }

    @Test
    fun `required configuration contract is forwarded without interpretation by hub`() {
        val project = project("project-a", "alpha")
        val fake = RecordingExecutor()
        val hub = ProjectControlHub(
            selectionReader = { ProjectRuntimeSelection.EMBEDDED_R },
            executor = fake,
        )

        hub.run(
            project,
            ProjectControlHub.RunRequest(requiredConfiguration = true),
        )

        assertEquals(true, fake.lastRunRequest?.requiredConfiguration)
    }

    private fun project(documentId: String, folderName: String): V04ProjectGateway.RuntimeProject =
        V04ProjectGateway.RuntimeProject(
            summary = ProjectStore.ProjectSummary(
                name = folderName,
                description = "",
                entry = "main.py",
                run = "python main.py",
                source = "test",
                documentId = documentId,
            ),
            folderName = folderName,
            sourceUrl = null,
            runtimeSelection = ProjectRuntimeExecutionPlanner.select(
                relativePaths = listOf("main.py"),
                declaredType = "python",
            ),
        )

    private class RecordingExecutor(
        private val runResult: ProjectControlHub.Result = ProjectControlHub.Result.Dispatched(
            action = ProjectControlHub.Action.RUN,
            provider = RuntimeOperationProvider.INTERNAL,
            observedState = RuntimeState.STARTING,
        ),
        private val stopResult: ProjectControlHub.Result = ProjectControlHub.Result.Dispatched(
            action = ProjectControlHub.Action.STOP,
            provider = RuntimeOperationProvider.INTERNAL,
            observedState = RuntimeState.RUNNING,
        ),
    ) : ProjectControlHub.Executor {
        var lastProjectId: String? = null
        var lastSelection: ProjectRuntimeSelection? = null
        var lastRunRequest: ProjectControlHub.RunRequest? = null
        var runCalls: Int = 0
        var stopCalls: Int = 0

        // Deliberately absent: the hub has no independent RuntimeState store.
        val localState: RuntimeState? = null

        override fun run(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
            request: ProjectControlHub.RunRequest,
        ): ProjectControlHub.Result {
            runCalls += 1
            lastProjectId = project.summary.documentId
            lastSelection = selection
            lastRunRequest = request
            return runResult
        }

        override fun stop(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
        ): ProjectControlHub.Result {
            stopCalls += 1
            lastProjectId = project.summary.documentId
            lastSelection = selection
            return stopResult
        }
    }
}
