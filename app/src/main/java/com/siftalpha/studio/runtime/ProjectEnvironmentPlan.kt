package com.siftalpha.studio.runtime

import com.siftalpha.studio.siftalphax.EmbeddedPythonRequirementParserV1
import com.siftalpha.studio.siftalphax.EmbeddedPythonRuntimeCompatibilityV1
import java.security.MessageDigest
import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * R47 Environment Detection / Environment Plan contract.
 *
 * Detection is read-only: it consumes an immutable imported-project snapshot plus bounded root
 * metadata, then produces facts. Planning turns those facts into a deterministic machine-readable
 * contract. Neither layer installs packages, executes third-party project code, or mutates source.
 */
enum class EnvironmentDependencySource(val wireValue: String) {
    REQUIREMENTS_TXT("requirements.txt"),
    PYPROJECT_TOML("pyproject.toml"),
    NONE("none"),
    UNSUPPORTED("unsupported"),
}

enum class EnvironmentBackend(val wireValue: String) {
    EMBEDDED_CPYTHON("embedded_cpython"),
    INTERNAL_ALPINE("internal_alpine"),
    EXTERNAL_PROVIDER("external_provider"),
}

enum class EnvironmentBuildStep(val wireValue: String) {
    VALIDATE_PLAN("validate_plan"),
    ACQUIRE_RUNTIME("acquire_runtime"),
    CREATE_ENVIRONMENT("create_environment"),
    NODE_INSTALL("node_install"),
    NODE_BUILD("node_build"),
    PYTHON_INSTALL("python_install"),
    VERIFY_ENVIRONMENT("verify_environment"),
    COMMIT_ENVIRONMENT("commit_environment"),
}

enum class EnvironmentIssueKind {
    RUNTIME_SELECTION_AMBIGUOUS,
    RUNTIME_UNSUPPORTED,
    PYPROJECT_INVALID,
    PROJECT_SOURCE_INCOMPLETE,
    PROJECT_REFERENCE_UNSAFE,
    PYTHON_DEPENDENCY_MANIFEST_UNSUPPORTED,
    NODE_PACKAGE_MANAGER_UNSUPPORTED,
    EMBEDDED_CPYTHON_INCOMPATIBLE,
    NO_PREPARE_BACKEND,
}

data class EnvironmentDetectionIssue(
    val kind: EnvironmentIssueKind,
    val detail: String,
    val blocking: Boolean,
)

data class ProjectEnvironmentDetection(
    val projectFingerprint: String,
    val selection: ProjectRuntimeExecutionPlanner.Selection,
    val primaryRuntime: RuntimeKind?,
    val supplementalRuntimes: List<RuntimeKind>,
    val dependencySource: EnvironmentDependencySource,
    val directDependencyCount: Int,
    val pythonRequiresVersion: String?,
    val pythonOptionalDependencyGroups: List<String>,
    val viteComponentCount: Int,
    val declaredEntry: String?,
    val declaredRun: String?,
    val embeddedCpythonEligible: Boolean,
    val internalAlpineEligible: Boolean,
    val issues: List<EnvironmentDetectionIssue>,
) {
    val blockingIssues: List<EnvironmentDetectionIssue>
        get() = issues.filter { it.blocking }
}

