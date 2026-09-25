package com.siftalpha.macos

import com.siftalpha.core.filesystem.ProjectFileEntry
import com.siftalpha.core.filesystem.ProjectFilesystem
import com.siftalpha.core.filesystem.ProjectFilesystemPolicy
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class MacImportedProject(
    val projectId: String,
    val root: ProjectFileEntry,
    val canonicalRootPath: String,
)

class MacProjectFilesystem : ProjectFilesystem {
    private val roots = ConcurrentHashMap<String, File>()

    fun importDirectory(directory: File): MacImportedProject {
        val root = directory.canonicalFile
        require(root.isDirectory) { "project root must be an existing directory" }
        require(root.canRead()) { "project root must be readable" }
        val projectId = "macos:" + sha256(root.absolutePath)
        roots[projectId] = root
        return MacImportedProject(
            projectId = projectId,
            root = ProjectFileEntry(
                id = root.absolutePath,
                name = root.name.ifBlank { root.absolutePath },
                relativePath = ".",
                depth = 0,
                isDirectory = true,
            ),
            canonicalRootPath = root.absolutePath,
        )
    }

    override fun listChildren(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
    ): List<ProjectFileEntry> {
        val root = root(projectId)
        val parent = checked(root, File(parentId), mustExist = true)
        require(parent.isDirectory) { "parent must be a directory" }
        return parent.listFiles()
            .orEmpty()
            .asSequence()
            .mapNotNull { child ->
                runCatching {
                    val canonical = checked(root, child, mustExist = true)
                    ProjectFileEntry(
                        id = canonical.absolutePath,
                        name = canonical.name,
                        relativePath = relative(root, canonical),
                        depth = depth,
                        isDirectory = canonical.isDirectory,
                    )
                }.getOrNull()
            }
            .sortedWith(compareBy<ProjectFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    override fun readBytes(file: ProjectFileEntry, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be > 0" }
        val root = rootFor(file)
        val target = checked(root, File(file.id), mustExist = true)
        require(target.isFile) { "file must be a regular file" }
        require(target.length() <= maxBytes) { "file exceeds read limit: " + file.relativePath }
        return target.readBytes()
    }

    override fun writeBytes(file: ProjectFileEntry, content: ByteArray) {
        val root = rootFor(file)
        val target = checked(root, File(file.id), mustExist = true)
        require(target.isFile) { "file must be a regular file" }
        target.writeBytes(content)
    }

    override fun createFile(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
        name: String,
        mediaType: String?,
        initialContent: ByteArray,
    ): ProjectFileEntry {
        val root = root(projectId)
        val parent = checked(root, File(parentId), mustExist = true)
        require(parent.isDirectory) { "parent must be a directory" }
        val relativePath = ProjectFilesystemPolicy.childPath(parentRelativePath, name)
        val target = checked(root, File(parent, name), mustExist = false)
        require(!target.exists()) { "file already exists" }
        target.writeBytes(initialContent)
        return ProjectFileEntry(
            id = target.canonicalPath,
            name = name,
            relativePath = relativePath,
            mediaType = mediaType,
            depth = depth,
            isDirectory = false,
        )
    }

    override fun createDirectory(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
        name: String,
    ): ProjectFileEntry {
        val root = root(projectId)
        val parent = checked(root, File(parentId), mustExist = true)
        require(parent.isDirectory) { "parent must be a directory" }
        val relativePath = ProjectFilesystemPolicy.childPath(parentRelativePath, name)
        val target = checked(root, File(parent, name), mustExist = false)
        require(target.mkdir()) { "failed to create directory" }
        return ProjectFileEntry(
            id = target.canonicalPath,
            name = name,
            relativePath = relativePath,
            depth = depth,
            isDirectory = true,
        )
    }

    override fun rename(file: ProjectFileEntry, newName: String): ProjectFileEntry {
        val parentRelative = ProjectFilesystemPolicy.parentPath(file.relativePath)
        val newRelative = ProjectFilesystemPolicy.childPath(parentRelative, newName)
        val root = rootFor(file)
        val source = checked(root, File(file.id), mustExist = true)
        val target = checked(root, File(source.parentFile, newName), mustExist = false)
        require(!target.exists()) { "rename target already exists" }
        require(source.renameTo(target)) { "rename failed" }
        return file.copy(id = target.canonicalPath, name = newName, relativePath = newRelative)
    }

    override fun delete(file: ProjectFileEntry) {
        val root = rootFor(file)
        val target = checked(root, File(file.id), mustExist = true)
        require(target != root) { "project root cannot be deleted through ProjectFilesystem" }
        require(if (target.isDirectory) target.deleteRecursively() else target.delete()) {
            "delete failed"
        }
    }

    fun rootFile(projectId: String): File = root(projectId)

    fun forgetProject(projectId: String) {
        roots.remove(projectId)
    }

    private fun root(projectId: String): File =
        roots[projectId] ?: error("unknown imported project: " + projectId)

    private fun rootFor(file: ProjectFileEntry): File {
        val candidate = File(file.id).canonicalFile
        return roots.values.firstOrNull { contains(it, candidate) }
            ?: error("file does not belong to an imported project")
    }

    private fun checked(root: File, candidate: File, mustExist: Boolean): File {
        val canonical = candidate.canonicalFile
        require(contains(root, canonical)) { "path escapes imported project root" }
        if (mustExist) require(canonical.exists()) { "path does not exist" }
        return canonical
    }

    private fun contains(root: File, candidate: File): Boolean =
        candidate == root || candidate.toPath().startsWith(root.toPath())

    private fun relative(root: File, child: File): String =
        root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/')
            .ifBlank { "." }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
