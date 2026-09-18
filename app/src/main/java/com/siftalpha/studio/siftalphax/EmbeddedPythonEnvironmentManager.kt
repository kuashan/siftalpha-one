package com.siftalpha.studio.siftalphax

import android.content.Context
import android.os.Build
import java.io.File

class EmbeddedPythonEnvironmentManager(context: Context) {
    private val appContext = context.applicationContext

    enum class Outcome {
        READY_REUSED,
        READY_NO_EXTERNAL_DEPENDENCIES,
        READY_DEPENDENCIES_INSTALLED,
    }

    data class PreparationResult(
        val ready: Boolean,
        val outcome: Outcome,
        val environmentRoot: File,
        val sitePackages: File,
        val environmentKey: String,
    )

    data class LoadBinding(
        val sitePackages: File,
        val environmentKey: String,
    )

    fun prepare(
        projectIdentity: String,
        dependencyInput: EmbeddedPythonDependencyInputV1,
    ): PreparationResult {
        require(projectIdentity.isNotBlank()) { "projectIdentity must not be blank" }
        EmbeddedPythonFiles.prepare(appContext)
        val cacheRoot = EmbeddedPythonFiles.dependencyCacheRoot(appContext)
        val environmentRoot = EmbeddedPythonFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val installer = EmbeddedPythonWheelInstallerV1(cacheRoot)

        installer.readReadyBinding(
            projectIdentity = projectIdentity,
            environmentRoot = environmentRoot,
            sourceFingerprint = dependencyInput.sourceFingerprint,
        )?.let { existing ->
            return PreparationResult(
                ready = true,
                outcome = Outcome.READY_REUSED,
                environmentRoot = environmentRoot,
                sitePackages = existing.sitePackages,
                environmentKey = existing.environmentKey,
            )
        }

        val plan = EmbeddedPythonDependencyResolverV1().resolve(
            input = dependencyInput,
            androidApiLevel = Build.VERSION.SDK_INT,
        )
        val installed = installer.install(
            projectIdentity = projectIdentity,
            environmentRoot = environmentRoot,
            plan = plan,
        )
        return PreparationResult(
            ready = true,
            outcome = if (plan.packages.isEmpty()) {
                Outcome.READY_NO_EXTERNAL_DEPENDENCIES
            } else {
                Outcome.READY_DEPENDENCIES_INSTALLED
            },
            environmentRoot = environmentRoot,
            sitePackages = installed.sitePackages,
            environmentKey = installed.environmentKey,
        )
    }

    fun loadBinding(
        projectIdentity: String,
        dependencyInput: EmbeddedPythonDependencyInputV1,
    ): LoadBinding? {
        val environmentRoot = EmbeddedPythonFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val installed = EmbeddedPythonWheelInstallerV1(
            EmbeddedPythonFiles.dependencyCacheRoot(appContext),
        ).readReadyBinding(
            projectIdentity = projectIdentity,
            environmentRoot = environmentRoot,
            sourceFingerprint = dependencyInput.sourceFingerprint,
        ) ?: return null
        return LoadBinding(
            sitePackages = installed.sitePackages,
            environmentKey = installed.environmentKey,
        )
    }
}