data class ProjectEnvironmentPlan(
    val schemaVersion: Int,
    val planId: String,
    val detection: ProjectEnvironmentDetection,
    val backendCandidates: List<EnvironmentBackend>,
    val preferredBackend: EnvironmentBackend?,
    val buildSteps: List<EnvironmentBuildStep>,
    val pythonInstallExtras: List<String>,
) {
    val readyToPrepare: Boolean
        get() = detection.blockingIssues.isEmpty() && preferredBackend != null

    fun supports(backend: EnvironmentBackend): Boolean = backend in backendCandidates

    fun diagnosticLines(): List<String> = buildList {
        add("SIFTALPHA_ENV_DETECTION=COMPLETE")
        add("SIFTALPHA_ENV_PLAN_SCHEMA=" + schemaVersion)
        add("SIFTALPHA_ENV_PLAN_ID=" + planId)
        add("SIFTALPHA_ENV_PROJECT_FINGERPRINT=" + detection.projectFingerprint)
        add("SIFTALPHA_ENV_PRIMARY_RUNTIME=" + (detection.primaryRuntime?.id ?: "unresolved"))
        add(
            "SIFTALPHA_ENV_SUPPLEMENTAL_RUNTIMES=" +
                detection.supplementalRuntimes.joinToString(",") { it.id },
        )
        add("SIFTALPHA_ENV_DEPENDENCY_SOURCE=" + detection.dependencySource.wireValue)
        add("SIFTALPHA_ENV_DIRECT_DEPENDENCIES=" + detection.directDependencyCount)
        add("SIFTALPHA_ENV_PYTHON_REQUIRES=" + detection.pythonRequiresVersion.orEmpty())
        add(
            "SIFTALPHA_ENV_PYTHON_OPTIONAL_GROUPS=" +
                detection.pythonOptionalDependencyGroups.joinToString(","),
        )
        add("SIFTALPHA_ENV_PYTHON_INSTALL_EXTRAS=" + pythonInstallExtras.joinToString(","))
        add("SIFTALPHA_ENV_VITE_COMPONENTS=" + detection.viteComponentCount)
        add(
            "SIFTALPHA_ENV_BACKEND_CANDIDATES=" +
                backendCandidates.joinToString(",") { it.wireValue },
        )
        add("SIFTALPHA_ENV_BACKEND_PREFERRED=" + (preferredBackend?.wireValue ?: "none"))
        add("SIFTALPHA_ENV_BUILD_STEPS=" + buildSteps.joinToString(",") { it.wireValue })
        add("SIFTALPHA_ENV_PLAN_READY=" + if (readyToPrepare) "1" else "0")
        detection.issues.take(MAX_DIAGNOSTIC_ISSUES).forEachIndexed { index, issue ->
            val safeDetail = issue.detail.replace('\n', ' ').replace('\r', ' ').take(240)
            add(
                "SIFTALPHA_ENV_ISSUE_" + (index + 1) + "=" +
                    issue.kind.name + ":" + (if (issue.blocking) "BLOCKING" else "INFO") +
                    ":" + safeDetail,
            )
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        private const val MAX_DIAGNOSTIC_ISSUES = 12
    }
}

enum class EnvironmentCompatibilityResolutionState(val wireValue: String) {
    STATIC_ONLY("static_only"),
    EMBEDDED_CPYTHON_RESOLVED("embedded_cpython_resolved"),
    EMBEDDED_CPYTHON_INCOMPATIBLE("embedded_cpython_incompatible"),
    LOOKUP_DEFERRED("lookup_deferred"),
}

data class ProjectEnvironmentResolution(
    val plan: ProjectEnvironmentPlan,
    val state: EnvironmentCompatibilityResolutionState,
    val selectedBackend: EnvironmentBackend?,
    val embeddedDependencyFingerprint: String? = null,
    val embeddedDependencyCount: Int? = null,
    val detail: String? = null,
) {
    fun diagnosticLines(): List<String> = buildList {
        addAll(plan.diagnosticLines())
        add("SIFTALPHA_ENV_COMPATIBILITY_RESOLUTION=" + state.wireValue)
        add("SIFTALPHA_ENV_SELECTED_BACKEND=" + (selectedBackend?.wireValue ?: "none"))
        embeddedDependencyFingerprint?.let {
            add("SIFTALPHA_ENV_EMBEDDED_DEPENDENCY_FINGERPRINT=" + it)
        }
        embeddedDependencyCount?.let {
            add("SIFTALPHA_ENV_EMBEDDED_DEPENDENCY_COUNT=" + it)
        }
        detail?.takeIf { it.isNotBlank() }?.let {
            add(
                "SIFTALPHA_ENV_COMPATIBILITY_DETAIL=" +
                    it.replace('\n', ' ').replace('\r', ' ').take(240),
            )
        }
    }
}

object ProjectEnvironmentResolutionPlanner {
    fun static(plan: ProjectEnvironmentPlan): ProjectEnvironmentResolution =
        ProjectEnvironmentResolution(
            plan = plan,
            state = EnvironmentCompatibilityResolutionState.STATIC_ONLY,
            selectedBackend = plan.preferredBackend,
        )

    fun embeddedResolved(
        plan: ProjectEnvironmentPlan,
        resolvedFingerprint: String,
        packageCount: Int,
    ): ProjectEnvironmentResolution = ProjectEnvironmentResolution(
        plan = plan,
        state = EnvironmentCompatibilityResolutionState.EMBEDDED_CPYTHON_RESOLVED,
        selectedBackend = EnvironmentBackend.EMBEDDED_CPYTHON
            .takeIf { plan.supports(it) },
        embeddedDependencyFingerprint = resolvedFingerprint,
        embeddedDependencyCount = packageCount,
    )

    fun embeddedIncompatible(
        plan: ProjectEnvironmentPlan,
        detail: String,
    ): ProjectEnvironmentResolution = ProjectEnvironmentResolution(
        plan = plan,
        state = EnvironmentCompatibilityResolutionState.EMBEDDED_CPYTHON_INCOMPATIBLE,
        selectedBackend = plan.backendCandidates.firstOrNull {
            it != EnvironmentBackend.EMBEDDED_CPYTHON
        },
        detail = detail,
    )

    fun lookupDeferred(
        plan: ProjectEnvironmentPlan,
        detail: String,
    ): ProjectEnvironmentResolution = ProjectEnvironmentResolution(
        plan = plan,
        state = EnvironmentCompatibilityResolutionState.LOOKUP_DEFERRED,
        selectedBackend = plan.preferredBackend,
        detail = detail,
    )
}

data class ProjectEnvironmentDetectionInput(
    val relativePaths: Collection<String>,
    val declaredType: String? = null,
    val declaredEntry: String? = null,
    val declaredRun: String? = null,
    val requirementsText: String? = null,
    val pyprojectText: String? = null,
)

data class ProjectEnvironmentCapabilities(
    val embeddedCpythonAvailable: Boolean,
    val internalAlpineAvailable: Boolean,
    val externalProviderAvailable: Boolean,
)

/**
 * Static project understanding. This layer is side-effect free and does not access package indexes.
 * Backend-specific transitive dependency resolution remains a later compatibility layer.
 */
object ProjectEnvironmentDetector {

    fun detect(input: ProjectEnvironmentDetectionInput): ProjectEnvironmentDetection {
        val normalizedPaths = input.relativePaths
            .asSequence()
            .map(::normalizePath)
            .filter { it.isNotBlank() }
            .toSortedSet()

        val selection = ProjectRuntimeExecutionPlanner.select(
            relativePaths = normalizedPaths,
            declaredType = input.declaredType,
        )
        val resolved = selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
        val issues = mutableListOf<EnvironmentDetectionIssue>()
        when (selection) {
            is ProjectRuntimeExecutionPlanner.Selection.Ambiguous -> issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.RUNTIME_SELECTION_AMBIGUOUS,
                selection.candidates.joinToString(",") { it.id },
                blocking = true,
            )
            is ProjectRuntimeExecutionPlanner.Selection.Unsupported -> issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.RUNTIME_UNSUPPORTED,
                "No authoritative supported runtime evidence was found",
                blocking = true,
            )
            is ProjectRuntimeExecutionPlanner.Selection.Resolved -> Unit
        }

        val parsedPyproject = parsePyproject(input.pyprojectText, issues)
        val projectTable = parsedPyproject?.get("project") as? TomlTable
        val requiresPythonValue = projectTable?.get("requires-python")
        val requiresPython = (requiresPythonValue as? String)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (requiresPythonValue != null && requiresPythonValue !is String) {
            issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.PYPROJECT_INVALID,
                "project.requires-python must be a string",
                blocking = true,
            )
        }

        if (projectTable != null) {
            staticProjectReferences(projectTable).forEach { reference ->
                val safe = safeProjectReference(reference.path)
                if (safe == null) {
                    issues += EnvironmentDetectionIssue(
                        EnvironmentIssueKind.PROJECT_REFERENCE_UNSAFE,
                        reference.source + "=" + reference.path,
                        blocking = true,
                    )
                } else if (safe !in normalizedPaths) {
                    issues += EnvironmentDetectionIssue(
                        EnvironmentIssueKind.PROJECT_SOURCE_INCOMPLETE,
                        reference.source + " requires " + safe,
                        blocking = true,
                    )
                }
            }
        }

        val activeRequirements = input.requirementsText
            ?.lineSequence()
            ?.map { stripRequirementComment(it).trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()
        val unsupportedManifest = normalizedPaths.firstOrNull { path ->
            '/' !in path && path.lowercase() in UNSUPPORTED_PYTHON_MANIFESTS
        }
        val dependencySource = when {
            activeRequirements.isNotEmpty() -> EnvironmentDependencySource.REQUIREMENTS_TXT
            input.pyprojectText != null -> EnvironmentDependencySource.PYPROJECT_TOML
            unsupportedManifest != null -> EnvironmentDependencySource.UNSUPPORTED
            else -> EnvironmentDependencySource.NONE
        }
        if (dependencySource == EnvironmentDependencySource.UNSUPPORTED) {
            issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.PYTHON_DEPENDENCY_MANIFEST_UNSUPPORTED,
                unsupportedManifest.orEmpty(),
                blocking = true,
            )
        }

        val pythonOptionalDependencyGroups =
            optionalDependencyGroups(projectTable, issues)

        val directDependencyCount = when (dependencySource) {
            EnvironmentDependencySource.REQUIREMENTS_TXT ->
                activeRequirements.count { !it.startsWith("-") }
            EnvironmentDependencySource.PYPROJECT_TOML ->
                directPyprojectDependencyCount(projectTable, issues)
            EnvironmentDependencySource.NONE,
            EnvironmentDependencySource.UNSUPPORTED,
            -> 0
        }

        val viteComponents = findViteComponents(normalizedPaths)
        val nodeRelevant = resolved?.primary == RuntimeKind.NODE_JS ||
            resolved?.supplemental?.contains(RuntimeKind.NODE_JS) == true
        if (nodeRelevant) {
            val unsupportedManager = unsupportedNodeManager(normalizedPaths, viteComponents)
            if (unsupportedManager != null) {
                issues += EnvironmentDetectionIssue(
                    EnvironmentIssueKind.NODE_PACKAGE_MANAGER_UNSUPPORTED,
                    unsupportedManager,
                    blocking = true,
                )
            }
        }

        val blocking = issues.any { it.blocking }
        var embeddedCpythonEligible =
            resolved?.primary == RuntimeKind.PYTHON &&
                viteComponents.isEmpty() &&
                !blocking &&
                dependencySource != EnvironmentDependencySource.UNSUPPORTED

        if (embeddedCpythonEligible) {
            val requirementsForInternal = input.requirementsText
                ?.takeIf { activeRequirements.isNotEmpty() }
            val compatibility = runCatching {
                val parsed = EmbeddedPythonRequirementParserV1.fromProjectFiles(
                    requirementsText = requirementsForInternal,
                    pyprojectText = input.pyprojectText,
                )
                parsed.projectRequiresPython?.let { requirement ->
                    check(EmbeddedPythonRuntimeCompatibilityV1.requiresPython(requirement).matches) {
                        "requires-python $requirement does not match Embedded CPython"
                    }
                }
            }
            if (compatibility.isFailure) {
                embeddedCpythonEligible = false
                issues += EnvironmentDetectionIssue(
                    EnvironmentIssueKind.EMBEDDED_CPYTHON_INCOMPATIBLE,
                    compatibility.exceptionOrNull()?.message.orEmpty().ifBlank {
                        "Embedded CPython compatibility could not be proven"
                    },
                    blocking = false,
                )
            }
        }

        val internalAlpineEligible =
            resolved?.primary == RuntimeKind.PYTHON &&
                dependencySource != EnvironmentDependencySource.UNSUPPORTED &&
                issues.none { it.blocking }

        val fingerprint = environmentInputFingerprint(
            requirementsText = input.requirementsText,
            pyprojectText = input.pyprojectText,
        )

        return ProjectEnvironmentDetection(
            projectFingerprint = fingerprint,
            selection = selection,
            primaryRuntime = resolved?.primary,
            supplementalRuntimes = resolved?.supplemental.orEmpty(),
            dependencySource = dependencySource,
            directDependencyCount = directDependencyCount,
            pythonRequiresVersion = requiresPython,
            pythonOptionalDependencyGroups = pythonOptionalDependencyGroups,
            viteComponentCount = viteComponents.size,
            declaredEntry = input.declaredEntry,
            declaredRun = input.declaredRun,
            embeddedCpythonEligible = embeddedCpythonEligible,
            internalAlpineEligible = internalAlpineEligible,
            issues = issues.toList(),
        )
    }

    private fun parsePyproject(
        text: String?,
        issues: MutableList<EnvironmentDetectionIssue>,
    ) = text?.let {
        val parsed = Toml.parse(it)
        if (parsed.hasErrors()) {
            issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.PYPROJECT_INVALID,
                parsed.errors().take(3).joinToString(" | ") { error -> error.toString() },
                blocking = true,
            )
            null
        } else {
            parsed
        }
    }

    private data class StaticReference(val source: String, val path: String)

    private fun staticProjectReferences(project: TomlTable): List<StaticReference> = buildList {
        when (val readme = project.get("readme")) {
            is String -> readme.takeIf { it.isNotBlank() }?.let {
                add(StaticReference("project.readme", it))
            }
            is TomlTable -> (readme.get("file") as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(StaticReference("project.readme.file", it)) }
        }
        val license = project.get("license")
        if (license is TomlTable) {
            (license.get("file") as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(StaticReference("project.license.file", it)) }
        }
    }

    private fun optionalDependencyGroups(
        project: TomlTable?,
        issues: MutableList<EnvironmentDetectionIssue>,
    ): List<String> {
        val optional = project?.get("optional-dependencies") ?: return emptyList()
        if (optional !is TomlTable) {
            issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.PYPROJECT_INVALID,
                "project.optional-dependencies must be a table",
                blocking = true,
            )
            return emptyList()
        }
        return optional.keySet().sorted()
    }

    private fun directPyprojectDependencyCount(
        project: TomlTable?,
        issues: MutableList<EnvironmentDetectionIssue>,
    ): Int {
        val dependencies = project?.get("dependencies") ?: return 0
        if (dependencies !is org.tomlj.TomlArray) {
            issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.PYPROJECT_INVALID,
                "project.dependencies must be an array",
                blocking = true,
            )
            return 0
        }
        return dependencies.size()
    }

    private fun safeProjectReference(raw: String): String? {
        val value = raw.trim().replace('\\', '/')
        if (value.isBlank() || value.startsWith('/') || URI_SCHEME.matches(value)) return null
        val segments = value.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun findViteComponents(paths: Set<String>): Set<String> {
        val packageDirectories = paths
            .filter { it.substringAfterLast('/').equals("package.json", ignoreCase = true) }
            .map { it.substringBeforeLast('/', "") }
        return packageDirectories.filterTo(linkedSetOf()) { directory ->
            VITE_CONFIG_NAMES.any { config ->
                val candidate = if (directory.isBlank()) config else "$directory/$config"
                candidate in paths
            }
        }
    }

    private fun unsupportedNodeManager(
        paths: Set<String>,
        viteComponents: Set<String>,
    ): String? {
        val relevantDirectories = if (viteComponents.isNotEmpty()) {
            viteComponents
        } else {
            setOf("")
        }
        relevantDirectories.forEach { directory ->
            val prefix = if (directory.isBlank()) "" else "$directory/"
            if (prefix + "pnpm-lock.yaml" in paths) return prefix + "pnpm-lock.yaml"
            if (prefix + "yarn.lock" in paths) return prefix + "yarn.lock"
        }
        return null
    }

    /**
     * Stable environment-input identity.
     *
     * Runtime-generated project files (for example __pycache__, logs, results or databases) must
     * never invalidate an already prepared environment. Runtime selection and structural checks
     * still consume the complete path snapshot above; their derived facts/issues are separately
     * encoded into ProjectEnvironmentPlanner.planId().
     */
    private fun environmentInputFingerprint(
        requirementsText: String?,
        pyprojectText: String?,
    ): String {
        val canonical = buildString {
            append("schema=2\n")
            append("requirements\n").append(normalizeText(requirementsText)).append('\n')
            append("pyproject\n").append(normalizeText(pyprojectText)).append('\n')
        }
        return "sha256:" + sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun normalizeText(value: String?): String =
        value.orEmpty().replace("\r\n", "\n").replace("\r", "\n")

    private fun stripRequirementComment(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("#")) return ""
        val comment = value.indexOf(" #")
        return if (comment >= 0) value.substring(0, comment) else value
    }

    private fun normalizePath(raw: String): String =
        raw.replace('\\', '/').trim().trim('/')

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*")
    private val VITE_CONFIG_NAMES = setOf(
        "vite.config.ts",
        "vite.config.js",
        "vite.config.mts",
        "vite.config.mjs",
        "vite.config.cjs",
    )
    private val UNSUPPORTED_PYTHON_MANIFESTS = setOf(
        "setup.py",
        "setup.cfg",
        "pipfile",
        "pipfile.lock",
        "poetry.lock",
    )
}

