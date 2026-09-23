package com.siftalpha.core.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectProcessControlTest {
    @Test
    fun ownershipIsStrictlyProjectScoped() {
        val a = ProjectProcessScope("project-a")
        val b = ProjectProcessScope("project-b")
        val handleA = ProjectProcessHandle(a, "opaque-a")

        assertTrue(ProjectProcessControlPolicy.owns(a, handleA))
        assertFalse(ProjectProcessControlPolicy.owns(b, handleA))
        assertFalse(ProjectProcessControlPolicy.sameProject(a, b))
    }

    @Test(expected = IllegalArgumentException::class)
    fun crossProjectHandleCannotBeClaimedByAnotherProject() {
        ProjectProcessControlPolicy.requireOwned(
            scope = ProjectProcessScope("project-b"),
            handle = ProjectProcessHandle(
                scope = ProjectProcessScope("project-a"),
                platformHandle = "opaque-a",
            ),
        )
    }

    @Test
    fun processControlCanStopOneProjectWithoutTouchingAnother() {
        val control = MemoryProcessControl()
        val a = ProjectProcessScope("project-a")
        val b = ProjectProcessScope("project-b")

        control.start(
            ProjectProcessLaunchRequest(
                scope = a,
                executable = "python",
                arguments = listOf("a.py"),
            ),
        )
        control.start(
            ProjectProcessLaunchRequest(
                scope = b,
                executable = "python",
                arguments = listOf("b.py"),
            ),
        )

        assertEquals(ProjectProcessState.RUNNING, control.status(a).state)
        assertEquals(ProjectProcessState.RUNNING, control.status(b).state)

        assertEquals(ProjectStopOutcome.STOPPED, control.stopProject(a).outcome)
        assertEquals(ProjectProcessState.STOPPED, control.status(a).state)
        assertEquals(ProjectProcessState.RUNNING, control.status(b).state)
    }

    @Test
    fun logsRemainProjectScoped() {
        val control = MemoryProcessControl()
        val a = ProjectProcessScope("project-a")
        val b = ProjectProcessScope("project-b")

        control.start(ProjectProcessLaunchRequest(a, executable = "python"))
        control.start(ProjectProcessLaunchRequest(b, executable = "node"))

        assertEquals("project-a", control.logs(a, 1024).stdout)
        assertEquals("project-b", control.logs(b, 1024).stdout)
    }

    private class MemoryProcessControl : ProjectProcessControl {
        private val states = linkedMapOf<String, ProjectProcessState>()

        override fun start(request: ProjectProcessLaunchRequest): ProjectProcessHandle {
            states[request.scope.projectId] = ProjectProcessState.RUNNING
            return ProjectProcessHandle(
                scope = request.scope,
                platformHandle = "handle-" + request.scope.projectId,
            )
        }

        override fun status(scope: ProjectProcessScope): ProjectProcessStatus =
            ProjectProcessStatus(
                scope = scope,
                state = states[scope.projectId] ?: ProjectProcessState.UNKNOWN,
            )

        override fun logs(
            scope: ProjectProcessScope,
            maxBytes: Int,
        ): ProjectProcessLogs {
            require(maxBytes > 0)
            return ProjectProcessLogs(scope = scope, stdout = scope.projectId)
        }

        override fun stopProject(scope: ProjectProcessScope): ProjectStopResult {
            val current = states[scope.projectId]
                ?: return ProjectStopResult(scope, ProjectStopOutcome.NOT_FOUND)
            if (current == ProjectProcessState.STOPPED) {
                return ProjectStopResult(scope, ProjectStopOutcome.ALREADY_STOPPED)
            }
            states[scope.projectId] = ProjectProcessState.STOPPED
            return ProjectStopResult(scope, ProjectStopOutcome.STOPPED)
        }
    }
}
