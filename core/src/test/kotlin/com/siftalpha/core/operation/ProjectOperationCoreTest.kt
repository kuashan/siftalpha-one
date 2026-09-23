package com.siftalpha.core.operation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOperationCoreTest {
    @Test
    fun emptyProjectSlotAcceptsAnyOperation() {
        ProjectOperationAction.entries.forEach { action ->
            assertTrue(
                ProjectOperationPolicy.canBegin(
                    current = null,
                    requestedProjectId = "project-a",
                    requestedAction = action,
                ),
            )
        }
    }

    @Test
    fun activeOperationBlocksDuplicateMutableWorkForSameProject() {
        val current = active(
            projectId = "project-a",
            action = ProjectOperationAction.START,
        )

        listOf(
            ProjectOperationAction.PREPARE,
            ProjectOperationAction.START,
            ProjectOperationAction.STATUS,
            ProjectOperationAction.LOGS,
            ProjectOperationAction.CLEAN,
        ).forEach { requested ->
            assertFalse(
                ProjectOperationPolicy.canBegin(
                    current = current,
                    requestedProjectId = "project-a",
                    requestedAction = requested,
                ),
            )
        }
    }

    @Test
    fun stopCanSupersedeAnotherActiveOperationForSameProject() {
        val current = active(
            projectId = "project-a",
            action = ProjectOperationAction.START,
        )

        assertTrue(
            ProjectOperationPolicy.canBegin(
                current = current,
                requestedProjectId = "project-a",
                requestedAction = ProjectOperationAction.STOP,
            ),
        )
        assertTrue(
            ProjectOperationPolicy.canSupersede(
                current = current,
                requestedProjectId = "project-a",
                requestedAction = ProjectOperationAction.STOP,
            ),
        )
    }

    @Test
    fun duplicateStopIsRejected() {
        val current = active(
            projectId = "project-a",
            action = ProjectOperationAction.STOP,
        )

        assertFalse(
            ProjectOperationPolicy.canBegin(
                current = current,
                requestedProjectId = "project-a",
                requestedAction = ProjectOperationAction.STOP,
            ),
        )
    }

    @Test
    fun operationOnAnotherProjectNeverBlocksRequestedProject() {
        val current = active(
            projectId = "project-a",
            action = ProjectOperationAction.START,
        )

        ProjectOperationAction.entries.forEach { action ->
            assertTrue(
                ProjectOperationPolicy.canBegin(
                    current = current,
                    requestedProjectId = "project-b",
                    requestedAction = action,
                ),
            )
        }
    }

    @Test
    fun terminalPhasesAreStableSharedFacts() {
        assertFalse(ProjectOperationPhase.ACCEPTED.terminal)
        assertFalse(ProjectOperationPhase.ACTIVE.terminal)
        assertTrue(ProjectOperationPhase.SUCCESS.terminal)
        assertTrue(ProjectOperationPhase.FAILED.terminal)
        assertTrue(ProjectOperationPhase.CANCELLED.terminal)
        assertTrue(ProjectOperationPhase.TIMED_OUT.terminal)
    }

    private fun active(
        projectId: String,
        action: ProjectOperationAction,
    ): ProjectOperationOwnership = ProjectOperationOwnership(
        projectId = projectId,
        action = action,
        phase = ProjectOperationPhase.ACTIVE,
        generation = 1L,
    )
}
