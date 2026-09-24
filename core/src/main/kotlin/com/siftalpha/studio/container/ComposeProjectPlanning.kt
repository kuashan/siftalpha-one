package com.siftalpha.studio.container

import com.siftalpha.studio.platform.CapabilityAvailability
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

enum class ComposeProjectPlanStatus {
    READY,
    CAPABILITY_UNAVAILABLE,
    CAPABILITY_UNKNOWN,
    INVALID_MANIFEST,
    NOT_COMPOSE,
}

data class ComposePortBinding(
    val raw: String,
    val published: Int? = null,
    val target: Int? = null,
    val protocol: String? = null,
)

data class ComposeServicePlan(
    val name: String,
    val image: String?,
    val buildContext: String?,
    val dependsOn: List<String>,
    val ports: List<ComposePortBinding>,
)

data class ComposeProjectPlan(
    val status: ComposeProjectPlanStatus,
    val manifestPath: String?,
    val services: List<ComposeServicePlan>,
    val containerAvailability: CapabilityAvailability,
    val issues: List<String>,
) {
    val isComposeProject: Boolean
        get() = status != ComposeProjectPlanStatus.NOT_COMPOSE
}

object ComposeProjectDetector {
    private val manifestPreference = listOf(
        "compose.yaml",
        "compose.yml",
        "docker-compose.yaml",
        "docker-compose.yml",
    )

    fun primaryManifest(relativePaths: Collection<String>): String? {
        val rootFiles = relativePaths
            .asSequence()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() && '/' !in it }
            .associateBy { it.lowercase() }
        return manifestPreference.firstNotNullOfOrNull { preferred -> rootFiles[preferred] }
    }
}

