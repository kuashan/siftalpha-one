package com.siftalpha.studio.siftalphax

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalAlpineRuntimeTest {
    @Test
    fun requirementsTakePrecedenceAndFingerprintIsStable() {
        val first = InternalAlpineDependencySource.fromProjectFiles(
            requirementsText = "oci>=2.161,<3\r\n",
            pyprojectText = "[project]\ndependencies=[]\n",
        )
        val second = InternalAlpineDependencySource.fromProjectFiles(
            requirementsText = "oci>=2.161,<3\n",
            pyprojectText = "[project]\ndependencies=[\"ignored\"]\n",
        )

        assertEquals(InternalAlpineDependencySource.Kind.REQUIREMENTS_TXT, first.kind)
        assertEquals(first.sourceFingerprint, second.sourceFingerprint)
    }

    @Test
    fun pythonRuntimeBootstrapRetriesBoundedApkFailures() {
        val command = InternalAlpinePythonRuntimeBootstrap.installCommand(maxAttempts = 4)

        assertTrue(command.contains("apk add --no-cache ca-certificates python3 py3-pip py3-virtualenv"))
        assertTrue(command.contains("SIFTALPHA_INTERNAL_ALPINE_APK_RETRY"))
        assertTrue(command.contains("SIFTALPHA_INTERNAL_ALPINE_APK_FAILED"))
        assertTrue(command.contains("if [ \"\$apk_attempt\" -ge \"4\" ]"))
        assertTrue(command.contains("sleep_seconds=\$((apk_attempt * 2))"))
        assertTrue(command.indexOf("apk add --no-cache") < command.indexOf("python3 --version"))
    }

    @Test
    fun tarExtractorPreservesFilesAndGuestAbsoluteSymlinks() {
        val root = Files.createTempDirectory("siftalpha-alpine-tar").toFile()
        try {
            val tar = tarOf(
                entry("bin/", type = '5'),
                entry("bin/tool", type = '0', mode = 0b111_101_101, payload = "ok".toByteArray()),
                entry("usr/", type = '5'),
                entry("usr/bin/", type = '5'),
                entry("usr/bin/tool", type = '2', linkName = "/bin/tool"),
            )
            InternalAlpineFiles.extractTar(ByteArrayInputStream(tar), root)

            assertEquals("ok", File(root, "bin/tool").readText())
            assertTrue(File(root, "bin/tool").canExecute())
            assertTrue(Files.isSymbolicLink(File(root, "usr/bin/tool").toPath()))
            assertEquals("/bin/tool", Files.readSymbolicLink(File(root, "usr/bin/tool").toPath()).toString())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun processTreeDiscoveryIsProjectRootScopedAndLeafFirst() {
        val proc = Files.createTempDirectory("siftalpha-proc-tree").toFile()
        try {
            fun children(pid: Int, value: String) {
                val file = File(proc, "$pid/task/$pid/children")
                file.parentFile.mkdirs()
                file.writeText(value)
                File(proc, pid.toString()).mkdirs()
            }
            children(100, "101 102\n")
            children(101, "103\n")
            children(102, "")
            children(103, "")
            children(999, "1000\n")
            children(1000, "")

            assertEquals(
                listOf(103, 101, 102),
                InternalAlpineProcessControl.descendantPids(proc, 100L),
            )
            assertFalse(999 in InternalAlpineProcessControl.descendantPids(proc, 100L))
            assertFalse(1000 in InternalAlpineProcessControl.descendantPids(proc, 100L))
        } finally {
            proc.deleteRecursively()
        }
    }

    @Test
    fun tarExtractorCopiesHardLinksAndSkipsMetadataRecords() {
        val root = Files.createTempDirectory("siftalpha-alpine-hardlink").toFile()
        try {
            val tar = tarOf(
                entry("bin/", type = '5'),
                entry("bin/base", type = '0', mode = 0b111_101_101, payload = "base".toByteArray()),
                entry("metadata", type = 'x', payload = "25 comment=safe-metadata\n".toByteArray()),
                entry("bin/copy", type = '1', mode = 0b111_101_101, linkName = "bin/base"),
                entry("after", payload = "ok".toByteArray()),
            )
            InternalAlpineFiles.extractTar(ByteArrayInputStream(tar), root)

            assertEquals("base", File(root, "bin/copy").readText())
            assertTrue(File(root, "bin/copy").canExecute())
            assertFalse(Files.isSymbolicLink(File(root, "bin/copy").toPath()))
            assertEquals("ok", File(root, "after").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tarExtractorRejectsHardLinkTraversal() {
        val root = Files.createTempDirectory("siftalpha-alpine-hardlink-traversal").toFile()
        try {
            assertFails {
                InternalAlpineFiles.extractTar(
                    ByteArrayInputStream(
                        tarOf(
                            entry("bin/", type = '5'),
                            entry("bin/bad", type = '1', linkName = "../outside"),
                        ),
                    ),
                    root,
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tarExtractorRejectsTraversalAndSymlinkParents() {
        val traversalRoot = Files.createTempDirectory("siftalpha-alpine-traversal").toFile()
        try {
            assertFails {
                InternalAlpineFiles.extractTar(
                    ByteArrayInputStream(tarOf(entry("../escape", payload = byteArrayOf(1)))),
                    traversalRoot,
                )
            }
            assertFalse(File(traversalRoot.parentFile, "escape").exists())
        } finally {
            traversalRoot.deleteRecursively()
        }

        val symlinkRoot = Files.createTempDirectory("siftalpha-alpine-symlink").toFile()
        try {
            assertFails {
                InternalAlpineFiles.extractTar(
                    ByteArrayInputStream(
                        tarOf(
                            entry("link", type = '2', linkName = "/tmp"),
                            entry("link/escape", payload = byteArrayOf(1)),
                        ),
                    ),
                    symlinkRoot,
                )
            }
        } finally {
            symlinkRoot.deleteRecursively()
        }
    }

    private data class TarEntry(
        val name: String,
        val type: Char,
        val mode: Int,
        val payload: ByteArray,
        val linkName: String,
    )

    private fun entry(
        name: String,
        type: Char = '0',
        mode: Int = 0b110_100_100,
        payload: ByteArray = ByteArray(0),
        linkName: String = "",
    ) = TarEntry(name, type, mode, payload, linkName)

    private fun tarOf(vararg entries: TarEntry): ByteArray {
        val output = ByteArrayOutputStream()
        entries.forEach { item ->
            val header = ByteArray(512)
            writeAscii(header, 0, 100, item.name)
            writeOctal(header, 100, 8, item.mode.toLong())
            writeOctal(header, 124, 12, item.payload.size.toLong())
            header[156] = item.type.code.toByte()
            writeAscii(header, 157, 100, item.linkName)
            output.write(header)
            output.write(item.payload)
            val padding = (512 - item.payload.size % 512) % 512
            output.write(ByteArray(padding))
        }
        output.write(ByteArray(1024))
        return output.toByteArray()
    }

    private fun writeAscii(target: ByteArray, offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size < length)
        bytes.copyInto(target, offset)
    }

    private fun writeOctal(target: ByteArray, offset: Int, length: Int, value: Long) {
        val text = value.toString(8).padStart(length - 1, '0')
        writeAscii(target, offset, length, text)
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }
    }
}
