package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy

/**
 * Preserves the accepted external-provider Python launch behavior while alpha40 introduces
 * structured CLI launches. Embedded R keeps its stricter entrypoint policy unchanged.
 */
internal object PythonLaunchCompatibilityPolicy {

    fun fallbackEntrypoint(
        summaryEntry: String,
        relativePaths: Collection<String>,
        strictFallback: String?,
    ): String? {
        val normalizedPaths = relativePaths
            .asSequence()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() }
            .toSet()
        val summaryCandidate = EmbeddedPythonEntrypointPolicy.safeRelativePath(summaryEntry)
            ?.takeIf { it in normalizedPaths }
        return summaryCandidate ?: strictFallback
    }

    fun preserveLegacyRun(
        resolution: PythonCliLaunchResolver.Resolution,
        legacyRun: String,
    ): PythonCliLaunchResolver.Resolution {
        if (resolution !is PythonCliLaunchResolver.Resolution.Missing) return resolution
        val command = legacyRun.trim()
        return if (command.isNotBlank()) {
            PythonCliLaunchResolver.Resolution.DeclaredRun(command)
        } else {
            resolution
        }
    }
}
