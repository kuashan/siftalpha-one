package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalProjectActivityContractTest {
    @Test
    fun stopIsAProjectControlCommandNotANormalActivity() {
        assertNull(
            ExternalProjectActivityContract.operationFor(ProjectRuntimeController.Action.STOP),
        )
        assertNull(
            ExternalProjectActivityContract.operationFor(ProjectRuntimeController.Action.CLONE_GITHUB),
        )
        assertEquals(
            "status",
            ExternalProjectActivityContract.operationFor(ProjectRuntimeController.Action.STATUS),
        )
        assertEquals(
            mapOf(
                ProjectRuntimeController.Action.PREPARE to "prepare",
                ProjectRuntimeController.Action.START to "start",
                ProjectRuntimeController.Action.STATUS to "status",
                ProjectRuntimeController.Action.LOGS to "logs",
                ProjectRuntimeController.Action.CLEAN to "clean",
            ),
            ProjectRuntimeController.Action.values()
                .filter { it != ProjectRuntimeController.Action.STOP && it != ProjectRuntimeController.Action.CLONE_GITHUB }
                .associateWith { ExternalProjectActivityContract.operationFor(it) },
        )
    }
}
