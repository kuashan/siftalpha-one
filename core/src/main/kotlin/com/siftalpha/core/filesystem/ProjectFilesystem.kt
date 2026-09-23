package com.siftalpha.core.filesystem

/**
 * Platform-neutral reference to one file-system entry inside a SiftAlpha project
 * （SiftAlpha 项目中的平台无关文件条目引用）.
 *
 * [id] is intentionally opaque. Android（安卓） may use a SAF document ID, while macOS（苹果）
 * and Windows（微软） may use a canonical path or another platform-owned identity.
 */
data class ProjectFileEntry(
    val id: String,
    val name: String,
    val relativePath: String,
    val mediaType: String? = null,
    val depth: Int,
    val isDirectory: Boolean,
) {
    init {
        require(id.isNotBlank()) { "file id must not be blank" }
        require(name.isNotBlank()) { "file name must not be blank" }
        require(depth >= 0) { "file depth must not be negative" }
        require(relativePath.isNotBlank()) { "relative path must not be blank" }
        require(!relativePath.startsWith("/")) { "relative path must remain project-relative" }
        require(".." !in relativePath.split('/')) { "relative path must not escape project root" }
    }
}

/**
 * Cross-platform project filesystem port（跨平台项目文件系统端口）.
 *
 * Core（核心） describes the operations SiftAlpha needs. Platform adapters decide how those
 * operations map to Android SAF（安卓存储访问框架）, macOS POSIX / Foundation（苹果文件接口）
 * or Windows filesystem APIs（Windows 文件系统接口）.
 */
interface ProjectFilesystem {
    fun listChildren(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
    ): List<ProjectFileEntry>

    fun readBytes(
        file: ProjectFileEntry,
        maxBytes: Int,
    ): ByteArray

    fun writeBytes(
        file: ProjectFileEntry,
        content: ByteArray,
    )

    fun createFile(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
        name: String,
        mediaType: String? = null,
        initialContent: ByteArray = ByteArray(0),
    ): ProjectFileEntry

    fun createDirectory(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
        name: String,
    ): ProjectFileEntry

    fun rename(
        file: ProjectFileEntry,
        newName: String,
    ): ProjectFileEntry

    fun delete(file: ProjectFileEntry)
}

/** Shared path rules（共享项目路径规则） that do not depend on a platform filesystem API. */
object ProjectFilesystemPolicy {
    fun childPath(parentRelativePath: String, name: String): String {
        val cleanParent = parentRelativePath.trim('/').trim()
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "file name must not be blank" }
        require(cleanName != "." && cleanName != "..") { "reserved file name" }
        require('/' !in cleanName && '\\' !in cleanName) {
            "file name must not contain path separators"
        }
        require(cleanName.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "file name contains unsupported characters"
        }
        return if (cleanParent.isBlank()) cleanName else "$cleanParent/$cleanName"
    }

    fun parentPath(relativePath: String): String =
        relativePath.substringBeforeLast('/', "")
}
