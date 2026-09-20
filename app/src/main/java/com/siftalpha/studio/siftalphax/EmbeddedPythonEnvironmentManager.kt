package com.siftalpha.studio.siftalphax

import android.content.Context
import android.os.Build
import com.siftalpha.studio.runtime.InterruptibleProjectTreeDelete
import com.siftalpha.studio.runtime.RuntimeOperationContract
import java.io.File

class EmbeddedPythonEnvironmentManager(
    context: Context,
    private val dependencyResolver: EmbeddedPythonDependencyResolverV1 =
        EmbeddedPythonDependencyResolverV1(),
) {
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

    fun resolveDependencyPlan(
        dependencyInput: EmbeddedPythonDependencyInputV1,
    ): EmbeddedPythonDependencyPlanV1 = dependencyResolver.resolve(
        input = dependencyInput,
        androidApiLevel = Build.VERSION.SDK_INT,
    )

    fun prepare(
        projectIdentity: String,
        dependencyInput: EmbeddedPythonDependencyInputV1,
        resolvedPlan: EmbeddedPythonDependencyPlanV1? = null,
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
            runtimeIdentity = EmbeddedPythonRuntimeIdentityV1.ID,
        )?.let { existing ->
            return PreparationResult(
                ready = true,
                outcome = Outcome.READY_REUSED,
                environmentRoot = environmentRoot,
                sitePackages = existing.sitePackages,
                environmentKey = existing.environmentKey,
            )
        }

        val plan = resolvedPlan?.also {
            check(it.sourceFingerprint == dependencyInput.sourceFingerprint) {
                "ENVIRONMENT_PLAN_SOURCE_FINGERPRINT_MISMATCH"
            }
        } ?: resolveDependencyPlan(dependencyInput)
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

    @Synchronized
    fun cleanProjectEnvironment(projectIdentity: String) {
        require(projectIdentity.isNotBlank()) { "projectIdentity must not be blank" }
        val root = EmbeddedPythonFiles.projectEnvironmentRoot(appContext, projectIdentity)
        if (root.exists()) {
            InterruptibleProjectTreeDelete.delete(
                environmentRoot = root,
                allowedParent = checkNotNull(root.parentFile),
                deadlineNanos = System.nanoTime() +
                    RuntimeOperationContract.CLEAN_TIMEOUT_MS * 1_000_000L,
            )
        }
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
            runtimeIdentity = EmbeddedPythonRuntimeIdentityV1.ID,
        ) ?: return null
        return LoadBinding(
            sitePackages = installed.sitePackages,
            environmentKey = installed.environmentKey,
        )
    }
}
