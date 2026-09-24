package com.siftalpha.macos

import com.siftalpha.core.environment.EnvironmentPreparationStep
import com.siftalpha.core.environment.ProjectEnvironmentNeedPolicy
import com.siftalpha.core.environment.ProjectEnvironmentNeeds
import com.siftalpha.studio.runtime.NodePackageManagerPolicy
import com.siftalpha.studio.runtime.ProjectRuntimeExecutionPlanner
import com.siftalpha.studio.runtime.RuntimeKind
import java.io.File
import org.tomlj.Toml
import org.tomlj.TomlTable

data class MacProjectSnapshot(
    val projectId: String,
    val rootPath: String,
    val relativePaths: List<String>,
    val requirementsText: String?,
    val pyprojectText: String?,
    val packageJsonText: String?,
)

enum class MacProjectPlanStatus {
    READY_TO_PREPARE,
    RUNTIME_MISSING,
    BLOCKED,
}

data class MacProjectEnvironmentPlan(
    val projectId: String,
    val status: MacProjectPlanStatus,
    val needs: ProjectEnvironmentNeeds?,
    val preparationSteps: List<EnvironmentPreparationStep>,
    val runtimeExecutables: Map<RuntimeKind, String>,
    val issues: List<String>,
) {
    fun diagnosticLines(): List<String> = buildList {
        add("SIFTALPHA_M4_IMPORT=PASS")
        add("SIFTALPHA_M4_PROJECT_ID=" + projectId)
        add("SIFTALPHA_M4_PLAN_STATUS=" + status)
        add("SIFTALPHA_M4_PRIMARY_RUNTIME=" + (needs?.primaryRuntime?.id ?: "unresolved"))
        add(
            "SIFTALPHA_M4_SUPPLEMENTAL_RUNTIMES=" +
                needs?.supplementalRuntimes.orEmpty().joinToString(",") { it.id },
        )
        add("SIFTALPHA_M4_DIRECT_DEPENDENCIES=" + (needs?.directDependencyCount ?: 0))
        add("SIFTALPHA_M4_PYTHON_REQUIRES=" + needs?.pythonRequiresVersion.orEmpty())
        add("SIFTALPHA_M4_PREPARE_STEPS=" + preparationSteps.joinToString(",") { it.name })
        runtimeExecutables.toSortedMap(compareBy { it.id }).forEach { (kind, path) ->
            add("SIFTALPHA_M4_RUNTIME=" + kind.id + "|" + path)
        }
        issues.forEachIndexed { index, issue ->
            add("SIFTALPHA_M4_ISSUE_" + (index + 1) + "=" + issue.replace('\n', ' ').take(240))
        }
    }
}

