package com.siftalpha.studio.siftalphax

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonWheelInstallerV1Test {
    @Test
    fun installsVerifiedCachedWheelTransactionally() {
        val root = Files.createTempDirectory("siftalpha-wheel-install").toFile()
        try {
            val cache = File(root, "cache").apply { mkdirs() }
            val environment = File(root, "environment").apply { mkdirs() }
            val sourceWheel = File(root, "demo.whl")
            ZipOutputStream(sourceWheel.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("demo/__init__.py"))
                zip.write("VALUE = 7\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("demo-1.0.dist-info/METADATA"))
                zip.write("Name: demo\nVersion: 1.0\n".toByteArray())
                zip.closeEntry()
            }
            val sha = sha256(sourceWheel)
            sourceWheel.copyTo(File(cache, "$sha.whl"))

            val wheel = EmbeddedPythonIndexWheelV1(
                filename = "demo-1.0-py3-none-any.whl",
                url = "https://files.pythonhosted.org/packages/demo.whl",
                sha256 = sha,
                size = sourceWheel.length(),
                requiresPython = ">=3.10",
                yanked = false,
            )
            val selected = EmbeddedPythonSelectedWheelV1(
                wheel = wheel,
                tags = requireNotNull(
                    EmbeddedPythonWheelSelectionV1.parseWheelFilename(wheel.filename),
                ),
                nativeAndroid = false,
            )
            val plan = EmbeddedPythonDependencyPlanV1(
                sourceFingerprint = "sha256:" + "1".repeat(64),
                resolvedFingerprint = "sha256:" + "2".repeat(64),
                packages = listOf(
                    EmbeddedPythonResolvedPackageV1(
                        normalizedName = "demo",
                        version = "1.0",
                        wheel = selected,
                        requiresPython = ">=3.10",
                    ),
                ),
            )

            val installed = EmbeddedPythonWheelInstallerV1(cache).install(
                projectIdentity = "project-a",
                environmentRoot = environment,
                plan = plan,
            )

            assertTrue(File(installed.sitePackages, "demo/__init__.py").isFile)
            assertEquals(
                "VALUE = 7",
                File(installed.sitePackages, "demo/__init__.py").readText().trim(),
            )
            assertTrue(File(environment, EmbeddedPythonWheelInstallerV1.READY_MARKER).isFile)
            assertTrue(
                EmbeddedPythonWheelInstallerV1(cache).readReadyBinding(
                    projectIdentity = "project-a",
                    environmentRoot = environment,
                    sourceFingerprint = plan.sourceFingerprint,
                    runtimeIdentity = EmbeddedPythonRuntimeIdentityV1.ID,
                ) != null,
            )
            assertNull(
                EmbeddedPythonWheelInstallerV1(cache).readReadyBinding(
                    projectIdentity = "project-a",
                    environmentRoot = environment,
                    sourceFingerprint = plan.sourceFingerprint,
                    runtimeIdentity = "cpython-9.9.9-android-arm64-v8a",
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migratesCompatibleLegacyV2EnvironmentWithoutReinstall() {
        val root = Files.createTempDirectory("siftalpha-wheel-migrate").toFile()
        try {
            val cache = File(root, "cache").apply { mkdirs() }
            val environment = File(root, "environment").apply { mkdirs() }
            File(environment, EmbeddedPythonWheelInstallerV1.SITE_PACKAGES).mkdirs()
            File(environment, EmbeddedPythonWheelInstallerV1.READY_MARKER)
                .writeText(EmbeddedPythonWheelInstallerV1.LEGACY_SCHEMA + "\n")
            val sourceFingerprint = "sha256:" + "1".repeat(64)
            val environmentKey = "sha256:" + "3".repeat(64)
            File(environment, EmbeddedPythonWheelInstallerV1.LEGACY_MANIFEST_FILE).writeText(
                """{"schema":"${EmbeddedPythonWheelInstallerV1.LEGACY_SCHEMA}","projectIdentity":"project-a","sourceFingerprint":"$sourceFingerprint","resolvedFingerprint":"sha256:${"2".repeat(64)}","environmentKey":"$environmentKey","packages":[]}""" + "\n",
            )

            val migrated = EmbeddedPythonWheelInstallerV1(cache).readReadyBinding(
                projectIdentity = "project-a",
                environmentRoot = environment,
                sourceFingerprint = sourceFingerprint,
                runtimeIdentity = EmbeddedPythonRuntimeIdentityV1.ID,
            )

            assertTrue(migrated != null)
            assertEquals(environmentKey, migrated?.environmentKey)
            val currentManifest = File(environment, EmbeddedPythonWheelInstallerV1.MANIFEST_FILE)
            assertTrue(currentManifest.isFile)
            assertTrue(currentManifest.readText().contains(EmbeddedPythonRuntimeIdentityV1.ID))
            assertEquals(
                EmbeddedPythonWheelInstallerV1.SCHEMA,
                File(environment, EmbeddedPythonWheelInstallerV1.READY_MARKER).readText().trim(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migratesCompatibleLegacyV2EnvironmentWithoutReinstall() {
        val root = Files.createTempDirectory("siftalpha-wheel-migrate").toFile()
        try {
            val cache = File(root, "cache").apply { mkdirs() }
            val environment = File(root, "environment").apply { mkdirs() }
            File(environment, EmbeddedPythonWheelInstallerV1.SITE_PACKAGES).mkdirs()
            File(environment, EmbeddedPythonWheelInstallerV1.READY_MARKER)
                .writeText(EmbeddedPythonWheelInstallerV1.LEGACY_SCHEMA + "\n")
            val sourceFingerprint = "sha256:" + "1".repeat(64)
            val environmentKey = "sha256:" + "3".repeat(64)
            File(environment, EmbeddedPythonWheelInstallerV1.LEGACY_MANIFEST_FILE).writeText(
                """{"schema":"${EmbeddedPythonWheelInstallerV1.LEGACY_SCHEMA}","projectIdentity":"project-a","sourceFingerprint":"$sourceFingerprint","resolvedFingerprint":"sha256:${"2".repeat(64)}","environmentKey":"$environmentKey","packages":[]}""" + "\n",
            )

            val migrated = EmbeddedPythonWheelInstallerV1(cache).readReadyBinding(
                projectIdentity = "project-a",
                environmentRoot = environment,
                sourceFingerprint = sourceFingerprint,
                runtimeIdentity = EmbeddedPythonRuntimeIdentityV1.ID,
            )

            assertTrue(migrated != null)
            assertEquals(environmentKey, migrated?.environmentKey)
            val currentManifest = File(environment, EmbeddedPythonWheelInstallerV1.MANIFEST_FILE)
            assertTrue(currentManifest.isFile)
            assertTrue(currentManifest.readText().contains(EmbeddedPythonRuntimeIdentityV1.ID))
            assertEquals(
                EmbeddedPythonWheelInstallerV1.SCHEMA,
                File(environment, EmbeddedPythonWheelInstallerV1.READY_MARKER).readText().trim(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
