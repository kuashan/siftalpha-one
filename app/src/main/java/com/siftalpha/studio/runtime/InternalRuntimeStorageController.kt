package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.storage.SiftAlphaStorage
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * App-private storage inspection for Embedded R.
 *
 * This controller never touches Android shared-storage project source. It only observes SiftAlpha's
 * own filesDir tree and exposes deletion for caches whose contents are reproducible downloads.
 * Project-environment deletion remains owned by ProjectRuntimeController so active-session checks
 * and both Embedded CPython / Internal Alpine environment managers stay authoritative.
 */
class InternalRuntimeStorageController(private val filesDir: File) {

    data class ProjectUsage(
        val documentId: String,
        val folderName: String,
        val displayName: String,
        val cpythonEnvironmentKb: Long,
        val alpineEnvironmentKb: Long,
    ) {
        val totalKb: Long get() = cpythonEnvironmentKb + alpineEnvironmentKb
    }

    data class Snapshot(
        val totalKb: Long,
        val cpythonRuntimeKb: Long,
        val alpineRootfsKb: Long,
        val wheelCacheKb: Long,
        val pipCacheKb: Long,
        val npmCacheKb: Long,
        val stagingKb: Long,
        val sessionDataKb: Long,
        val projectEnvironmentKb: Long,
        val unassociatedEnvironmentKb: Long,
        val projects: List<ProjectUsage>,
    )

    fun snapshot(projects: List<V04ProjectGateway.RuntimeProject>): Snapshot {
        val knownIds = projects.associateBy { environmentId(it.summary.documentId) }
        val usages = projects.mapNotNull { project ->
            val id = environmentId(project.summary.documentId)
            val cpythonKb = sizeKb(File(cpythonEnvironmentsRoot(), id))
            val alpineKb = sizeKb(File(alpineEnvironmentsRoot(), id))
            if (cpythonKb <= 0L && alpineKb <= 0L) {
                null
            } else {
                ProjectUsage(
                    documentId = project.summary.documentId,
                    folderName = project.folderName,
                    displayName = project.summary.name,
                    cpythonEnvironmentKb = cpythonKb,
                    alpineEnvironmentKb = alpineKb,
                )
            }
        }.sortedBy { it.displayName.lowercase() }

        val unassociatedKb =
            unassociatedEnvironmentSizeKb(cpythonEnvironmentsRoot(), knownIds.keys) +
                unassociatedEnvironmentSizeKb(alpineEnvironmentsRoot(), knownIds.keys)

        val cpythonSessions = sizeKb(SiftAlphaStorage.cpythonSessionsRoot(filesDir))
        val alpineSessions = sizeKb(SiftAlphaStorage.alpineSessionsRoot(filesDir))

        return Snapshot(
            totalKb = sizeKb(siftAlphaRoot()),
            cpythonRuntimeKb = sizeKb(SiftAlphaStorage.cpythonRuntimeRoot(filesDir)),
            alpineRootfsKb = sizeKb(File(alpineRoot(), "rootfs")),
            wheelCacheKb = sizeKb(wheelCacheRoot()),
            pipCacheKb = sizeKb(alpinePipCacheRoot()),
            npmCacheKb = sizeKb(alpineNpmCacheRoot()),
            stagingKb = sizeKb(SiftAlphaStorage.projectsRoot(filesDir)),
            sessionDataKb = cpythonSessions + alpineSessions,
            projectEnvironmentKb = usages.sumOf { it.totalKb },
            unassociatedEnvironmentKb = unassociatedKb,
            projects = usages,
        )
    }

    fun clearWheelCache() = clearReproducibleCache(
        target = wheelCacheRoot(),
        allowedParent = siftAlphaRoot(),
    )

    fun clearAlpinePipCache() = clearReproducibleCache(
        target = alpinePipCacheRoot(),
        allowedParent = File(File(File(alpineRoot(), "rootfs"), "root"), ".cache"),
    )

    fun clearAlpineNpmCache() = clearReproducibleCache(
        target = alpineNpmCacheRoot(),
        allowedParent = File(File(File(alpineRoot(), "rootfs"), "root"), ".npm"),
    )

    private fun clearReproducibleCache(target: File, allowedParent: File) {
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        InterruptibleProjectTreeDelete.delete(
            environmentRoot = target,
            allowedParent = allowedParent,
            deadlineNanos = System.nanoTime() + RuntimeOperationContract.CLEAN_TIMEOUT_MS * 1_000_000L,
        )
    }

    private fun unassociatedEnvironmentSizeKb(root: File, knownIds: Set<String>): Long {
        if (!Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return 0L
        return root.listFiles().orEmpty()
            .filter { child ->
                child.name !in knownIds &&
                    Files.isDirectory(child.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(child.toPath())
            }
            .sumOf(::sizeKb)
    }

    private fun sizeKb(root: File): Long = (sizeBytes(root) + 1023L) / 1024L

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
                    throw InterruptedException("INTERNAL_STORAGE_SCAN_CANCELLED")
                }
                total += sizeBytes(entry.toFile())
            }
        }
        return total
    }

    private fun siftAlphaRoot(): File = SiftAlphaStorage.root(filesDir)
    private fun alpineRoot(): File = SiftAlphaStorage.alpineRuntimeRoot(filesDir)
    private fun cpythonEnvironmentsRoot(): File = SiftAlphaStorage.cpythonEnvironmentsRoot(filesDir)
    private fun alpineEnvironmentsRoot(): File = SiftAlphaStorage.alpineEnvironmentsRoot(filesDir)
    private fun wheelCacheRoot(): File = SiftAlphaStorage.wheelhouseRoot(filesDir)
    private fun alpinePipCacheRoot(): File = File(alpineRoot(), "rootfs/root/.cache/pip")
    private fun alpineNpmCacheRoot(): File = File(alpineRoot(), "rootfs/root/.npm/_cacache")

    companion object {
        internal fun environmentId(projectIdentity: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(projectIdentity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