object ProjectEnvironmentPlanner {

    fun plan(
        detection: ProjectEnvironmentDetection,
        capabilities: ProjectEnvironmentCapabilities,
    ): ProjectEnvironmentPlan {
        val issues = detection.issues.toMutableList()
        val candidates = buildList {
            when (detection.primaryRuntime) {
                RuntimeKind.PYTHON -> {
                    if (detection.embeddedCpythonEligible && capabilities.embeddedCpythonAvailable) {
                        add(EnvironmentBackend.EMBEDDED_CPYTHON)
                    }
                    if (detection.internalAlpineEligible && capabilities.internalAlpineAvailable) {
                        add(EnvironmentBackend.INTERNAL_ALPINE)
                    }
                    if (
                        detection.blockingIssues.isEmpty() &&
                        capabilities.externalProviderAvailable
                    ) {
                        add(EnvironmentBackend.EXTERNAL_PROVIDER)
                    }
                }
                RuntimeKind.NODE_JS -> {
                    if (
                        detection.blockingIssues.isEmpty() &&
                        capabilities.externalProviderAvailable
                    ) {
                        add(EnvironmentBackend.EXTERNAL_PROVIDER)
                    }
                }
                else -> Unit
            }
        }.distinct()

        if (detection.primaryRuntime != null && candidates.isEmpty() && issues.none { it.blocking }) {
            issues += EnvironmentDetectionIssue(
                EnvironmentIssueKind.NO_PREPARE_BACKEND,
                "No available backend can execute the detected environment plan",
                blocking = true,
            )
        }

        val effectiveDetection = detection.copy(issues = issues)
        val steps = buildSteps(effectiveDetection)
        val pythonInstallExtras = buildPythonInstallExtras(effectiveDetection)
        val preferred = candidates.firstOrNull()
        val planId = planId(effectiveDetection, candidates, steps, pythonInstallExtras)
        return ProjectEnvironmentPlan(
            schemaVersion = ProjectEnvironmentPlan.CURRENT_SCHEMA_VERSION,
            planId = planId,
            detection = effectiveDetection,
            backendCandidates = candidates,
            preferredBackend = preferred,
            buildSteps = steps,
            pythonInstallExtras = pythonInstallExtras,
        )
    }

