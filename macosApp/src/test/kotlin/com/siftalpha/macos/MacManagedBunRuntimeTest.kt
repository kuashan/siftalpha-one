package com.siftalpha.macos

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacManagedBunRuntimeTest {
    @Test
    fun extractsExactBunVersionFromPackageManager() {
        assertEquals(
            "1.3.14",
            MacManagedBunArtifactPolicy.packageManagerVersion(
                """{"name":"demo","packageManager":"bun@1.3.14"}""",
            ),
        )
        assertNull(
            MacManagedBunArtifactPolicy.packageManagerVersion(
                """{"name":"demo","packageManager":"bun@latest"}""",
            ),
        )
    }

    @Test
    fun selectsOfficialDarwinArtifactsByArchitecture() {
        val intel = MacManagedBunArtifactPolicy.artifact("1.3.14", "x86_64")
        val arm = MacManagedBunArtifactPolicy.artifact("1.3.14", "aarch64")
        assertEquals("bun-darwin-x64.zip", intel?.assetName)
        assertEquals("bun-darwin-aarch64.zip", arm?.assetName)
        assertTrue(intel?.assetUrl?.contains("/bun-v1.3.14/") == true)
        assertNull(MacManagedBunArtifactPolicy.artifact("../escape", "x86_64"))
    }

    @Test
    fun parsesOnlyExactAssetChecksum() {
        val text = """
            aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  bun-darwin-aarch64.zip
            bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  bun-darwin-x64.zip
        """.trimIndent()
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            MacManagedBunArtifactPolicy.expectedSha256(text, "bun-darwin-x64.zip"),
        )
        assertNull(
            MacManagedBunArtifactPolicy.expectedSha256(text, "bun-linux-x64.zip"),
        )
    }

    @Test
    fun locatesCachedManagedBunOnlyWhenVersionMatches() {
        val root = Files.createTempDirectory("siftalpha-bun-cache-").toFile()
        try {
            val runtimeRoot = root.resolve("1.3.14")
            val bun = runtimeRoot.resolve("bin/bun")
            bun.parentFile.mkdirs()
            bun.writeText("#!/bin/sh\necho 1.3.14\n")
            assertTrue(bun.setExecutable(true, false))

            val provider = MacManagedBunRuntimeProvider(root = root, architecture = "x86_64")
            assertNotNull(provider.locate("1.3.14"))
            assertNull(provider.locate("1.3.13"))
        } finally {
            root.deleteRecursively()
        }
    }
}
