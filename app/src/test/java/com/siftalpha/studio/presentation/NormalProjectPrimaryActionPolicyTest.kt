package com.siftalpha.studio.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalProjectPrimaryActionPolicyTest {

    @Test
    fun `prepare configure start and stop map to normal first level actions`() {
        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT,
            NormalProjectPrimaryActionPolicy.resolve(result(ProjectActionPolicy.Action.PREPARE)),
        )
        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.CONFIGURE,
            NormalProjectPrimaryActionPolicy.resolve(result(ProjectActionPolicy.Action.CONFIGURE)),
        )
        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.RUN,
            NormalProjectPrimaryActionPolicy.resolve(result(ProjectActionPolicy.Action.START)),
        )
        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.STOP,
            NormalProjectPrimaryActionPolicy.resolve(result(ProjectActionPolicy.Action.STOP)),
        )
    }

    @Test
    fun `environment status falls back to prepare when prepare is the direct secondary action`() {
        val policy = result(
            primary = ProjectActionPolicy.Action.STATUS,
            secondary = ProjectActionPolicy.Action.PREPARE,
        )

        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT,
            NormalProjectPrimaryActionPolicy.resolve(policy),
        )
    }

    @Test
    fun `unrelated status is not silently converted into prepare`() {
        val policy = result(
            primary = ProjectActionPolicy.Action.STATUS,
            secondary = null,
        )

        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.NONE,
            NormalProjectPrimaryActionPolicy.resolve(policy),
        )
    }

    private fun result(
        primary: ProjectActionPolicy.Action?,
        secondary: ProjectActionPolicy.Action? = null,
    ): ProjectActionPolicy.Result {
        val enabled = buildSet {
            primary?.let(::add)
            secondary?.let(::add)
        }
        val actions = ProjectActionPolicy.Action.entries.associateWith { action ->
            if (action in enabled) {
                ProjectActionPolicy.ActionDecision(enabled = true)
            } else {
                ProjectActionPolicy.ActionDecision(
                    enabled = false,
                    disableReason = ProjectActionPolicy.DisableReason.PENDING_OPERATION,
                )
            }
        }
        return ProjectActionPolicy.Result(
            summary = ProjectActionPolicy.MessageKey.READY_TO_RUN,
            explanationResourceKey = ProjectActionPolicy.MessageKey.READY_TO_RUN.resourceKey,
            primaryAction = primary,
            directSecondaryAction = secondary,
            disableReason = null,
            detailEntry = ProjectActionPolicy.DetailEntry.RUNTIME,
            actions = actions,
        )
    }
}