    private fun buildPythonInstallExtras(
        detection: ProjectEnvironmentDetection,
    ): List<String> = buildList {
        if (
            detection.primaryRuntime == RuntimeKind.PYTHON &&
            detection.viteComponentCount > 0 &&
            "web" in detection.pythonOptionalDependencyGroups
        ) {
            add("web")
        }
    }

    private fun buildSteps(detection: ProjectEnvironmentDetection): List<EnvironmentBuildStep> {
        if (detection.blockingIssues.isNotEmpty() || detection.primaryRuntime == null) {
            return listOf(EnvironmentBuildStep.VALIDATE_PLAN)
        }
        return buildList {
            add(EnvironmentBuildStep.VALIDATE_PLAN)
            add(EnvironmentBuildStep.ACQUIRE_RUNTIME)
            add(EnvironmentBuildStep.CREATE_ENVIRONMENT)
            val nodeNeeded = detection.primaryRuntime == RuntimeKind.NODE_JS ||
                (
                    RuntimeKind.NODE_JS in detection.supplementalRuntimes &&
                        detection.viteComponentCount > 0
                )
            if (nodeNeeded) {
                add(EnvironmentBuildStep.NODE_INSTALL)
                if (
                    detection.primaryRuntime != RuntimeKind.NODE_JS ||
                    detection.viteComponentCount > 0
                ) {
                    add(EnvironmentBuildStep.NODE_BUILD)
                }
            }
            if (detection.primaryRuntime == RuntimeKind.PYTHON) {
                add(EnvironmentBuildStep.PYTHON_INSTALL)
            }
            add(EnvironmentBuildStep.VERIFY_ENVIRONMENT)
            add(EnvironmentBuildStep.COMMIT_ENVIRONMENT)
        }
    }

