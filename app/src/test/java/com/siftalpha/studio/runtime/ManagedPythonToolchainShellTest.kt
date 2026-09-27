package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedPythonToolchainShellTest {
    @Test
    fun `default external python is pinned to 3_12`() {
        assertEquals("3.12.14", ManagedPythonToolchainShell.DEFAULT_PYTHON)
        assertEquals(
            listOf("3.12.14", "3.11.16", "3.13.15", "3.14.7"),
            ManagedPythonToolchainShell.supportedVersions,
        )
    }

    @Test
    fun `managed python toolchain is pinned and discovered from owned directory`() {
        val shell = ManagedPythonToolchainShell.toolchainShell()
        assertTrue(shell.contains("uv-0.12.18"))
        assertTrue(shell.contains(ManagedPythonToolchainShell.UV_AARCH64_LINUX_GNU_SHA256))
        assertTrue(shell.contains("python install"))
        assertTrue(shell.contains("UV_PYTHON_PREFERENCE='only-managed'"))
        assertFalse(shell.contains("python find"))
        assertTrue(shell.contains("cpython-"))
        assertTrue(shell.contains("requested_version"))
        assertTrue(shell.contains("SIFTALPHA_MANAGED_PYTHON_DIR"))
        assertTrue(shell.contains("\\( -type f -o -type l \\)"))
        assertFalse(shell.contains("\\\\( -type f"))
        assertTrue(shell.contains("SIFTALPHA_ERROR=MANAGED_PYTHON_INSTALL_FAILED"))
    }

    @Test
    fun `resolver prefers compatibility baseline instead of newest python`() {
        val shell = ManagedPythonToolchainShell.resolverShell()
        val py312 = shell.indexOf("\"3.12.14\"")
        val py311 = shell.indexOf("\"3.11.16\"")
        val py313 = shell.indexOf("\"3.13.15\"")
        val py314 = shell.indexOf("\"3.14.7\"")
        assertTrue(py312 >= 0)
        assertTrue(py311 > py312)
        assertTrue(py313 > py311)
        assertTrue(py314 > py313)
    }
}
