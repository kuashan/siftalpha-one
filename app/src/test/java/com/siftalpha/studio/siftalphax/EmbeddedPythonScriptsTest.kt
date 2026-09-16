package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonScriptsTest {
    @Test
    fun declaresFileBackedFixturesForTheExperimentalScenarios() {
        assertEquals(
            listOf("NORMAL", "STDOUT_STDERR_FAILURE", "LONG_RUNNING"),
            EmbeddedPythonScenario.entries.map { it.name },
        )
        assertEquals(
            listOf("PROJECT_A", "PROJECT_B", "PROJECT_C"),
            EmbeddedPythonScenario.entries.map { it.fixture.name },
        )
        assertEquals("main.py", EmbeddedPythonProjectFixture.PROJECT_A.entrypoint)
        assertEquals(".", EmbeddedPythonProjectFixture.PROJECT_A.workingDirectory)
        assertTrue(
            EmbeddedPythonProjectFixture.entries.all {
                it.assetPath.startsWith("siftalphax/project-fixtures/")
            },
        )
        assertTrue(EmbeddedPythonProjectFixture.PROJECT_A.projectIdentity.contains("project-a"))
        assertTrue(EmbeddedPythonProjectFixture.PROJECT_B.projectIdentity.contains("project-b"))
        assertTrue(EmbeddedPythonProjectFixture.PROJECT_C.projectIdentity.contains("project-c"))
    }

    @Test
    fun fixtureMetadataDoesNotExposeExternalRuntimeCommands() {
        EmbeddedPythonProjectFixture.entries.forEach { fixture ->
            val metadata = (fixture.assetPath + fixture.projectIdentity).lowercase()
            assertFalse(metadata.contains("termux"))
            assertFalse(metadata.contains("run_command"))
            assertFalse(metadata.contains("proot"))
            assertFalse(metadata.contains("apt"))
        }
    }
}