class MacProjectSnapshotBuilder(
    private val filesystem: MacProjectFilesystem,
) {
    fun build(
        project: MacImportedProject,
        maxEntries: Int = 5_000,
        maxDepth: Int = 12,
    ): MacProjectSnapshot {
        require(maxEntries > 0)
        require(maxDepth > 0)
        val root = filesystem.rootFile(project.projectId)
        val paths = mutableListOf<String>()
        var count = 0

        fun walk(directory: File, depth: Int) {
            if (depth > maxDepth || count >= maxEntries) return
            directory.listFiles()
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .forEach { raw ->
                    if (count >= maxEntries) return@forEach
                    val canonical = runCatching { raw.canonicalFile }.getOrNull() ?: return@forEach
                    if (!canonical.toPath().startsWith(root.toPath())) return@forEach
                    val relative = root.toPath().relativize(canonical.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                    if (relative.isBlank()) return@forEach
                    val firstSegment = relative.substringBefore('/').lowercase()
                    if (firstSegment in IGNORED_ROOT_DIRECTORIES) return@forEach
                    paths += relative
                    count++
                    if (canonical.isDirectory) walk(canonical, depth + 1)
                }
        }

        walk(root, 0)

        return MacProjectSnapshot(
            projectId = project.projectId,
            rootPath = root.absolutePath,
            relativePaths = paths.sorted(),
            requirementsText = readRootText(root, "requirements.txt"),
            pyprojectText = readRootText(root, "pyproject.toml"),
            packageJsonText = readRootText(root, "package.json"),
        )
    }

    private fun readRootText(root: File, name: String): String? {
        val file = File(root, name)
        if (!file.isFile || file.length() > MAX_METADATA_BYTES) return null
        return file.readText(Charsets.UTF_8)
    }

    companion object {
        private const val MAX_METADATA_BYTES = 512 * 1024L
        private val IGNORED_ROOT_DIRECTORIES = setOf(
            ".git", ".venv", "venv", "node_modules", "__pycache__", "dist", "build", ".gradle", "target",
        )
    }
}

object MacProjectWorkflowPlanner {
    fun plan(
        snapshot: MacProjectSnapshot,
        hostTools: List<MacHostToolSnapshot>,
        managedPythonExecutable: String? = null,
    ): MacProjectEnvironmentPlan {
        val selection = ProjectRuntimeExecutionPlanner.select(snapshot.relativePaths)
        if (selection !is ProjectRuntimeExecutionPlanner.Selection.Resolved) {
            val detail = when (selection) {
                is ProjectRuntimeExecutionPlanner.Selection.Ambiguous ->
                    "runtime selection is ambiguous: " + selection.candidates.joinToString(",") { it.id }
                is ProjectRuntimeExecutionPlanner.Selection.Unsupported ->
                    "no supported runtime evidence found"
                else -> "runtime selection unresolved"
            }
            return MacProjectEnvironmentPlan(
                projectId = snapshot.projectId,
                status = MacProjectPlanStatus.BLOCKED,
                needs = null,
                preparationSteps = listOf(EnvironmentPreparationStep.VALIDATE_PLAN),
                runtimeExecutables = emptyMap(),
                issues = listOf(detail),
            )
        }

        val primary = selection.primary
        val supplemental = selection.supplemental
        if (primary !in SUPPORTED_HOST_RUNTIMES || supplemental.any { it !in SUPPORTED_HOST_RUNTIMES }) {
            return MacProjectEnvironmentPlan(
                projectId = snapshot.projectId,
                status = MacProjectPlanStatus.BLOCKED,
                needs = ProjectEnvironmentNeeds(primary, supplemental, hasBlockingIssues = true),
                preparationSteps = listOf(EnvironmentPreparationStep.VALIDATE_PLAN),
                runtimeExecutables = emptyMap(),
                issues = listOf("M4 host workflow currently supports Python and Node.js projects only"),
            )
        }

        val issues = mutableListOf<String>()
        val pythonMetadata = parsePythonMetadata(snapshot.pyprojectText, issues)
        val requirementsCount = snapshot.requirementsText
            ?.lineSequence()
            ?.map { it.substringBefore('#').trim() }
            ?.count { it.isNotBlank() && !it.startsWith("-") }
            ?: 0

        if (
            primary == RuntimeKind.PYTHON &&
            snapshot.pyprojectText == null &&
            snapshot.requirementsText == null &&
            snapshot.relativePaths.any {
                it.substringAfterLast('/').lowercase() in UNSUPPORTED_PYTHON_MANIFESTS
            }
        ) {
            issues += "Python dependency manifest is not yet supported by M4 host workflow"
        }

        if (primary == RuntimeKind.NODE_JS || RuntimeKind.NODE_JS in supplemental) {
            when (val npm = NodePackageManagerPolicy.resolve(snapshot.relativePaths)) {
                is NodePackageManagerPolicy.Result.Unsupported ->
                    issues += "unsupported Node package manager: " + npm.manager
                is NodePackageManagerPolicy.Result.Ambiguous ->
                    issues += "ambiguous Node package manager: " + npm.evidence.joinToString(",")
                is NodePackageManagerPolicy.Result.Supported -> Unit
            }
        }

        val viteCount = snapshot.relativePaths.count {
            val name = it.substringAfterLast('/').lowercase()
            name == "vite.config.js" || name == "vite.config.ts" ||
                name == "vite.config.mjs" || name == "vite.config.cjs"
        }

        val directDependencies = when {
            requirementsCount > 0 -> requirementsCount
            pythonMetadata.directDependencyCount > 0 -> pythonMetadata.directDependencyCount
            else -> 0
        }

        val needs = ProjectEnvironmentNeeds(
            primaryRuntime = primary,
            supplementalRuntimes = supplemental,
            directDependencyCount = directDependencies,
            pythonRequiresVersion = pythonMetadata.requiresPython,
            pythonOptionalDependencyGroups = pythonMetadata.optionalGroups,
            viteComponentCount = viteCount,
            hasBlockingIssues = issues.isNotEmpty(),
        )

        val requiredKinds = buildSet {
            add(primary)
            addAll(supplemental)
        }
        val availableByRuntime = buildMap<RuntimeKind, String> {
            managedPythonExecutable
                ?.takeIf { primary == RuntimeKind.PYTHON || RuntimeKind.PYTHON in supplemental }
                ?.let { put(RuntimeKind.PYTHON, it) }
            hostTools.forEach { tool ->
                if (tool.availability != MacHostToolAvailability.AVAILABLE) return@forEach
                val kind = when (tool.kind) {
                    MacHostToolKind.PYTHON -> RuntimeKind.PYTHON
                    MacHostToolKind.NODE_JS -> RuntimeKind.NODE_JS
                    else -> null
                } ?: return@forEach
                tool.executablePath?.let { path ->
                    if (kind !in this) put(kind, path)
                }
            }
        }
        val missing = requiredKinds.filter { it !in availableByRuntime }

        val status = when {
            issues.isNotEmpty() -> MacProjectPlanStatus.BLOCKED
            missing.isNotEmpty() -> MacProjectPlanStatus.RUNTIME_MISSING
            else -> MacProjectPlanStatus.READY_TO_PREPARE
        }
        if (missing.isNotEmpty()) {
            issues += "missing host runtime: " + missing.joinToString(",") { it.id }
        }

        return MacProjectEnvironmentPlan(
            projectId = snapshot.projectId,
            status = status,
            needs = needs,
            preparationSteps = ProjectEnvironmentNeedPolicy.preparationSteps(needs),
            runtimeExecutables = availableByRuntime.filterKeys { it in requiredKinds },
            issues = issues,
        )
    }

    private data class PythonMetadata(
        val requiresPython: String? = null,
        val directDependencyCount: Int = 0,
        val optionalGroups: List<String> = emptyList(),
    )

    private fun parsePythonMetadata(
        text: String?,
        issues: MutableList<String>,
    ): PythonMetadata {
        if (text == null) return PythonMetadata()
        val parsed = Toml.parse(text)
        if (parsed.hasErrors()) {
            issues += "pyproject.toml is invalid: " + parsed.errors().take(2).joinToString(" | ")
            return PythonMetadata()
        }
        val project = parsed.get("project") as? TomlTable ?: return PythonMetadata()
        val requires = (project.get("requires-python") as? String)?.trim()?.takeIf { it.isNotBlank() }
        val dependencies = project.getArray("dependencies")?.size() ?: 0
        val optional = (project.get("optional-dependencies") as? TomlTable)?.keySet()?.sorted().orEmpty()
        return PythonMetadata(requires, dependencies, optional)
    }

    private val SUPPORTED_HOST_RUNTIMES = setOf(RuntimeKind.PYTHON, RuntimeKind.NODE_JS)
    private val UNSUPPORTED_PYTHON_MANIFESTS = setOf("setup.py", "setup.cfg", "pipfile", "poetry.lock")
}
