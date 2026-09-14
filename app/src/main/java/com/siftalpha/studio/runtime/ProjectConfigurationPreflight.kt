package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.ConfigurationEvidence
import com.siftalpha.studio.project.ConfigurationSource
import com.siftalpha.studio.project.ProjectConfigurationInspector

/** Pure configuration readiness evaluation used by Runtime Center before process launch. */
object ProjectConfigurationPreflight {

    data class Result(
        val requiredCount: Int,
        val configuredRequiredCount: Int,
        val missingRequired: List<ProjectConfigurationInspector.Requirement>,
        val credentialCandidateCount: Int,
        val optionalConfiguredCount: Int = 0,
        val missingOptional: List<ProjectConfigurationInspector.Requirement> = emptyList(),
    ) {
        val ready: Boolean
            get() = missingRequired.isEmpty()

        val requiredMissingCount: Int
            get() = missingRequired.size

        val optionalMissingCount: Int
            get() = missingOptional.size

        val optionalCount: Int
            get() = optionalConfiguredCount + optionalMissingCount
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
                ?: profile.optional.firstOrNull { it.name == normalized }
            val promoted = (declared ?: ProjectConfigurationInspector.Requirement(
                name = normalized,
                secret = ProjectConfigurationInspector.looksSensitive(normalized),
                required = true,
                description = "",
            )).copy(
                required = true,
                source = ConfigurationSource.RUNTIME_DIAGNOSTIC,
                evidence = ConfigurationEvidence(
                    detail = "Runtime reported missing configuration",
                ),
            )
            requiredByName[normalized] = promoted
        }

        val required = requiredByName.values.toList()
        val missingRequired = required.filterNot { it.name in configured }
        val requiredNames = required.mapTo(linkedSetOf()) { it.name }
        val optional = profile.optional.filterNot { it.name in requiredNames }
        val missingOptional = optional.filterNot { it.name in configured }

        return Result(
            requiredCount = required.size,
            configuredRequiredCount = required.size - missingRequired.size,
            missingRequired = missingRequired,
            credentialCandidateCount = profile.credentialCandidates.size,
            optionalConfiguredCount = optional.size - missingOptional.size,
            missingOptional = missingOptional,
        )
    }
}
