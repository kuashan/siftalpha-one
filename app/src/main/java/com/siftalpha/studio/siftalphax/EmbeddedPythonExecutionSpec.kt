package com.siftalpha.studio.siftalphax

import java.io.File

enum class EmbeddedPythonRuntimeKind {
    CPYTHON,
}

/**
 * Explicit M/R boundary data for one file-backed Python execution.
 *
 * The root is always an app-private staged directory in the experimental path. Relative
 * entrypoint and working-directory values prevent callers from smuggling arbitrary absolute paths
 * into the native boundary; native code repeats the containment and symlink checks.
 */
data class EmbeddedPythonExecutionSpec(
    val projectIdentity: String,
    val executionRoot: File,
    val entrypoint: String,
    val workingDirectory: String,
    val runtimeKind: EmbeddedPythonRuntimeKind,
    val sessionId: String,
    val generation: Long,
    val environmentSitePackages: File? = null,
    val environmentKey: String? = null,
    val arguments: List<String> = emptyList(),
) {
    init {
        require(projectIdentity.isNotBlank()) { "project identity must not be blank" }
        require(executionRoot.isAbsolute) { "execution root must be absolute" }
        require(safeRelativePath(entrypoint, allowCurrent = false)) {
            "entrypoint must be a safe relative path"
        }
        require(safeRelativePath(workingDirectory, allowCurrent = true)) {
            "working directory must be a safe relative path"
        }
        require(sessionId.matches(SESSION_ID_PATTERN)) { "session id is not safe" }
        require(generation > 0L) { "generation must be positive" }
        require((environmentSitePackages == null) == (environmentKey == null)) {
            "environment site-packages and environment key must be provided together"
        }
        require(arguments.size <= 64) { "too many Python arguments" }
        require(arguments.all { it.length <= 4096 && '\u0000' !in it }) {
            "invalid Python argument"
        }
        if (environmentSitePackages != null) {
            require(environmentSitePackages.isAbsolute) {
                "environment site-packages must be absolute"
            }
            require(environmentKey?.matches(ENVIRONMENT_KEY_PATTERN) == true) {
                "environment key is invalid"
            }
        }
    }

    val entrypointFile: File
        get() = File(executionRoot, entrypoint)

    val workingDirectoryFile: File
        get() = File(executionRoot, workingDirectory)

    val entrypointExists: Boolean
        get() = entrypointFile.isFile

    /**
     * Structural validation is intentionally separate from native validation so a missing
     * entrypoint can be handed to native and published as a real FAILED terminal result.
     */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        val root = runCatching { executionRoot.canonicalFile }.getOrNull()
        if (root == null || !root.isDirectory) {
            errors += "EXECUTION_ROOT_INVALID"
            return errors
        }

        val entrypoint = runCatching { entrypointFile.canonicalFile }.getOrNull()
        if (entrypoint == null || !isContained(root, entrypoint)) {
            errors += "ENTRYPOINT_OUTSIDE_ROOT"
        }
        if (!entrypointFile.isFile) {
            errors += "ENTRYPOINT_MISSING"
        }

        val workingDirectory = runCatching { workingDirectoryFile.canonicalFile }.getOrNull()
        if (workingDirectory == null || !isContained(root, workingDirectory)) {
            errors += "WORKING_DIRECTORY_OUTSIDE_ROOT"
        }
        if (!workingDirectoryFile.isDirectory) {
            errors += "WORKING_DIRECTORY_MISSING"
        }
        environmentSitePackages?.let { environment ->
            val canonical = runCatching { environment.canonicalFile }.getOrNull()
            if (canonical == null || !canonical.isDirectory) {
                errors += "ENVIRONMENT_SITE_PACKAGES_INVALID"
            }
        }
        return errors
    }

    fun nativeValidationErrors(): List<String> =
        validationErrors().filterNot { it == "ENTRYPOINT_MISSING" }

    companion object {
        private val SESSION_ID_PATTERN = Regex("[A-Za-z0-9._-]+")
        private val ENVIRONMENT_KEY_PATTERN = Regex("sha256:[0-9a-f]{64}")
        private fun safeRelativePath(value: String, allowCurrent: Boolean): Boolean {
            if (value.isBlank() || value.startsWith("/") || value.contains('\u0000')) {
                return false
            }
            if (!allowCurrent && value == ".") {
                return false
            }
            val components = value.split('/')
            return components.all { component ->
                component.isNotEmpty() && component != ".." && (allowCurrent || component != ".")
            }
        }

        private fun isContained(root: File, candidate: File): Boolean {
            val rootPath = root.path.trimEnd(File.separatorChar)
            val candidatePath = candidate.path
            return candidatePath == rootPath ||
                candidatePath.startsWith(rootPath + File.separator)
        }
    }
}
