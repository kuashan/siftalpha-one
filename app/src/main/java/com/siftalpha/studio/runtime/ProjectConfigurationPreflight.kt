package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.ProjectConfigurationInspector

/** Pure configuration readiness evaluation used by Runtime Center before process launch. */
object ProjectConfigurationPreflight {

    data class Result(
        val requiredCount: Int,
        val configuredRequiredCount: Int,
        val missingRequired: List<ProjectConfigurationInspector.Requirement>,
        val credentialCandidateCount: Int,
    ) {
        val ready: Boolean
            get() = missingRequired.isEmpty()
    }

    fun evaluate(
        profile: ProjectConfigurationInspector.Profile,
        protectedConfiguredKeys: Set<String>,
        runtimeRequiredNames: Set<String> = emptySet(),
    ): Result {
        val configured = profile.configuredProjectEnvKeys + protectedConfiguredKeys
        val requiredByName = linkedMapOf<String, ProjectConfigurationInspector.Requirement>()
        profile.required.forEach { requirement ->
            requiredByName.putIfAbsent(requirement.name, requirement)
        }
        runtimeRequiredNames.forEach { name ->
            val normalized = name.trim()
            if (normalized.isBlank()) return@forEach
            val declared = profile.requirements.firstOrNull { it.name == normalized }
            val promoted = (declared ?: ProjectConfigurationInspector.Requirement(
                name = normalized,
                secret = ProjectConfigurationInspector.looksSensitive(normalized),
                required = true,
                description = "",
            )).copy(required = true)
            requiredByName[normalized] = promoted
        }

        val required = requiredByName.values.toList()
        val missing = required.filterNot { it.name in configured }
        return Result(
            requiredCount = required.size,
            configuredRequiredCount = required.size - missing.size,
            missingRequired = missing,
            credentialCandidateCount = profile.credentialCandidates.size,
        )
    }
}
