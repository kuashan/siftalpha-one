package com.siftalpha.studio.storage

import android.content.Context
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Single ownership registry for SiftAlpha-created app-private files.
 *
 * User-selected source trees are deliberately outside this registry and are never deleted here.
 * Android-managed SharedPreferences, databases, Keystore entries and no-backup security tokens
 * remain in their platform-standard locations; Android removes those with the application.
 */
object SiftAlphaStorage {
    const val ROOT_NAME = "SiftAlphaX"
    const val LAYOUT_VERSION = 1
    const val LAYOUT_FILE = "layout.info"

    private const val LEGACY_INTERNAL_ROOT = "siftalphax"
    private const val LEGACY_RESULT_ROOT = "siftalpha-results"
    private const val LEGACY_PROOT_TEMP = "siftalpha-proot-tmp"
    private const val LEGACY_PROOT_PIDS = "siftalpha-proot-pids"

    private val RESET_PREFERENCES = listOf(
        "siftalpha_runtime_lifecycle_v1",
        "siftalpha_runtime_operations_v1",
        "siftalpha_runtime_web_state_v1",
        "siftalpha_runtime_web_learned_endpoint_v1",
        "siftalpha_result_web_store_v1",
    )

    @Synchronized
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        initialize(
            filesDir = appContext.filesDir,
            cacheDir = appContext.cacheDir,
        ) {
            RESET_PREFERENCES.forEach { name ->
                appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }
    }

    /**
     * R47 is one deliberate development-stage reset, not a recurring upgrade wipe.
     * Once layout.info matches layout 1, future launches preserve the complete SiftAlphaX tree.
     */
    @Synchronized
    internal fun initialize(
        filesDir: File,
        cacheDir: File,
        onReset: () -> Unit = {},
    ) {
        val root = root(filesDir)
        val marker = File(root, LAYOUT_FILE)
        if (marker.isFile && runCatching { marker.readText() }.getOrNull() == layoutText()) {
            ensureLayoutDirectories(filesDir)
            return
        }

        deleteOwnedChild(filesDir, ROOT_NAME)
        deleteOwnedChild(filesDir, LEGACY_INTERNAL_ROOT)
        deleteOwnedChild(filesDir, LEGACY_RESULT_ROOT)
        deleteOwnedChild(cacheDir, LEGACY_PROOT_TEMP)
        deleteOwnedChild(cacheDir, LEGACY_PROOT_PIDS)
        onReset()

        ensureLayoutDirectories(filesDir)
        val tempMarker = File(root, "$LAYOUT_FILE.tmp")
        tempMarker.writeText(layoutText())
        if (!tempMarker.renameTo(marker)) {
            marker.writeText(layoutText())
            tempMarker.delete()
        }
        check(marker.isFile && marker.readText() == layoutText()) {
            "Unable to commit SiftAlphaX storage layout marker"
        }
    }

    fun root(filesDir: File): File = File(filesDir, ROOT_NAME)
    fun runtimesRoot(filesDir: File): File = File(root(filesDir), "runtimes")
    fun cpythonRuntimeRoot(filesDir: File): File = File(runtimesRoot(filesDir), "cpython")
    fun alpineRuntimeRoot(filesDir: File): File = File(runtimesRoot(filesDir), "alpine")

    fun environmentsRoot(filesDir: File): File = File(root(filesDir), "environments")
    fun cpythonEnvironmentsRoot(filesDir: File): File = File(environmentsRoot(filesDir), "cpython")
    fun alpineEnvironmentsRoot(filesDir: File): File = File(environmentsRoot(filesDir), "alpine")

    fun projectsRoot(filesDir: File): File = File(root(filesDir), "projects")

    fun sessionsRoot(filesDir: File): File = File(root(filesDir), "sessions")
    fun cpythonSessionsRoot(filesDir: File): File = File(sessionsRoot(filesDir), "cpython")
    fun alpineSessionsRoot(filesDir: File): File = File(sessionsRoot(filesDir), "alpine")

    fun logsRoot(filesDir: File): File = File(root(filesDir), "logs")
    fun resultsRoot(filesDir: File): File = File(root(filesDir), "results")
    fun wheelhouseRoot(filesDir: File): File = File(root(filesDir), "wheelhouse")

    fun stateRoot(filesDir: File): File = File(root(filesDir), "state")
    fun alpinePidRoot(filesDir: File): File = File(stateRoot(filesDir), "pids/alpine")

    fun tempRoot(filesDir: File): File = File(root(filesDir), "temp")
    fun prootTempRoot(filesDir: File): File = File(tempRoot(filesDir), "proot")

    internal fun layoutText(): String =
        "SIFTALPHA_STORAGE_LAYOUT=$LAYOUT_VERSION\n" +
            "ROOT=$ROOT_NAME\n" +
            "OWNERSHIP=APP_PRIVATE\n" +
            "USER_SOURCE_EXCLUDED=1\n"

    private fun ensureLayoutDirectories(filesDir: File) {
        listOf(
            runtimesRoot(filesDir),
            cpythonRuntimeRoot(filesDir),
            alpineRuntimeRoot(filesDir),
            environmentsRoot(filesDir),
            cpythonEnvironmentsRoot(filesDir),
            alpineEnvironmentsRoot(filesDir),
            projectsRoot(filesDir),
            sessionsRoot(filesDir),
            cpythonSessionsRoot(filesDir),
            alpineSessionsRoot(filesDir),
            logsRoot(filesDir),
            resultsRoot(filesDir),
            wheelhouseRoot(filesDir),
            stateRoot(filesDir),
            alpinePidRoot(filesDir),
            tempRoot(filesDir),
            prootTempRoot(filesDir),
        ).forEach { directory ->
            check(directory.mkdirs() || directory.isDirectory) {
                "Unable to create SiftAlphaX storage directory: $directory"
            }
        }
    }

    private fun deleteOwnedChild(parent: File, childName: String) {
        val normalizedParent = parent.toPath().toAbsolutePath().normalize()
        val target = normalizedParent.resolve(childName).normalize()
        check(target.parent == normalizedParent) { "Unsafe SiftAlpha storage reset target" }
        deleteTree(target)
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path)
            return
        }
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: java.io.IOException?,
                ): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