object ComposeProjectPlanner {
    fun plan(
        relativePaths: Collection<String>,
        manifestText: String?,
        containerAvailability: CapabilityAvailability,
    ): ComposeProjectPlan {
        val manifest = ComposeProjectDetector.primaryManifest(relativePaths)
            ?: return ComposeProjectPlan(
                status = ComposeProjectPlanStatus.NOT_COMPOSE,
                manifestPath = null,
                services = emptyList(),
                containerAvailability = containerAvailability,
                issues = emptyList(),
            )

        if (manifestText.isNullOrBlank()) {
            return invalid(manifest, containerAvailability, "Compose manifest is empty or unreadable")
        }

        val root = runCatching {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(manifestText)
        }.getOrElse { error ->
            return invalid(
                manifest,
                containerAvailability,
                "Compose manifest YAML is invalid: " + (error.message ?: error.javaClass.simpleName),
            )
        }

        val rootMap = stringMap(root)
            ?: return invalid(manifest, containerAvailability, "Compose manifest root must be a mapping")
        val servicesMap = stringMap(rootMap["services"])
            ?: return invalid(manifest, containerAvailability, "Compose manifest must define services")
        if (servicesMap.isEmpty()) {
            return invalid(manifest, containerAvailability, "Compose manifest must define at least one service")
        }

        val issues = mutableListOf<String>()
        val services = servicesMap.mapNotNull { (name, rawService) ->
            if (name.isBlank()) {
                issues += "Compose service name must not be blank"
                return@mapNotNull null
            }
            val service = stringMap(rawService)
            if (service == null) {
                issues += "Compose service '$name' must be a mapping"
                return@mapNotNull null
            }
            val image = service["image"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val buildContext = when (val build = service["build"]) {
                is String -> build.trim().takeIf { it.isNotBlank() }
                is Map<*, *> -> stringMap(build)?.get("context")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                else -> null
            }
            val dependsOn = parseDependsOn(service["depends_on"])
            val ports = parsePorts(service["ports"], name, issues)
            ComposeServicePlan(
                name = name,
                image = image,
                buildContext = buildContext,
                dependsOn = dependsOn,
                ports = ports,
            )
        }.sortedBy { it.name }

        if (services.isEmpty()) {
            return invalid(manifest, containerAvailability, issues.firstOrNull() ?: "No valid Compose services")
        }

        val names = services.map { it.name }.toSet()
        services.forEach { service ->
            service.dependsOn.filter { it !in names }.forEach { missing ->
                issues += "Compose service '" + service.name + "' depends on unknown service '" + missing + "'"
            }
        }
        if (issues.isNotEmpty()) {
            return ComposeProjectPlan(
                status = ComposeProjectPlanStatus.INVALID_MANIFEST,
                manifestPath = manifest,
                services = services,
                containerAvailability = containerAvailability,
                issues = issues.distinct(),
            )
        }

        val status = when (containerAvailability) {
            CapabilityAvailability.AVAILABLE -> ComposeProjectPlanStatus.READY
            CapabilityAvailability.UNAVAILABLE -> ComposeProjectPlanStatus.CAPABILITY_UNAVAILABLE
            CapabilityAvailability.UNKNOWN -> ComposeProjectPlanStatus.CAPABILITY_UNKNOWN
        }
        return ComposeProjectPlan(
            status = status,
            manifestPath = manifest,
            services = services,
            containerAvailability = containerAvailability,
            issues = emptyList(),
        )
    }

    private fun parseDependsOn(value: Any?): List<String> = when (value) {
        is Collection<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()
        is Map<*, *> -> value.keys.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()
        else -> emptyList()
    }.sorted()

    private fun parsePorts(
        value: Any?,
        serviceName: String,
        issues: MutableList<String>,
    ): List<ComposePortBinding> {
        if (value == null) return emptyList()
        val entries = value as? Collection<*> ?: run {
            issues += "Compose service '$serviceName' ports must be a list"
            return emptyList()
        }
        return entries.mapNotNull { entry ->
            when (entry) {
                is String, is Number -> parseShortPort(entry.toString())
                is Map<*, *> -> {
                    val map = stringMap(entry).orEmpty()
                    val rawTarget = map["target"]?.toString()?.trim()
                    val rawPublished = map["published"]?.toString()?.trim()
                    if (rawTarget.isNullOrBlank()) {
                        issues += "Compose service '$serviceName' long port syntax requires target"
                        null
                    } else {
                        ComposePortBinding(
                            raw = entry.toString(),
                            published = rawPublished?.toIntOrNull(),
                            target = rawTarget.toIntOrNull(),
                            protocol = map["protocol"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
                        )
                    }
                }
                else -> {
                    issues += "Compose service '$serviceName' contains an unsupported port entry"
                    null
                }
            }
        }
    }

    private fun parseShortPort(rawValue: String): ComposePortBinding {
        val raw = rawValue.trim()
        val protocolSplit = raw.split('/', limit = 2)
        val address = protocolSplit[0]
        val protocol = protocolSplit.getOrNull(1)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val targetOnly = Regex("""^(\d+)$""").matchEntire(address)
        if (targetOnly != null) {
            return ComposePortBinding(
                raw = raw,
                target = targetOnly.groupValues[1].toIntOrNull(),
                protocol = protocol,
            )
        }
        val publishedTarget = Regex("""^(?:[^:]+:)?(\d+):(\d+)$""").matchEntire(address)
        return if (publishedTarget != null) {
            ComposePortBinding(
                raw = raw,
                published = publishedTarget.groupValues[1].toIntOrNull(),
                target = publishedTarget.groupValues[2].toIntOrNull(),
                protocol = protocol,
            )
        } else {
            ComposePortBinding(raw = raw, protocol = protocol)
        }
    }

    private fun invalid(
        manifest: String,
        availability: CapabilityAvailability,
        issue: String,
    ): ComposeProjectPlan = ComposeProjectPlan(
        status = ComposeProjectPlanStatus.INVALID_MANIFEST,
        manifestPath = manifest,
        services = emptyList(),
        containerAvailability = availability,
        issues = listOf(issue),
    )

    private fun stringMap(value: Any?): Map<String, Any?>? {
        val raw = value as? Map<*, *> ?: return null
        val result = linkedMapOf<String, Any?>()
        raw.forEach { (key, item) ->
            val text = key as? String ?: return@forEach
            result[text] = item
        }
        return result
    }
}
