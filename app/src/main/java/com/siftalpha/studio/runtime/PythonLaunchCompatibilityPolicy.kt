package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
import com.siftalpha.studio.project.PythonCliRequirement

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

    fun bindCliRequirementsToEntrypoint(
        resolution: PythonCliLaunchResolver.Resolution,
        fallbackEntrypoint: String?,
        requirements: List<PythonCliRequirement>,
    ): PythonCliLaunchResolver.Resolution {
        if (resolution !is PythonCliLaunchResolver.Resolution.ConsoleScripts) return resolution
        val entrypoint = fallbackEntrypoint ?: return resolution
        val required = requirements.filter { it.required }
        if (required.isEmpty()) return resolution

        val evidencePaths = required
            .mapNotNull { requirement ->
                requirement.evidence?.filePath
                    ?.replace('\\', '/')
                    ?.trim()
                    ?.trim('/')
                    ?.takeIf { it.isNotBlank() }
            }
            .toSet()
        if (evidencePaths.size != 1 || evidencePaths.single() != entrypoint) {
            return resolution
        }
        if (required.any { it.evidence?.filePath.isNullOrBlank() }) {
            return resolution
        }
        return PythonCliLaunchResolver.Resolution.PythonFile(entrypoint)
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
