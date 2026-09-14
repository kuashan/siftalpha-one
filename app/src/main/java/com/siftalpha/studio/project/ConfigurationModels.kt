package com.siftalpha.studio.project

/**
 * Configuration semantics shared by static inspection, runtime diagnostics and UI presentation.
 *
 * Severity answers whether a missing value can block START. Source and evidence explain why Studio
 * surfaced the item, so a suggestion is not presented as an unverified requirement.
 */
enum class ConfigurationSeverity {
    REQUIRED,
    OPTIONAL,
}

enum class ConfigurationSource {
    PROJECT_DECLARED,
    STATIC_REQUIRED_READ,
    STATIC_OPTIONAL_READ,
    ENV_EXAMPLE,
    RUNTIME_DIAGNOSTIC,
}

data class ConfigurationEvidence(
    val filePath: String? = null,
    val lineNumber: Int? = null,
    val detail: String? = null,
) {
    init {
        require(lineNumber == null || lineNumber > 0) {
            "lineNumber must be positive when present"
        }
    }
}

data class ConfigurationItem(
    val key: String,
    val isConfigured: Boolean,
    val severity: ConfigurationSeverity,
    val source: ConfigurationSource,
    val description: String? = null,
    val evidence: ConfigurationEvidence? = null,
    val secret: Boolean = false,
) {
    init {
        require(key.isNotBlank()) { "key must not be blank" }
    }

    val required: Boolean
        get() = severity == ConfigurationSeverity.REQUIRED

    val name: String
        get() = key
}
