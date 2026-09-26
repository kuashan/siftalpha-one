package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessScope
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacHybridProjectLifecycleTest {
    @Test
    fun createsDotEnvFromProjectTemplateWithoutOverwritingExistingConfig() {
        val root = Files.createTempDirectory("siftalpha-env-template-").toFile()
        try {
            root.resolve(".env.example").writeText("TOKEN=\nPORT=3000\n")

            val first = MacProjectEnvironmentTemplatePolicy.ensure(root.absolutePath)
            assertTrue(first.success)
            assertEquals(
                MacProjectEnvProvisionStatus.CREATED_FROM_TEMPLATE,
                first.status,
            )
            assertEquals("TOKEN=\nPORT=3000\n", root.resolve(".env").readText())

            root.resolve(".env").writeText("TOKEN=user-value\n")
            val second = MacProjectEnvironmentTemplatePolicy.ensure(root.absolutePath)
            assertTrue(second.success)
            assertEquals(MacProjectEnvProvisionStatus.EXISTING, second.status)
            assertEquals("TOKEN=user-value\n", root.resolve(".env").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedDotEnvTemplate() {
        val root = Files.createTempDirectory("siftalpha-env-template-link-").toFile()
        val outside = Files.createTempFile("siftalpha-env-outside-", ".txt").toFile()
        try {
            outside.writeText("TOKEN=outside\n")
            Files.createSymbolicLink(
                root.resolve(".env.example").toPath(),
                outside.toPath(),
            )
            val result = MacProjectEnvironmentTemplatePolicy.ensure(root.absolutePath)
            assertFalse(result.success)
            assertFalse(root.resolve(".env").exists())
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun launcherExitCodeControlsHybridStartSuccess() {
        val root = Files.createTempDirectory("siftalpha-launcher-result-").toFile()
        try {
            val processControl = MacProjectProcessControl()
            val runner = MacHybridLauncherRunner(processControl)
            val scope = ProjectProcessScope("macos:hybrid-exit")

            val failed = runner.run(
                ProjectProcessLaunchRequest(
                    scope = scope,
                    executable = "/bin/bash",
                    arguments = listOf("-c", "echo launcher-failed >&2; exit 7"),
                    workingDirectory = root.absolutePath,
                    environment = emptyMap(),
                ),
                cancelled = { false },
            )
            assertFalse(failed.success)
            assertEquals(7, failed.exitCode)
            assertTrue(failed.detail.orEmpty().contains("launcher-failed"))

            processControl.forget(scope)

            val success = runner.run(
                ProjectProcessLaunchRequest(
                    scope = scope,
                    executable = "/bin/bash",
                    arguments = listOf("-c", "echo ready; exit 0"),
                    workingDirectory = root.absolutePath,
                    environment = emptyMap(),
                ),
                cancelled = { false },
            )
            assertTrue(success.success)
            assertEquals(0, success.exitCode)
        } finally {
            root.deleteRecursively()
        }
    }
}
