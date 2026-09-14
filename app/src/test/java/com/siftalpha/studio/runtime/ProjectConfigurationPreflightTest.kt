package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.ProjectConfigurationInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConfigurationPreflightTest {

    @Test
    fun `required values can be satisfied by project env or protected store`() {
        val profile = ProjectConfigurationInspector.Profile(
            requirements = listOf(
                ProjectConfigurationInspector.Requirement(
                    name = "DATABASE_PATH",
                    secret = false,
                    required = true,
                    description = "database",
                ),
                ProjectConfigurationInspector.Requirement(
                    name = "GEMINI_API_KEY",
                    secret = true,
                    required = true,
                    description = "AI key",
                ),
            ),
            configuredProjectEnvKeys = setOf("DATABASE_PATH"),
            credentialCandidates = listOf("TUSHARE_TOKEN"),
        )

        val result = ProjectConfigurationPreflight.evaluate(
            profile,
            protectedConfiguredKeys = setOf("GEMINI_API_KEY"),
        )

        assertTrue(result.ready)
        assertEquals(2, result.requiredCount)
        assertEquals(2, result.configuredRequiredCount)
        assertTrue(result.missingRequired.isEmpty())
        assertEquals(1, result.credentialCandidateCount)
    }

    @Test
    fun `candidate env keys never become blocking requirements`() {
        val profile = ProjectConfigurationInspector.Profile(
            requirements = emptyList(),
            configuredProjectEnvKeys = emptySet(),
            credentialCandidates = listOf("OPENAI_API_KEY", "TUSHARE_TOKEN"),
        )

        val result = ProjectConfigurationPreflight.evaluate(profile, emptySet())

        assertTrue(result.ready)
        assertEquals(0, result.requiredCount)
        assertEquals(2, result.credentialCandidateCount)
    }

    @Test
    fun `missing required values block launch deterministically`() {
        val required = ProjectConfigurationInspector.Requirement(
            name = "SERVICE_TOKEN",
            secret = true,
            required = true,
            description = "service credential",
        )
        val profile = ProjectConfigurationInspector.Profile(
            requirements = listOf(required),
            configuredProjectEnvKeys = emptySet(),
            credentialCandidates = emptyList(),
        )

        val result = ProjectConfigurationPreflight.evaluate(profile, emptySet())

        assertFalse(result.ready)
        assertEquals(listOf(required), result.missingRequired)
        assertEquals(0, result.configuredRequiredCount)
    }

    @Test
    fun `runtime reported names become blocking requirements after discovery`() {
        val profile = ProjectConfigurationInspector.Profile(
            requirements = emptyList(),
            configuredProjectEnvKeys = emptySet(),
            credentialCandidates = listOf("DYNAMIC_API_KEY"),
            configurationCandidates = listOf(
                ProjectConfigurationInspector.Requirement(
                    name = "DYNAMIC_API_KEY",
                    secret = true,
                    required = false,
                    description = "",
                ),
            ),
        )

        val result = ProjectConfigurationPreflight.evaluate(
            profile = profile,
            protectedConfiguredKeys = emptySet(),
            runtimeRequiredNames = setOf("DYNAMIC_API_KEY"),
        )

        assertFalse(result.ready)
        assertEquals(1, result.requiredCount)
        assertEquals(listOf("DYNAMIC_API_KEY"), result.missingRequired.map { it.name })
    }

    @Test
    fun `runtime reported name is satisfied by protected configuration`() {
        val profile = ProjectConfigurationInspector.Profile(
            requirements = emptyList(),
            configuredProjectEnvKeys = emptySet(),
            credentialCandidates = emptyList(),
        )

        val result = ProjectConfigurationPreflight.evaluate(
            profile = profile,
            protectedConfiguredKeys = setOf("DYNAMIC_API_KEY"),
            runtimeRequiredNames = setOf("DYNAMIC_API_KEY"),
        )

        assertTrue(result.ready)
        assertEquals(1, result.requiredCount)
        assertEquals(1, result.configuredRequiredCount)
        assertTrue(result.missingRequired.isEmpty())
    }

}
