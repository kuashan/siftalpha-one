package com.siftalpha.studio

import com.siftalpha.studio.presentation.ProjectActionPolicy
import com.siftalpha.studio.presentation.ProjectUiSnapshot
import com.siftalpha.studio.project.ConfigurationItem
import com.siftalpha.studio.project.ConfigurationSeverity
import com.siftalpha.studio.project.ConfigurationSource
import com.siftalpha.studio.runtime.RuntimeKind
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.runtime.RuntimeWebUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConfigurationEditorFlowTest {

    @Test
    fun optional_configuration_can_be_edited_and_saved() {
        val optional = item(
            key = "TEST_DEBUG",
            configured = false,
            severity = ConfigurationSeverity.OPTIONAL,
        )

        val plan = ProjectConfigurationUiController.planConfigurationSave(
            items = listOf(optional),
            enteredValues = mapOf(optional.key to "true"),
        )

        assertTrue(plan.canSave)
        assertTrue(plan.missingRequiredKeys.isEmpty())
        assertEquals(mapOf("TEST_DEBUG" to "true"), plan.valuesToSave)
    }

    @Test
    fun required_configuration_can_be_edited_and_saved() {
        val required = item(
            key = "TEST_API_KEY",
            configured = false,
            severity = ConfigurationSeverity.REQUIRED,
        )

        val plan = ProjectConfigurationUiController.planConfigurationSave(
            items = listOf(required),
            enteredValues = mapOf(required.key to "secret-value"),
        )
        val blocked = ProjectConfigurationUiController.planConfigurationSave(
            items = listOf(required),
            enteredValues = emptyMap(),
        )

        assertTrue(plan.canSave)
        assertEquals(mapOf("TEST_API_KEY" to "secret-value"), plan.valuesToSave)
        assertFalse(blocked.canSave)
        assertEquals(listOf("TEST_API_KEY"), blocked.missingRequiredKeys)
    }

    @Test
    fun mixed_configuration_keeps_optional_values_non_blocking() {
        val required = item(
            key = "TEST_API_KEY",
            configured = true,
            severity = ConfigurationSeverity.REQUIRED,
        )
        val optional = item(
            key = "TEST_SERVER",
            configured = false,
            severity = ConfigurationSeverity.OPTIONAL,
        )

        val plan = ProjectConfigurationUiController.planConfigurationSave(
            items = listOf(required, optional),
            enteredValues = mapOf(optional.key to ""),
        )

        assertTrue(plan.canSave)
        assertTrue(plan.valuesToSave.isEmpty())
        assertTrue(plan.missingRequiredKeys.isEmpty())
    }

    @Test
    fun configuration_remains_editable_after_stop() {
        val snapshot = ProjectUiSnapshot(
            identity = ProjectUiSnapshot.Identity(
                documentId = "doc-1",
                folderName = "project",
                displayName = "Project",
            ),
            runtime = ProjectUiSnapshot.Runtime(
                selection = ProjectUiSnapshot.Runtime.Selection(
                    status = ProjectUiSnapshot.Runtime.SelectionStatus.RESOLVED,
                    primary = RuntimeKind.PYTHON,
                ),
                supported = true,
            ),
            environment = ProjectUiSnapshot.Environment(ProjectUiSnapshot.Readiness.READY),
            configuration = ProjectUiSnapshot.Configuration(
                requiredCount = 0,
                configuredRequiredCount = 0,
                optionalMissingCount = 1,
            ),
            lifecycle = RuntimeState.STOPPED_BY_USER,
            web = ProjectUiSnapshot.Web(
                expected = false,
                status = RuntimeWebUiStatus.AUTO_DETECT,
                endpointReachable = null,
            ),
        )

        val policy = ProjectActionPolicy.resolve(snapshot)

        assertTrue(policy.isEnabled(ProjectActionPolicy.Action.CONFIGURE))
        assertTrue(policy.isEnabled(ProjectActionPolicy.Action.START))
        assertFalse(policy.isEnabled(ProjectActionPolicy.Action.STOP))

        val plan = ProjectConfigurationUiController.planConfigurationSave(
            items = listOf(
                item(
                    key = "TEST_DEBUG",
                    configured = false,
                    severity = ConfigurationSeverity.OPTIONAL,
                ),
            ),
            enteredValues = mapOf("TEST_DEBUG" to "false"),
        )
        assertTrue(plan.canSave)
        assertEquals(mapOf("TEST_DEBUG" to "false"), plan.valuesToSave)
    }

    private fun item(
        key: String,
        configured: Boolean,
        severity: ConfigurationSeverity,
    ): ConfigurationItem = ConfigurationItem(
        key = key,
        isConfigured = configured,
        severity = severity,
        source = if (severity == ConfigurationSeverity.REQUIRED) {
            ConfigurationSource.STATIC_REQUIRED_READ
        } else {
            ConfigurationSource.STATIC_OPTIONAL_READ
        },
    )
}
