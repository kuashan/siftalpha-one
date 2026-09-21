package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRuntimeSelectionChangePolicyTest {

    @Test
    fun `idle project can change runtime selection`() {
        assertTrue(
            ProjectRuntimeSelectionChangePolicy.canChange(
                ProjectRuntimeSelectionChangePolicy.Input(
                    runtimeState = RuntimeState.UNKNOWN,
                    operation = null,
                ),
            ),
        )
    }

    @Test
    fun `active runtime states block runtime selection change`() {
        listOf(
            RuntimeState.PREPARING,
            RuntimeState.STARTING,
            RuntimeState.RUNNING,
        ).forEach { state ->
            assertFalse(
                ProjectRuntimeSelectionChangePolicy.canChange(
                    ProjectRuntimeSelectionChangePolicy.Input(
                        runtimeState = state,
                        operation = null,
                    ),
                ),
            )
        }
    }

    @Test
    fun `non terminal operation blocks runtime selection change`() {
        val record = RuntimeOperationRecord(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STATUS,
            executionId = 7,
            generation = 1,
            startedAtEpochMs = 1_000L,
            deadlineAtEpochMs = null,
            phase = RuntimeOperationPhase.ACTIVE,
        )
        assertFalse(
            ProjectRuntimeSelectionChangePolicy.canChange(
                ProjectRuntimeSelectionChangePolicy.Input(
                    runtimeState = RuntimeState.STOPPED_BY_USER,
                    operation = record,
                ),
            ),
        )
    }

    @Test
    fun `terminal operation does not block runtime selection change`() {
        val record = RuntimeOperationRecord(
            projectId = "project-a",
            provider = RuntimeOperationProvider.INTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = null,
            generation = 1,
            startedAtEpochMs = 1_000L,
            deadlineAtEpochMs = 46_000L,
            phase = RuntimeOperationPhase.SUCCESS,
        )
        assertTrue(
            ProjectRuntimeSelectionChangePolicy.canChange(
                ProjectRuntimeSelectionChangePolicy.Input(
                    runtimeState = RuntimeState.STOPPED_BY_USER,
                    operation = record,
                ),
            ),
        )
    }
}
