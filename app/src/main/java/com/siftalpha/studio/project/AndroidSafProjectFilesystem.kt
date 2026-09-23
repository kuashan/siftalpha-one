package com.siftalpha.studio.project

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.siftalpha.core.filesystem.ProjectFileEntry
import com.siftalpha.core.filesystem.ProjectFilesystem
import com.siftalpha.core.filesystem.ProjectFilesystemPolicy

/**
 * Android SAF（安卓存储访问框架） implementation of ProjectFilesystem（项目文件系统端口）.
 *
 * Android Uri / ContentResolver / DocumentsContract（URI / 内容解析器 / 文档接口） stop here and
 * never enter Core（核心）.
 */
internal class AndroidSafProjectFilesystem(
    context: Context,
    private val rootUriProvider: () -> Uri?,
) : ProjectFilesystem {

    private val resolver = context.contentResolver

    override fun listChildren(
        projectId: String,
        parentId: String,
        parentRelativePath: String,
        depth: Int,
    ): List<ProjectFileEntry> {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        require(parentId.isNotBlank()) { "parentId must not be blank" }
        require(depth >= 0) { "depth must not be negative" }

        val treeUri = rootUri()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val result = mutableListOf<ProjectFileEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val mimeIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                if (name.isBlank()) continue
                val mime = cursor.getString(mimeIndex)
                result += ProjectFileEntry(
                    id = id,
                    name = name,
                    relativePath = ProjectFilesystemPolicy.childPath(parentRelativePath, name),
                    mediaType = mime,
                    depth = depth,
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        }
        return result
    }

    override fun readBytes(
        file: ProjectFileEntry,
        maxBytes: Int,
    ): ByteArray {
        require(!file.isDirectory) { "directory cannot be read as a file" }
        require(maxBytes > 0) { "read limit must be positive" }

        val uri = documentUri(file.id)
        return resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(maxBytes)
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) break
                offset += count
            }
            buffer.copyOf(offset)
        } ?: error("无法读取 ${file.relativePath}")
    }

    override fun writeBytes(
        file: ProjectFileEntry,
        content: ByteArray,
    ) {
        require(!file.isDirectory) { "directory cannot be written as a file" }
        val uri = documentUri(file.id)
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(content)
            output.flush()
        } ?: error("无法写入 ${file.relativePath}")
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
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        val parentUri = documentUri(parentId)
        val created = createExactDocument(
            parentUri = parentUri,
            name = name,
            requestedMime = mediaType ?: "application/octet-stream",
        )
        if (initialContent.isNotEmpty()) {
            resolver.openOutputStream(created, "wt")?.use { output ->
                output.write(initialContent)
                output.flush()
            } ?: error("无法写入 $name")
        }
        return ProjectFileEntry(
            id = DocumentsContract.getDocumentId(created),
            name = documentDisplayName(created) ?: name,
            relativePath = ProjectFilesystemPolicy.childPath(parentRelativePath, name),
            mediaType = documentMimeType(created) ?: mediaType,
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
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        val folderUri = DocumentsContract.createDocument(
            resolver,
            documentUri(parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: error("无法创建文件夹 $name")

        return ProjectFileEntry(
            id = DocumentsContract.getDocumentId(folderUri),
            name = documentDisplayName(folderUri) ?: name,
            relativePath = ProjectFilesystemPolicy.childPath(parentRelativePath, name),
            mediaType = DocumentsContract.Document.MIME_TYPE_DIR,
            depth = depth,
            isDirectory = true,
        )
    }

    override fun rename(
        file: ProjectFileEntry,
        newName: String,
    ): ProjectFileEntry {
        val renamedUri = DocumentsContract.renameDocument(
            resolver,
            documentUri(file.id),
            newName,
        ) ?: error("重命名失败")

        val actualName = documentDisplayName(renamedUri)
        require(actualName == null || actualName == newName) {
            "Android 文件提供器修改了文件名：$newName -> $actualName"
        }
        return file.copy(
            id = DocumentsContract.getDocumentId(renamedUri),
            name = newName,
            relativePath = ProjectFilesystemPolicy.childPath(
                ProjectFilesystemPolicy.parentPath(file.relativePath),
                newName,
            ),
            mediaType = documentMimeType(renamedUri) ?: file.mediaType,
        )
    }

    override fun delete(file: ProjectFileEntry) {
        check(DocumentsContract.deleteDocument(resolver, documentUri(file.id))) {
            "删除失败：${file.relativePath}"
        }
    }

    private fun rootUri(): Uri = rootUriProvider() ?: error("项目目录不可用")

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(rootUri(), documentId)

    private fun createExactDocument(
        parentUri: Uri,
        name: String,
        requestedMime: String,
    ): Uri {
        val createMime = if (
            requestedMime.startsWith("text/") ||
            name.startsWith(".") ||
            name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
        ) {
            "application/octet-stream"
        } else {
            requestedMime
        }

        var uri = DocumentsContract.createDocument(resolver, parentUri, createMime, name)
            ?: error("无法创建 $name")
        var actualName = documentDisplayName(uri)
        if (actualName != null && actualName != name) {
            uri = DocumentsContract.renameDocument(resolver, uri, name) ?: uri
            actualName = documentDisplayName(uri)
        }
        require(actualName == null || actualName == name) {
            "Android 文件提供器修改了文件名：$name -> $actualName"
        }
        return uri
    }

    private fun documentDisplayName(uri: Uri): String? {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun documentMimeType(uri: Uri): String? {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "py", "pyi", "txt", "md", "json", "jsonl", "toml", "yaml", "yml",
            "ini", "cfg", "conf", "sh", "bash", "zsh", "fish", "sql", "csv",
            "xml", "html", "htm", "css", "scss", "js", "mjs", "cjs", "ts",
            "tsx", "jsx", "java", "kt", "kts", "gradle", "properties", "env",
            "gitignore", "dockerfile",
        )
    }
}
