package com.siftalpha.studio.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptibleProjectTreeDeleteTest {
    @Test
    fun deletesOnlyTheSelectedProjectEnvironmentAndPreservesSibling() {
        val parent = Files.createTempDirectory("siftalpha-project-environments").toFile()
        val selected = File(parent, "project-a").apply {
            mkdirs()
            File(this, "nested/file.txt").apply {
                parentFile!!.mkdirs()
                writeText("a")
            }
        }
        val sibling = File(parent, "project-b").apply {
            mkdirs()
            File(this, "keep.txt").writeText("b")
        }
        try {
            InterruptibleProjectTreeDelete.delete(selected, parent)
            assertFalse(selected.exists())
            assertTrue(File(sibling, "keep.txt").isFile)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun interruptionIsReportedBeforeDeletingTheRoot() {
        val parent = Files.createTempDirectory("siftalpha-project-cancel").toFile()
        val selected = File(parent, "project-a").apply {
            mkdirs()
            File(this, "file.txt").writeText("a")
        }
        try {
            var cancelled = true
            try {
                InterruptibleProjectTreeDelete.delete(selected, parent, shouldCancel = { cancelled })
            } catch (_: InterruptibleProjectTreeDelete.Cancelled) {
                cancelled = false
            }
            assertTrue(selected.exists())
            assertFalse(cancelled)
        } finally {
            parent.deleteRecursively()
        }
    }
}
