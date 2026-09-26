package com.siftalpha.studio.runtime

/**
 * Selects the bounded Internal Python staging profile from project/runtime authority.
 *
 * A project-owned console-script contract means execution is package/project scoped rather than a
 * single-file entrypoint, even when a UI caller has not yet supplied the final invocation. Vite
 * source builds and explicit console-script invocations therefore require full-project staging.
 * Simple Python-file projects keep the stricter entrypoint staging profile.
 */
internal enum class InternalPythonStagingProfile {
    ENTRYPOINT,
    FULL_PROJECT,
}

internal object InternalPythonStagingProfilePolicy {
    fun select(
        requiresNodeVite: Boolean,
        launchInvocation: PythonLaunchInvocation?,
        pyprojectToml: String?,
    ): InternalPythonStagingProfile {
        if (requiresNodeVite) return InternalPythonStagingProfile.FULL_PROJECT
        if (launchInvocation?.kind == PythonLaunchKind.CONSOLE_SCRIPT) {
            return InternalPythonStagingProfile.FULL_PROJECT
        }

        val projectOwnsConsoleScript =
            PythonCliLaunchResolver.resolve(
                declaredRun = null,
                pyprojectToml = pyprojectToml,
                fallbackEntrypoint = null,
            ) is PythonCliLaunchResolver.Resolution.ConsoleScripts

        return if (projectOwnsConsoleScript) {
            InternalPythonStagingProfile.FULL_PROJECT
        } else {
            InternalPythonStagingProfile.ENTRYPOINT
        }
    }
}