    private fun planId(
        detection: ProjectEnvironmentDetection,
        candidates: List<EnvironmentBackend>,
        steps: List<EnvironmentBuildStep>,
        pythonInstallExtras: List<String>,
    ): String {
        val canonical = buildString {
            append("schema=").append(ProjectEnvironmentPlan.CURRENT_SCHEMA_VERSION).append('\n')
            append("project=").append(detection.projectFingerprint).append('\n')
            append("primary=").append(detection.primaryRuntime?.id.orEmpty()).append('\n')
            append("supplemental=")
                .append(detection.supplementalRuntimes.joinToString(",") { it.id })
                .append('\n')
            append("dependency=").append(detection.dependencySource.wireValue).append('\n')
            append("vite_components=").append(detection.viteComponentCount).append('\n')
            append("python=").append(detection.pythonRequiresVersion.orEmpty()).append('\n')
            append("python_optional_groups=")
                .append(detection.pythonOptionalDependencyGroups.joinToString(","))
                .append('\n')
            append("python_install_extras=").append(pythonInstallExtras.joinToString(",")).append('\n')
            append("backends=").append(candidates.joinToString(",") { it.wireValue }).append('\n')
            append("steps=").append(steps.joinToString(",") { it.wireValue }).append('\n')
            detection.issues.forEach {
                append("issue=").append(it.kind.name).append(':')
                    .append(if (it.blocking) '1' else '0').append(':')
                    .append(it.detail).append('\n')
            }
        }
        return "sha256:" + MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
