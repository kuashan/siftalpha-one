package com.siftalpha.studio.siftalphax

import android.content.Context
import java.io.File

/** Copies only generated CPython assets into app-private storage; no SAF or external paths. */
object EmbeddedPythonFiles {
    private const val ASSET_ROOT = "siftalphax/python"
    private const val PRIVATE_ROOT = "siftalphax"
    private const val READY_MARKER = ".siftalpha_x_assets_ready"

    fun prepare(context: Context): File {
        val root = File(context.filesDir, PRIVATE_ROOT)
        val home = File(root, "python")
        val marker = File(home, READY_MARKER)
        if (!marker.isFile || !File(home, "lib/python3.14/os.py").isFile) {
            check(!home.exists() || home.deleteRecursively()) {
                "Unable to clear incomplete private CPython directory"
            }
            check(root.mkdirs() || root.isDirectory) {
                "Unable to create private SiftAlpha X directory"
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

    fun stageProjectFixture(
        context: Context,
        fixture: EmbeddedPythonProjectFixture,
        sessionId: String,
    ): File {
        val projectsRoot = File(context.filesDir, "$PRIVATE_ROOT/projects")
        check(projectsRoot.mkdirs() || projectsRoot.isDirectory) {
            "Unable to create private project staging directory"
        }
        val root = File(projectsRoot, sessionId)
        val canonicalProjectsRoot = projectsRoot.canonicalFile
        check(root.parentFile?.canonicalFile == canonicalProjectsRoot) {
            "Project session staging escaped the app-private root"
        }
        if (root.exists()) {
            check(root.deleteRecursively()) { "Unable to clear existing project staging" }
        }
        check(root.mkdirs() || root.isDirectory) {
            "Unable to create project staging root"
        }
        copyAssetTree(context, fixture.assetPath, root)
        return root
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
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(destination, child))
        }
    }
}
