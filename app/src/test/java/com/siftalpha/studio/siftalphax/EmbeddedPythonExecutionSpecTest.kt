package com.siftalpha.studio.siftalphax

import java.io.File
import java.nio.file.Files
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
    fun validatesOptionalProjectEnvironmentBinding() {
        val base = Files.createTempDirectory("siftalpha-spec-environment").toFile()
        val root = File(base, "projects/session").apply { mkdirs() }
        val sitePackages = File(base, "environments/project/site-packages").apply { mkdirs() }
        File(root, "main.py").writeText("print('ok')")
        try {
            val spec = EmbeddedPythonExecutionSpec(
                projectIdentity = "fixture-project-environment",
                executionRoot = root,
                entrypoint = "main.py",
                workingDirectory = ".",
                runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
                sessionId = "siftalpha-x-environment",
                generation = 1L,
                environmentSitePackages = sitePackages,
                environmentKey = "sha256:" + "a".repeat(64),
            )
            assertEquals(emptyList<String>(), spec.validationErrors())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun acceptsBoundedPythonArguments() {
        val root = File(System.getProperty("java.io.tmpdir"), "siftalpha-spec-args")
        root.mkdirs()
        File(root, "main.py").writeText("print('ok')")
        try {
            val spec = EmbeddedPythonExecutionSpec(
                projectIdentity = "fixture-project-args",
                executionRoot = root,
                entrypoint = "main.py",
                workingDirectory = ".",
                runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
                sessionId = "siftalpha-x-args",
                generation = 4L,
                arguments = listOf("US", "AAPL", "--interval", "15"),
            )

            assertEquals(listOf("US", "AAPL", "--interval", "15"), spec.arguments)
            assertEquals(emptyList<String>(), spec.validationErrors())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsNulInsidePythonArgument() {
        val root = File(System.getProperty("java.io.tmpdir"), "siftalpha-spec-bad-args")
        root.mkdirs()
        File(root, "main.py").writeText("print('ok')")
        try {
            assertRejects {
                EmbeddedPythonExecutionSpec(
                    projectIdentity = "fixture-project-bad-args",
                    executionRoot = root,
                    entrypoint = "main.py",
                    workingDirectory = ".",
                    runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
                    sessionId = "siftalpha-x-bad-args",
                    generation = 5L,
                    arguments = listOf("bad\u0000value"),
                )
            }
        } finally {
            root.deleteRecursively()
        }
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


    @Test
    fun acceptsAppPrivateStylePathThroughSymlinkedAncestor() {
        val base = Files.createTempDirectory("siftalpha-app-private").toFile()
        val realUserRoot = File(base, "real/data/user/0")
        val aliasUserRoot = File(base, "alias/data/user/0")
        realUserRoot.mkdirs()
        aliasUserRoot.parentFile.mkdirs()
        Files.createSymbolicLink(aliasUserRoot.toPath(), realUserRoot.toPath())
        val root = File(aliasUserRoot, "com.siftalpha.studio/files/siftalphax/projects/session")
        root.mkdirs()
        File(root, "main.py").writeText("print('ok')")
        try {
            val spec = specFor(root, "main.py", ".")
            assertEquals(emptyList<String>(), spec.validationErrors())
        } finally {
            Files.deleteIfExists(aliasUserRoot.toPath())
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsEntrypointSymlinkThatEscapesExecutionRoot() {
        val base = Files.createTempDirectory("siftalpha-entrypoint-link").toFile()
        val root = File(base, "project").apply { mkdirs() }
        val outside = File(base, "outside.py").apply { writeText("print('must not run')") }
        val entrypoint = File(root, "main.py")
        Files.createSymbolicLink(entrypoint.toPath(), outside.toPath())
        try {
            val errors = specFor(root, "main.py", ".").validationErrors()
            assertTrue(errors.contains("ENTRYPOINT_OUTSIDE_ROOT"))
        } finally {
            Files.deleteIfExists(entrypoint.toPath())
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsWorkingDirectorySymlinkThatEscapesExecutionRoot() {
        val base = Files.createTempDirectory("siftalpha-working-link").toFile()
        val root = File(base, "project").apply { mkdirs() }
        val outside = File(base, "outside").apply { mkdirs() }
        val workingDirectory = File(root, "work")
        Files.createSymbolicLink(workingDirectory.toPath(), outside.toPath())
        File(root, "main.py").writeText("print('ok')")
        try {
            val errors = specFor(root, "main.py", "work").validationErrors()
            assertTrue(errors.contains("WORKING_DIRECTORY_OUTSIDE_ROOT"))
        } finally {
            Files.deleteIfExists(workingDirectory.toPath())
            base.deleteRecursively()
        }
    }

    private fun specFor(root: File, entrypoint: String, workingDirectory: String): EmbeddedPythonExecutionSpec =
        EmbeddedPythonExecutionSpec(
            projectIdentity = "fixture-project-path-test",
            executionRoot = root,
            entrypoint = entrypoint,
            workingDirectory = workingDirectory,
            runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
            sessionId = "siftalpha-x-path-test",
            generation = 1L,
        )

    private fun assertRejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected invalid execution specification")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
