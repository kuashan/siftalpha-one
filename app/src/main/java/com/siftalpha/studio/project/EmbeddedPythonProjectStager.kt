package com.siftalpha.studio.project

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Explicit bounds for one SAF-to-app-private Embedded R staging transaction. */
data class EmbeddedPythonStagingLimits(
    val maxNodes: Int = 1_024,
    val maxFiles: Int = 512,
    val maxFileBytes: Int = 4 * 1024 * 1024,
    val maxTotalBytes: Long = 32L * 1024L * 1024L,
) {
    init {
        require(maxNodes > 0)
        require(maxFiles > 0)
        require(maxFileBytes > 0)
        require(maxTotalBytes > 0L)
    }
}

/**
 * Copies one M-owned SAF project into an app-private, bounded execution root.
 * It owns only the generated staging copy; it never mutates or deletes the SAF source.
 */
class EmbeddedPythonProjectStager(
    context: Context,
    private val projectStore: ProjectStore = ProjectStore(context.applicationContext),
) {
    private val appContext = context.applicationContext

    fun stage(projectDocumentId: String, entrypoint: String): File {
        require(projectDocumentId.isNotBlank()) { "项目身份不能为空" }
        val projectsRoot = File(appContext.filesDir, PRIVATE_PROJECTS_PATH)
        check(projectsRoot.mkdirs() || projectsRoot.isDirectory) {
            "无法创建内置 R 项目暂存目录"
        }
        val canonicalProjectsRoot = projectsRoot.canonicalFile
        val stagingRoot = File(projectsRoot, "session-" + UUID.randomUUID())
        check(stagingRoot.parentFile?.canonicalFile == canonicalProjectsRoot) {
            "项目暂存路径逃出 app-private 根目录"
        }
        check(!stagingRoot.exists()) { "项目暂存目录已存在" }

        val nodes = projectStore.listProjectTreeForStaging(projectDocumentId)
        return stageNodes(
            destination = stagingRoot,
            nodes = nodes,
            entrypoint = entrypoint,
            limits = DEFAULT_LIMITS,
        ) { node, maxBytes ->
            projectStore.readProjectFileBytes(node, maxBytes)
        }
    }

    fun cleanup(stagingRoot: File) {
        if (!stagingRoot.exists()) return
        val projectsRoot = File(appContext.filesDir, PRIVATE_PROJECTS_PATH)
        val canonicalProjectsRoot = projectsRoot.canonicalFile
        val canonicalRoot = stagingRoot.canonicalFile
        check(canonicalRoot != canonicalProjectsRoot && pathWithin(canonicalProjectsRoot, canonicalRoot)) {
            "拒绝清理 app-private 项目暂存根之外的路径"
        }
        check(!Files.isSymbolicLink(stagingRoot.toPath())) {
            "拒绝清理符号链接暂存路径"
        }
        check(stagingRoot.deleteRecursively()) { "无法清理项目暂存目录" }
    }

    companion object {
        const val PRIVATE_PROJECTS_PATH = "siftalphax/projects"
        val DEFAULT_LIMITS = EmbeddedPythonStagingLimits()

        /** Pure file-backed copy primitive used by JVM tests and the Android SAF adapter above. */
        internal fun stageNodes(
            destination: File,
            nodes: List<ProjectStore.FileNode>,
            entrypoint: String,
            limits: EmbeddedPythonStagingLimits = DEFAULT_LIMITS,
            reader: (ProjectStore.FileNode, Int) -> ByteArray,
        ): File {
            val safeEntrypoint = EmbeddedPythonEntrypointPolicy.safeRelativePath(entrypoint)
                ?: error("入口路径不是安全的项目相对路径")
            require(nodes.size <= limits.maxNodes) {
                "项目文件树超过内置 R 暂存节点上限 ${limits.maxNodes}"
            }

            val entries = nodes.map { node ->
                val safePath = EmbeddedPythonEntrypointPolicy.safeRelativePath(node.relativePath)
                    ?: error("项目包含不安全相对路径：${node.relativePath}")
                safePath to node
            }
            require(entries.map { it.first }.distinct().size == entries.size) {
                "项目文件树包含重复路径"
            }
            val files = entries.filterNot { it.second.isDirectory }
            require(files.size <= limits.maxFiles) {
                "项目文件数量超过内置 R 暂存上限 ${limits.maxFiles}"
            }
            require(files.any { it.first == safeEntrypoint }) {
                "入口文件不在项目暂存源树中"
            }

            var destinationCreated = false
            try {
                check(!destination.exists()) { "项目暂存目录已存在" }
                check(destination.mkdirs() || destination.isDirectory) {
                    "无法创建项目暂存根"
                }
                destinationCreated = true
                val canonicalRoot = destination.canonicalFile
                check(!Files.isSymbolicLink(destination.toPath())) {
                    "项目暂存根不能是符号链接"
                }

                entries.filter { it.second.isDirectory }
                    .sortedBy { pathDepth(it.first) }
                    .forEach { (path, _) -> ensureDirectory(canonicalRoot, path) }

                var totalBytes = 0L
                files.sortedBy { it.first }.forEach { (path, node) ->
                    val parent = path.substringBeforeLast('/', "")
                    if (parent.isNotBlank()) ensureDirectory(canonicalRoot, parent)
                    val target = File(canonicalRoot, path)
                    check(pathWithin(canonicalRoot, target.canonicalFile)) {
                        "项目文件路径逃出暂存根：$path"
                    }
                    check(!Files.isSymbolicLink(target.toPath())) {
                        "项目文件符号链接不允许进入暂存区：$path"
                    }
                    check(!target.exists()) { "项目文件路径冲突：$path" }
                    val bytes = reader(node, limits.maxFileBytes + 1)
                    require(bytes.size <= limits.maxFileBytes) {
                        "项目文件超过单文件暂存上限：$path"
                    }
                    totalBytes += bytes.size.toLong()
                    require(totalBytes <= limits.maxTotalBytes) {
                        "项目总大小超过内置 R 暂存上限 ${limits.maxTotalBytes}"
                    }
                    target.outputStream().use { it.write(bytes) }
                }
                return canonicalRoot
            } catch (error: Throwable) {
                if (destinationCreated) destination.deleteRecursively()
                throw error
            }
        }

        private fun ensureDirectory(root: File, relativePath: String) {
            val safePath = EmbeddedPythonEntrypointPolicy.safeRelativePath(relativePath)
                ?: error("项目目录路径不是安全的相对路径")
            var current = root
            safePath.split('/').forEach { component ->
                val next = File(current, component)
                check(!Files.isSymbolicLink(next.toPath())) {
                    "项目目录符号链接不允许进入暂存区：$safePath"
                }
                if (next.exists()) {
                    check(next.isDirectory) { "项目目录路径与文件冲突：$safePath" }
                } else {
                    check(next.mkdirs() || next.isDirectory) { "无法创建项目目录：$safePath" }
                }
                check(pathWithin(root, next.canonicalFile)) {
                    "项目目录路径逃出暂存根：$safePath"
                }
                current = next
            }
        }

        private fun pathDepth(path: String): Int = path.count { it == '/' }

        private fun pathWithin(root: File, candidate: File): Boolean {
            val rootPath = root.path.trimEnd(File.separatorChar)
            val candidatePath = candidate.path
            return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
        }
    }
}
