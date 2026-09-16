package com.siftalpha.studio.siftalphax

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonExecutionSpecTest {
    @Test
    fun validatesRootEntrypointWorkingDirectoryAndSessionIdentity() {
        val root = File(System.getProperty("java.io.tmpdir"), "siftalpha-spec-valid")
        root.mkdirs()
        File(root, "main.py").writeText("print('ok')")
        val spec = EmbeddedPythonExecutionSpec(
            projectIdentity = "fixture-project-a",
            executionRoot = root,
            entrypoint = "main.py",
            workingDirectory = ".",
            runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
            sessionId = "siftalpha-x-test",
            generation = 1L,
        )

        assertEquals(emptyList<String>(), spec.validationErrors())
        assertTrue(spec.entrypointExists)
        assertTrue(spec.entrypointFile.path.endsWith("main.py"))
        assertEquals(root.canonicalFile, spec.workingDirectoryFile.canonicalFile)
        root.deleteRecursively()
    }

    @Test
    fun reportsMissingEntrypointForNativeTerminalFailure() {
        val root = File(System.getProperty("java.io.tmpdir"), "siftalpha-spec-missing")
        root.mkdirs()
        val spec = EmbeddedPythonExecutionSpec(
            projectIdentity = "fixture-project-missing",
            executionRoot = root,
            entrypoint = "main.py",
            workingDirectory = ".",
            runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
            sessionId = "siftalpha-x-missing",
            generation = 2L,
        )

        assertFalse(spec.entrypointExists)
        assertTrue(spec.validationErrors().contains("ENTRYPOINT_MISSING"))
        assertEquals(emptyList<String>(), spec.nativeValidationErrors())
        root.deleteRecursively()
    }

    @Test
    fun rejectsTraversalAndAbsoluteTargetsAtConstruction() {
        val root = File(System.getProperty("java.io.tmpdir"), "siftalpha-spec-invalid")
        root.mkdirs()
        assertRejects {
            EmbeddedPythonExecutionSpec(
                "fixture-project-invalid",
                root,
                "../main.py",
                ".",
                EmbeddedPythonRuntimeKind.CPYTHON,
                "siftalpha-x-invalid",
                3L,
            )
        }
        assertRejects {
            EmbeddedPythonExecutionSpec(
                "fixture-project-invalid",
                root,
                "/etc/passwd",
                ".",
                EmbeddedPythonRuntimeKind.CPYTHON,
                "siftalpha-x-invalid",
                3L,
            )
        }
        assertRejects {
            EmbeddedPythonExecutionSpec(
                "fixture-project-invalid",
                root,
                "main.py",
                "../",
                EmbeddedPythonRuntimeKind.CPYTHON,
                "siftalpha-x-invalid",
                3L,
            )
        }
        root.deleteRecursively()
    }

    private fun assertRejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected invalid execution specification")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
