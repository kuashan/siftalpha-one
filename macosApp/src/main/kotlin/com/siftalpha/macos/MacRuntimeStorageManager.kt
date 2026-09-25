package com.siftalpha.macos

import com.siftalpha.core.storage.RuntimeStorageCleanupPolicy
import com.siftalpha.core.storage.RuntimeStorageEntry
import com.siftalpha.core.storage.RuntimeStorageKind
import com.siftalpha.core.storage.RuntimeStorageSnapshot
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * macOS Runtime Storage Manager（运行空间管理器）.
 *
 * It mirrors Android's mature safety semantics rather than Android paths:
 * - inventory first;
 * - never clean source code;
 * - never auto-clean an active/unknown environment;
 * - only reproducible SiftAlpha-managed data is eligible for automatic cleanup.
 */
class MacRuntimeStorageManager(
    private val dataRoot: File,
    private val environmentManager: MacProjectEnvironmentManager,
    private val isProjectActive: (String) -> Boolean,
    private val userHome: File = File(System.getProperty("user.home")),
) {
    fun snapshot(
        knownProjectIds: Collection<String>,
        containerProjectActive: Boolean,
    ): RuntimeStorageSnapshot {
        val entries = mutableListOf<RuntimeStorageEntry>()
        val knownByDirectory = knownProjectIds.associateBy { id ->
            environmentManager.managedProjectDirectory(id).name
        }

        val projectsRoot = File(dataRoot, "projects")
        projectsRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
            .sortedBy { it.name }
            .forEach { directory ->
                val knownId = knownByDirectory[directory.name]
                if (knownId != null) {
                    entries += RuntimeStorageEntry(
                        id = "path:" + directory.canonicalPath,
                        label = "项目环境 · " + knownId.substringAfterLast('/').takeLast(80),
                        kind = RuntimeStorageKind.PROJECT_ENVIRONMENT,
                        sizeBytes = sizeBytes(directory),
                        projectId = knownId,
                        active = isProjectActive(knownId),
                        reproducible = true,
                        cleanable = false,
                        detail = "当前项目环境由项目级“清理环境”管理",
                    )
                } else {
                    val identity = readProjectIdentity(directory)
                    val composeUnknown = File(directory, "compose-prepared.ready").isFile
                    val active = identity?.let(isProjectActive) ?: true
                    val provablySafe = identity != null && !active && !composeUnknown
                    entries += RuntimeStorageEntry(
                        id = "path:" + directory.canonicalPath,
                        label = "未关联项目环境 · " + directory.name,
                        kind = RuntimeStorageKind.ORPHAN_PROJECT_DATA,
                        sizeBytes = sizeBytes(directory),
                        projectId = identity,
                        active = active || composeUnknown,
                        reproducible = identity != null,
                        cleanable = provablySafe,
                        detail = when {
                            identity == null -> "缺少项目身份标记，保守保留"
                            composeUnknown -> "可能属于 Compose 项目，需重新导入确认后清理"
                            active -> "检测到仍有项目进程活动"
                            else -> "可安全清理的未关联 SiftAlpha 环境"
                        },
                    )
                }
            }

        val toolchainRoot = MacManagedContainerToolchain.root(userHome)
        val current = runCatching {
            MacManagedContainerToolchain.currentRoot(userHome)?.canonicalFile
        }.getOrNull()
        toolchainRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
            .sortedBy { it.name }
            .forEach { directory ->
                val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return@forEach
                val isCurrent = current?.path == canonical.path
                entries += RuntimeStorageEntry(
                    id = "path:" + canonical.path,
                    label = if (isCurrent) "当前托管容器工具链" else "旧托管容器工具链 · " + directory.name,
                    kind = RuntimeStorageKind.MANAGED_TOOLCHAIN,
                    sizeBytes = sizeBytes(canonical),
                    active = isCurrent,
                    reproducible = true,
                    cleanable = !isCurrent,
                    detail = if (isCurrent) {
                        "当前版本由 SiftAlpha 使用，不自动清理"
                    } else {
                        "非当前版本，可重新下载"
                    },
                )
            }

        current?.resolve("cache")?.takeIf { it.isDirectory }?.let { cache ->
            entries += RuntimeStorageEntry(
                id = "path:" + cache.canonicalPath,
                label = "托管容器共享缓存",
                kind = RuntimeStorageKind.SHARED_CACHE,
                sizeBytes = sizeBytes(cache),
                active = containerProjectActive,
                reproducible = true,
                cleanable = false,
                detail = "当前 Colima/Lima 可能仍在使用；本轮只统计，不冒险删除",
            )
        }

        runCatching { MacManagedContainerToolchain.managedStateRoot(userHome) }
            .getOrNull()
            ?.takeIf { it.exists() }
            ?.let { state ->
                entries += RuntimeStorageEntry(
                    id = "path:" + state.canonicalPath,
                    label = "托管容器运行状态",
                    kind = RuntimeStorageKind.PLATFORM_RUNTIME_STATE,
                    sizeBytes = sizeBytes(state),
                    active = true,
                    reproducible = false,
                    cleanable = false,
                    detail = "包含 VM / socket / Docker 状态，不自动清理",
                )
            }

        return RuntimeStorageSnapshot(entries.sortedWith(compareBy({ it.kind.name }, { it.label })))
    }

    fun cleanSafeEntries(
        knownProjectIds: Collection<String>,
        containerProjectActive: Boolean,
    ): RuntimeStorageSnapshot {
        val before = snapshot(knownProjectIds, containerProjectActive)
        RuntimeStorageCleanupPolicy.safeEntries(before).forEach { entry ->
            val target = pathFromEntry(entry) ?: return@forEach
            when (entry.kind) {
                RuntimeStorageKind.ORPHAN_PROJECT_DATA ->
                    deleteInside(target, File(dataRoot, "projects"))
                RuntimeStorageKind.MANAGED_TOOLCHAIN ->
                    deleteInside(target, MacManagedContainerToolchain.root(userHome))
                else -> Unit
            }
        }
        return snapshot(knownProjectIds, containerProjectActive)
    }

    private fun readProjectIdentity(directory: File): String? {
        val marker = File(directory, ".siftalpha-project-id")
        if (!marker.isFile || marker.length() > 16 * 1024) return null
        return runCatching { marker.readText().trim() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
    }

    private fun pathFromEntry(entry: RuntimeStorageEntry): File? =
        entry.id.removePrefix("path:")
            .takeIf { entry.id.startsWith("path:") && it.isNotBlank() }
            ?.let(::File)

    private fun deleteInside(target: File, allowedParent: File) {
        val parent = allowedParent.canonicalFile
        val canonical = target.canonicalFile
        require(canonical != parent && canonical.toPath().startsWith(parent.toPath())) {
            "runtime storage cleanup escaped allowed parent"
        }
        if (!canonical.exists()) return
        Files.walkFileTree(
            canonical.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun sizeBytes(root: File): Long {
        val path = root.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) return 0L
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return runCatching { Files.size(path) }.getOrDefault(0L)
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return 0L
        var total = 0L
        Files.newDirectoryStream(path).use { entries ->
            for (entry in entries) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("MACOS_RUNTIME_STORAGE_SCAN_CANCELLED")
                }
                total += sizeBytes(entry.toFile())
            }
        }
        return total
    }
}
