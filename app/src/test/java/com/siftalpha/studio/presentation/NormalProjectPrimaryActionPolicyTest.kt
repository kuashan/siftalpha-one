package com.siftalpha.studio.presentation

import com.siftalpha.studio.runtime.RuntimeKind
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.runtime.RuntimeWebUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `ready shared environment maps to run and never back to prepare project`() {
        val shared = ProjectActionPolicy.resolve(
            ProjectUiSnapshot(
                identity = ProjectUiSnapshot.Identity(
                    documentId = "project:ready",
                    folderName = "ready",
                    displayName = "Ready",
                ),
                runtime = ProjectUiSnapshot.Runtime(
                    selection = ProjectUiSnapshot.Runtime.Selection(
                        status = ProjectUiSnapshot.Runtime.SelectionStatus.RESOLVED,
                        primary = RuntimeKind.PYTHON,
                    ),
                    supported = true,
                ),
                environment = ProjectUiSnapshot.Environment(ProjectUiSnapshot.Readiness.READY),
                configuration = ProjectUiSnapshot.Configuration(0, 0),
                lifecycle = RuntimeState.STOPPED_BY_USER,
                web = ProjectUiSnapshot.Web(
                    expected = false,
                    status = RuntimeWebUiStatus.AUTO_DETECT,
                    endpointReachable = null,
                ),
            ),
        )

        assertFalse(shared.isEnabled(ProjectActionPolicy.Action.PREPARE))
        assertEquals(ProjectActionPolicy.Action.START, shared.primaryAction)
        assertEquals(
            NormalProjectPrimaryActionPolicy.Action.RUN,
            NormalProjectPrimaryActionPolicy.resolve(shared),
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
