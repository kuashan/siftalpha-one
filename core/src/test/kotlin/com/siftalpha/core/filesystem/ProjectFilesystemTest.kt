package com.siftalpha.core.filesystem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFilesystemTest {
    @Test
    fun projectRelativePathsAreStableAcrossPlatforms() {
        assertEquals("src/main.py", ProjectFilesystemPolicy.childPath("src", "main.py"))
        assertEquals("main.py", ProjectFilesystemPolicy.childPath("", "main.py"))
        assertEquals("src", ProjectFilesystemPolicy.parentPath("src/main.py"))
        assertEquals("", ProjectFilesystemPolicy.parentPath("main.py"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun childNameCannotEscapeProjectRoot() {
        ProjectFilesystemPolicy.childPath("src", "../secret.txt")
    }

    @Test
    fun genericFilesystemSupportsProjectScopedCrudWithoutPlatformTypes() {
        val fs = MemoryProjectFilesystem()
        val rootId = "project-a"

        val src = fs.createDirectory(
            projectId = rootId,
            parentId = rootId,
            parentRelativePath = "",
            depth = 0,
            name = "src",
        )
        val file = fs.createFile(
            projectId = rootId,
            parentId = src.id,
            parentRelativePath = src.relativePath,
            depth = 1,
            name = "main.py",
            mediaType = "text/plain",
            initialContent = "print('ok')".toByteArray(),
        )

        assertTrue(src.isDirectory)
        assertFalse(file.isDirectory)
        assertEquals("src/main.py", file.relativePath)
        assertArrayEquals("print('ok')".toByteArray(), fs.readBytes(file, 1024))

        fs.writeBytes(file, "print('new')".toByteArray())
        assertArrayEquals("print('new')".toByteArray(), fs.readBytes(file, 1024))

        val renamed = fs.rename(file, "app.py")
        assertEquals("src/app.py", renamed.relativePath)

        fs.delete(renamed)
        assertTrue(fs.listChildren(rootId, src.id, src.relativePath, 1).isEmpty())
    }

    private class MemoryProjectFilesystem : ProjectFilesystem {
        private data class Node(
            var entry: ProjectFileEntry,
            var content: ByteArray = ByteArray(0),
            val parentId: String,
        )

        private val nodes = linkedMapOf<String, Node>()
        private var nextId = 1

        override fun listChildren(
            projectId: String,
            parentId: String,
            parentRelativePath: String,
            depth: Int,
        ): List<ProjectFileEntry> =
            nodes.values.filter { it.parentId == parentId }.map { it.entry }

        override fun readBytes(file: ProjectFileEntry, maxBytes: Int): ByteArray {
            require(maxBytes > 0)
            return nodes.getValue(file.id).content.copyOf(minOf(nodes.getValue(file.id).content.size, maxBytes))
        }

        override fun writeBytes(file: ProjectFileEntry, content: ByteArray) {
            nodes.getValue(file.id).content = content.copyOf()
        }

        override fun createFile(
            projectId: String,
            parentId: String,
            parentRelativePath: String,
            depth: Int,
            name: String,
            mediaType: String?,
            initialContent: ByteArray,
        ): ProjectFileEntry =
            create(parentId, parentRelativePath, depth, name, mediaType, false, initialContent)

        override fun createDirectory(
            projectId: String,
            parentId: String,
            parentRelativePath: String,
            depth: Int,
            name: String,
        ): ProjectFileEntry =
            create(parentId, parentRelativePath, depth, name, null, true, ByteArray(0))

        override fun rename(file: ProjectFileEntry, newName: String): ProjectFileEntry {
            val node = nodes.getValue(file.id)
            val renamed = file.copy(
                name = newName,
                relativePath = ProjectFilesystemPolicy.childPath(
                    ProjectFilesystemPolicy.parentPath(file.relativePath),
                    newName,
                ),
            )
            node.entry = renamed
            return renamed
        }

        override fun delete(file: ProjectFileEntry) {
            nodes.remove(file.id)
        }

        private fun create(
            parentId: String,
            parentRelativePath: String,
            depth: Int,
            name: String,
            mediaType: String?,
            directory: Boolean,
            content: ByteArray,
        ): ProjectFileEntry {
            val id = "node-${nextId++}"
            val entry = ProjectFileEntry(
                id = id,
                name = name,
                relativePath = ProjectFilesystemPolicy.childPath(parentRelativePath, name),
                mediaType = mediaType,
                depth = depth,
                isDirectory = directory,
            )
            nodes[id] = Node(entry, content.copyOf(), parentId)
            return entry
        }
    }
}
