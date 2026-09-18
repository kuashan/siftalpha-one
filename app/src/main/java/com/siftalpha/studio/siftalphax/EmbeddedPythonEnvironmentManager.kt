package com.siftalpha.studio.siftalphax

import android.content.Context
import java.io.File

class EmbeddedPythonEnvironmentManager(context: Context) {
    private val appContext = context.applicationContext

    enum class Outcome {
        READY_NO_EXTERNAL_DEPENDENCIES,
        DEPENDENCY_INSTALL_REQUIRED,
    }

    data class PreparationResult(
        val ready: Boolean,
        val outcome: Outcome,
        val environmentRoot: File,
        val sitePackages: File,
    )

    fun prepareFoundation(
        projectIdentity: String,
        hasExternalDependencyRequirement: Boolean,
    ): PreparationResult {
        require(projectIdentity.isNotBlank()) { "projectIdentity must not be blank" }
        EmbeddedPythonFiles.prepare(appContext)
        EmbeddedPythonFiles.dependencyCacheRoot(appContext)
        val environmentRoot = EmbeddedPythonFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val sitePackages = File(environmentRoot, SITE_PACKAGES)
        check(sitePackages.mkdirs() || sitePackages.isDirectory) {
            "Unable to create project-owned site-packages directory"
        }
        val stateFile = File(environmentRoot, STATE_FILE)
        val readyMarker = File(environmentRoot, READY_MARKER)
        val outcome = if (hasExternalDependencyRequirement) {
            if (readyMarker.exists()) check(readyMarker.delete()) {
                "Unable to clear stale Internal Environment READY marker"
            }
            stateFile.writeText(Outcome.DEPENDENCY_INSTALL_REQUIRED.name + "\n")
            Outcome.DEPENDENCY_INSTALL_REQUIRED
        } else {
            stateFile.writeText(Outcome.READY_NO_EXTERNAL_DEPENDENCIES.name + "\n")
            readyMarker.writeText(SCHEMA + "\n")
            Outcome.READY_NO_EXTERNAL_DEPENDENCIES
        }
        return PreparationResult(
            ready = outcome == Outcome.READY_NO_EXTERNAL_DEPENDENCIES,
            outcome = outcome,
            environmentRoot = environmentRoot,
            sitePackages = sitePackages,
        )
    }

    fun isReady(projectIdentity: String): Boolean {
        val root = EmbeddedPythonFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val state = File(root, STATE_FILE)
        return File(root, READY_MARKER).isFile &&
            File(root, SITE_PACKAGES).isDirectory &&
            state.isFile &&
            state.readText().trim() == Outcome.READY_NO_EXTERNAL_DEPENDENCIES.name
    }

    companion object {
        private const val SCHEMA = "siftalpha.internal-python-environment.v1"
        private const val READY_MARKER = ".siftalpha_internal_environment_ready"
        private const val STATE_FILE = "state-v1.txt"
        private const val SITE_PACKAGES = "site-packages"
    }
}
