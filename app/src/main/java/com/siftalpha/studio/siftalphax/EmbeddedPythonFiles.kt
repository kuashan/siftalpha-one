package com.siftalpha.studio.siftalphax

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object EmbeddedPythonFiles {
    private const val ASSET_ROOT = "siftalphax/python"
    private const val PRIVATE_ROOT = "siftalphax"
    private const val READY_MARKER = ".siftalpha_x_assets_ready"

    fun prepare(context: Context): File {
        val home = runtimeHome(context.filesDir)
        val marker = File(home, READY_MARKER)
        if (!marker.isFile || !File(home, "lib/python3.14/os.py").isFile) {
            if (home.exists()) check(home.deleteRecursively()) {
                "Unable to clear private CPython Runtime Base"
            }
            check(home.mkdirs() || home.isDirectory) { "Unable to create private CPython directory" }
            copyAssetTree(context, ASSET_ROOT, home)
            check(File(home, "lib/python3.14/os.py").isFile) {
                "Embedded CPython standard library was not packaged"
            }
            check(marker.createNewFile() || marker.isFile) {
                "Unable to mark private CPython assets ready"
            }
        }
        check(File(home, "lib/python3.14/lib-dynload").isDirectory) {
            "Embedded CPython extension directory was not packaged"
        }
        check(File(home, "tmp").mkdirs() || File(home, "tmp").isDirectory) {
            "Unable to create private CPython temporary directory"
        }
        return home
    }

    fun stageProjectFixture(context: Context, fixture: EmbeddedPythonProjectFixture, sessionId: String): File {
        val projectsRoot = projectStagingRoot(context.filesDir)
        check(projectsRoot.mkdirs() || projectsRoot.isDirectory) { "Unable to create private project staging directory" }
        val root = File(projectsRoot, sessionId)
        check(root.parentFile?.canonicalFile == projectsRoot.canonicalFile) {
            "Project session staging escaped the app-private root"
        }
        if (root.exists()) check(root.deleteRecursively()) { "Unable to clear existing project staging" }
        check(root.mkdirs() || root.isDirectory) { "Unable to create project staging root" }
        copyAssetTree(context, fixture.assetPath, root)
        return root
    }

    fun projectEnvironmentRoot(context: Context, projectIdentity: String): File =
        projectEnvironmentRoot(context.filesDir, projectIdentity)

    fun dependencyCacheRoot(context: Context): File = dependencyCacheRoot(context.filesDir)

    fun sessionWorkspaceRoot(context: Context, sessionId: String): File =
        sessionWorkspaceRoot(context.filesDir, sessionId)

    internal fun runtimeHome(filesDir: File): File = File(filesDir, "$PRIVATE_ROOT/python")
    internal fun projectStagingRoot(filesDir: File): File = File(filesDir, "$PRIVATE_ROOT/projects")

    internal fun projectEnvironmentRoot(filesDir: File, projectIdentity: String): File {
        require(projectIdentity.isNotBlank()) { "projectIdentity must not be blank" }
        val environments = File(filesDir, "$PRIVATE_ROOT/environments")
        check(environments.mkdirs() || environments.isDirectory) { "Unable to create Embedded Python environments root" }
        val root = File(environments, sha256Hex(projectIdentity))
        check(root.parentFile?.canonicalFile == environments.canonicalFile) {
            "Project environment escaped the app-private environments root"
        }
        check(root.mkdirs() || root.isDirectory) { "Unable to create project-owned Embedded Python environment root" }
        return root.canonicalFile
    }

    internal fun dependencyCacheRoot(filesDir: File): File {
        val root = File(filesDir, "$PRIVATE_ROOT/cache/wheels")
        check(root.mkdirs() || root.isDirectory) { "Unable to create Embedded Python dependency cache root" }
        return root.canonicalFile
    }

    internal fun sessionWorkspaceRoot(filesDir: File, sessionId: String): File {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        val sessions = File(filesDir, "$PRIVATE_ROOT/sessions")
        check(sessions.mkdirs() || sessions.isDirectory) { "Unable to create Embedded Python session workspace root" }
        val root = File(sessions, sha256Hex(sessionId))
        check(root.parentFile?.canonicalFile == sessions.canonicalFile) {
            "Session workspace escaped the app-private sessions root"
        }
        check(root.mkdirs() || root.isDirectory) { "Unable to create Embedded Python session workspace" }
        return root.canonicalFile
    }

    fun provenance(context: Context): String =
        context.assets.open("siftalphax/cpython/PROVENANCE.md").bufferedReader().use { it.readText() }

    private fun copyAssetTree(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        check(destination.mkdirs() || destination.isDirectory) {
            "Unable to create private CPython asset directory: $destination"
        }
        children.forEach { child -> copyAssetTree(context, "$assetPath/$child", File(destination, child)) }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
