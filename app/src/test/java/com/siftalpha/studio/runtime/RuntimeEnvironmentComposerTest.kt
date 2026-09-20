package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEnvironmentComposerTest {

    private class FakeHost : RuntimeCommandHost {
        override fun runtimeSupported(): Boolean = true
        override fun runtimeUnsupportedReason(): String = "unsupported"
        override fun sharedRoot(): String = "/storage/emulated/0/AcodeProjects"
        override fun runtimeId(folderName: String): String = "runtime-id"
        override fun sh(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
        override fun wrapUbuntu(inner: String): String = inner
        override fun hostPreamble(): String = ""
        override fun hostProcessHelpers(): String = ""
    }

    private val primary = RuntimeCommand(
        shellScript = "echo SIFTALPHA_ENV=READY; echo PRIMARY_OK",
        label = "primary",
    )
    private val node = RuntimeCommand(
        shellScript = "echo SIFTALPHA_NODE_ENV=READY; echo NODE_OK",
        label = "node",
    )

    @Test
    fun `prepare hides child ready marker and emits one project ready marker`() {
        val shell = RuntimeEnvironmentComposer.prepare(FakeHost(), "Project", primary, node).shellScript

        assertTrue(shell.contains("sed"))
        assertTrue(shell.contains("SIFTALPHA_NODE_ENV=(READY|NOT_REQUIRED)"))
        assertTrue(shell.contains("echo 'SIFTALPHA_ENV=READY'"))
        assertTrue(shell.contains("echo 'SIFTALPHA_ENV=NOT_READY'"))
    }

    @Test
    fun `prepare builds supplemental frontend before python packaging`() {
        val shell = RuntimeEnvironmentComposer.prepare(FakeHost(), "Project", primary, node).shellScript

        val nodeIndex = shell.indexOf("node_output=")
        val primaryIndex = shell.indexOf("primary_output=")
        assertTrue(nodeIndex >= 0)
        assertTrue(primaryIndex > nodeIndex)
    }

    @Test
    fun `prepare strips child project environment markers from both steps`() {
        val shell = RuntimeEnvironmentComposer.prepare(FakeHost(), "Project", primary, node).shellScript

        val markerFilterCount = "SIFTALPHA_ENV=READY".toRegex().findAll(shell).count()
        assertTrue(shell.contains("SIFTALPHA_NODE_ENV=(READY|NOT_REQUIRED)"))
        assertTrue(markerFilterCount >= 3)
        assertTrue(shell.trimEnd().contains("echo 'SIFTALPHA_ENV=READY'"))
    }

    @Test
    fun `start blocks stale supplemental environment before primary process`() {
        val secretProvider = { "secret-payload" }
        val primaryStart = RuntimeCommand(
            shellScript = "echo PRIMARY_START",
            label = "start",
            description = "start description",
            background = false,
            secretNamespace = "project-secret",
            stdinPayloadProvider = secretProvider,
        )
        val command = RuntimeEnvironmentComposer.start(FakeHost(), "Project", primaryStart, node)
        val shell = command.shellScript

        assertTrue(shell.contains("SIFTALPHA_NODE_ENV=(READY|NOT_REQUIRED)"))
        assertTrue(shell.contains("SIFTALPHA_ERROR=NODE_ENV_NOT_READY"))
        assertTrue(shell.contains("exit 73"))
        assertTrue(shell.contains("exec bash -lc"))
        assertEquals(primaryStart.label, command.label)
        assertEquals(primaryStart.description, command.description)
        assertEquals(primaryStart.background, command.background)
        assertEquals(primaryStart.secretNamespace, command.secretNamespace)
        assertSame(secretProvider, command.stdinPayloadProvider)
    }

    @Test
    fun `status requires both python and node readiness`() {
        val shell = RuntimeEnvironmentComposer.status(FakeHost(), "Project", primary, node).shellScript

        assertTrue(shell.contains("primary_ready=0"))
        assertTrue(shell.contains("node_ready=0"))
        assertTrue(shell.contains("SIFTALPHA_ENV_REASON=NODE_%s"))
        assertTrue(shell.contains("SIFTALPHA_ENV=READY"))
        assertTrue(shell.contains("SIFTALPHA_ENV=NOT_READY"))
    }

    @Test
    fun `clean emits project cleaned only after both child clean commands`() {
        val shell = RuntimeEnvironmentComposer.clean(FakeHost(), "Project", primary, node).shellScript

        assertTrue(shell.contains("primary_code"))
        assertTrue(shell.contains("node_code"))
        assertTrue(shell.contains("echo 'SIFTALPHA_ENV=CLEANED'"))
    }
}
