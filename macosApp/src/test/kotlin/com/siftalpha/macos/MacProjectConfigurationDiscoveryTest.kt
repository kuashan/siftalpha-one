package com.siftalpha.macos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacProjectConfigurationDiscoveryTest {
    @Test
    fun extractsOnlyStrongMissingEnvironmentEvidence() {
        val log = """
            level=warning msg="The \"OPTIONAL_KEY\" variable is not set. Defaulting to a blank string."
            agent-bot | OPENAI_API_KEY is not set, and BOT_PROVIDER=openai needs it.
            licence is 'invalid', not 'valid'. Check INTELLIGENCE_API_KEY.
        """.trimIndent()

        assertEquals(
            listOf("OPENAI_API_KEY", "INTELLIGENCE_API_KEY"),
            MacProjectConfigurationDiscovery.missingFromRuntime(log),
        )
    }

    @Test
    fun discoversOnlyDeclaredUnresolvedKeys() {
        val root = kotlin.io.path.createTempDirectory("siftalpha-config-discovery-").toFile()
        try {
            root.resolve(".env.example").writeText(
                "OPENAI_API_KEY=\nINTELLIGENCE_API_KEY=\nOPTIONAL_KEY=\n",
            )
            root.resolve(".env").writeText(
                "OPENAI_API_KEY=\nINTELLIGENCE_API_KEY=already-in-file\nOPTIONAL_KEY=\n",
            )

            val result = MacProjectConfigurationDiscovery.discover(
                projectRootPath = root.absolutePath,
                configuredKeys = emptySet(),
                runtimeLog = """
                    OPENAI_API_KEY is not set, and this service cannot start without it.
                    Check INTELLIGENCE_API_KEY.
                    UNDECLARED_KEY is not set.
                """.trimIndent(),
            )

            assertEquals(listOf("OPENAI_API_KEY"), result.map { it.name })
            assertTrue(result.single().sensitive)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun keychainConfigurationSatisfiesMissingRuntimeRequirement() {
        val root = kotlin.io.path.createTempDirectory("siftalpha-config-keychain-").toFile()
        try {
            root.resolve(".env.example").writeText("OPENAI_API_KEY=\n")
            root.resolve(".env").writeText("OPENAI_API_KEY=\n")

            val result = MacProjectConfigurationDiscovery.discover(
                projectRootPath = root.absolutePath,
                configuredKeys = setOf("OPENAI_API_KEY"),
                runtimeLog = "OPENAI_API_KEY is not set, and provider needs it.",
            )

            assertTrue(result.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sensitivityPolicyKeepsOrdinarySettingsVisible() {
        assertTrue(MacProjectConfigurationDiscovery.isSensitive("OPENAI_API_KEY"))
        assertTrue(MacProjectConfigurationDiscovery.isSensitive("WORKER_SHARED_SECRET"))
        assertFalse(MacProjectConfigurationDiscovery.isSensitive("BOT_PROVIDER"))
        assertFalse(MacProjectConfigurationDiscovery.isSensitive("APP_PORT"))
    }
}
