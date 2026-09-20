package com.siftalpha.studio.project

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.siftalpha.studio.runtime.NodeStartContractPolicy
import com.siftalpha.studio.runtime.ProjectRuntimeExecutionPlanner
import com.siftalpha.studio.runtime.RuntimeKind
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.zip.ZipInputStream

/** v0.4 导入与 Runtime 所需的 SAF 扩展，不改变 v0.3 已验证的 ProjectStore 行为。 */
class V04ProjectGateway(private val context: Context) {

    data class RuntimeProject(
        val summary: ProjectStore.ProjectSummary,
        val folderName: String,
        val sourceUrl: String?,
        /** Root-only authoritative selection used by Runtime Center presentation. */
        val runtimeSelection: ProjectRuntimeExecutionPlanner.Selection,
    )

    /** Runtime-selection facts read from authoritative project metadata and project paths. */
    data class RuntimeFacts(
        val relativePaths: List<String>,
        val declaredType: String?,
        val declaredRun: String?,
        val declaredEntry: String?,
        val hasExternalDependencyRequirement: Boolean = false,
    )

    private data class Child(
        val id: String,
        val name: String,
        val mime: String,
    )

    private data class ProjectIndexEntry(
        val summary: ProjectStore.ProjectSummary,
        val folderName: String,
        val sourceUrl: String?,
        val rootNames: List<String>,
        val declaredType: String?,
    ) {
        fun toRuntimeProject(): RuntimeProject = RuntimeProject(
            summary = summary,
            folderName = folderName,
            sourceUrl = sourceUrl,
            runtimeSelection = ProjectRuntimeExecutionPlanner.select(
                relativePaths = rootNames,
                declaredType = declaredType,
            ),
        )
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val projectStore = ProjectStore(appContext)
    private val indexPrefs = appContext.getSharedPreferences(PROJECT_INDEX_PREFS, Context.MODE_PRIVATE)

    fun rootUri(): Uri? = projectStore.rootUri()

    fun projects(forceRefresh: Boolean = false): List<RuntimeProject> {
        val tree = rootUri() ?: return emptyList()
        if (!forceRefresh) {
            readCachedProjectIndex(tree)?.let { cached ->
                return cached.map(ProjectIndexEntry::toRuntimeProject)
            }
        }
        val scanned = scanProjectIndex(tree)
        writeProjectIndex(tree, scanned)
        return scanned.map(ProjectIndexEntry::toRuntimeProject)
    }

    private fun scanProjectIndex(tree: Uri): List<ProjectIndexEntry> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        return children(tree, rootId)
            .asSequence()
            .filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
            .filterNot { it.name.startsWith(".") }
            .map { directory -> scanProjectIndexEntry(tree, directory) }
            .sortedBy { it.summary.name.lowercase() }
            .toList()
    }

    private fun scanProjectIndexEntry(tree: Uri, directory: Child): ProjectIndexEntry {
        // One root-directory query per project. All card facts below are derived from this snapshot
        // instead of repeatedly querying the same SAF directory through separate helper methods.
        val rootItems = children(tree, directory.id)
        val rootNames = rootItems.map { it.name }
        val metadataChild = rootItems.firstOrNull { it.name == ".project.json" }
        val fallbackMetadataChild = rootItems.firstOrNull { it.name == ".project.json.txt" }
        val runtimeMetadata = metadataChild
            ?.let { runCatching { JSONObject(readText(tree, it.id)) }.getOrNull() }
        val summaryMetadata = runtimeMetadata ?: fallbackMetadataChild
            ?.let { runCatching { JSONObject(readText(tree, it.id)) }.getOrNull() }

        val explicitEntry = summaryMetadata?.optString("entry")?.takeIf { it.isNotBlank() }
        val explicitRun = summaryMetadata?.optString("run")?.takeIf { it.isNotBlank() }
        val summaryDeclaredType = summaryMetadata?.optString("type")?.trim()?.lowercase().orEmpty()
        val childNames = rootNames.map { it.lowercase() }.toSet()
        val hasNodeRoot = "package.json" in childNames
        val hasPythonRoot = childNames.any { it in PYTHON_ROOT_EVIDENCE } ||
            rootItems.any { it.name.endsWith(".py", ignoreCase = true) }
        val inferredPythonEntry = ENTRY_PRIORITY.firstOrNull { wanted ->
            rootItems.any { it.name == wanted }
        } ?: rootItems.firstOrNull { it.name.endsWith(".py", ignoreCase = true) }?.name
        val nodePrimary = when {
            summaryDeclaredType in NODE_DECLARED_TYPES -> true
            summaryDeclaredType.isNotBlank() -> false
            hasNodeRoot && !hasPythonRoot -> true
            else -> false
        }
        val pythonPrimary = when {
            summaryDeclaredType == "python" || summaryDeclaredType == "py" -> true
            summaryDeclaredType.isNotBlank() -> false
            hasPythonRoot && !hasNodeRoot -> true
            else -> false
        }
        val packageHasStart = if ("package.json" in childNames) {
            rootNodePackageHasStart(tree, rootItems)
        } else {
            false
        }
        val nodeHasStart = nodePrimary && packageHasStart
        val entry = when {
            explicitEntry != null -> explicitEntry
            nodePrimary -> "package.json"
            pythonPrimary -> inferredPythonEntry.orEmpty()
            else -> ""
        }
        val run = when {
            explicitRun != null -> explicitRun
            nodePrimary && nodeHasStart -> "npm start"
            pythonPrimary && entry.isNotBlank() -> "python $entry"
            else -> ""
        }
        val sourceObject = summaryMetadata?.optJSONObject("source")
        val source = when {
            sourceObject == null -> "本地项目"
            sourceObject.optString("repository").isNotBlank() -> {
                val label = sourceObject.optString("label").ifBlank { "GitHub" }
                "$label · ${sourceObject.optString("repository")}"
            }
            sourceObject.optString("label").isNotBlank() -> sourceObject.optString("label")
            sourceObject.optString("type").isNotBlank() -> sourceObject.optString("type")
            else -> "本地项目"
        }
        var summary = ProjectStore.ProjectSummary(
            name = summaryMetadata?.optString("name")?.takeIf { it.isNotBlank() } ?: directory.name,
            description = summaryMetadata?.optString("description") ?: "",
            entry = entry,
            run = run,
            source = source,
            documentId = directory.id,
        )

        val runtimeDeclaredType = runtimeMetadata?.optString("type")?.takeIf { it.isNotBlank() }
        val selection = ProjectRuntimeExecutionPlanner.select(rootNames, runtimeDeclaredType)
        if (
            selection is ProjectRuntimeExecutionPlanner.Selection.Resolved &&
            selection.primary == RuntimeKind.NODE_JS
        ) {
            val declaredEntry = runtimeMetadata?.optString("entry")?.takeIf { it.isNotBlank() }
            val declaredRun = runtimeMetadata?.optString("run")?.takeIf { it.isNotBlank() }
            val start = NodeStartContractPolicy.resolve(
                declaredRun = declaredRun,
                hasPackageStartScript = packageHasStart,
            )
            summary = summary.copy(
                entry = declaredEntry ?: "package.json",
                run = (start as? NodeStartContractPolicy.Result.Resolved)?.command.orEmpty(),
            )
        }

        return ProjectIndexEntry(
            summary = summary,
            folderName = directory.name,
            sourceUrl = runtimeMetadata
                ?.optJSONObject("source")
                ?.optString("url")
                ?.takeIf { it.isNotBlank() },
            rootNames = rootNames,
            declaredType = runtimeDeclaredType,
        )
    }

    private fun readCachedProjectIndex(tree: Uri): List<ProjectIndexEntry>? {
        val raw = indexPrefs.getString(PROJECT_INDEX_KEY, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != PROJECT_INDEX_VERSION) return@runCatching null
            if (root.optString("rootUri") != tree.toString()) return@runCatching null
            val values = root.optJSONArray("projects") ?: return@runCatching null
            buildList {
                for (index in 0 until values.length()) {
                    val value = values.optJSONObject(index) ?: continue
                    val documentId = value.optString("documentId")
                    val folderName = value.optString("folderName")
                    val name = value.optString("name")
                    if (documentId.isBlank() || folderName.isBlank() || name.isBlank()) continue
                    val rootNamesJson = value.optJSONArray("rootNames") ?: JSONArray()
                    val rootNames = buildList {
                        for (item in 0 until rootNamesJson.length()) {
                            rootNamesJson.optString(item).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    add(
                        ProjectIndexEntry(
                            summary = ProjectStore.ProjectSummary(
                                name = name,
                                description = value.optString("description"),
                                entry = value.optString("entry"),
                                run = value.optString("run"),
                                source = value.optString("source").ifBlank { "本地项目" },
                                documentId = documentId,
                            ),
                            folderName = folderName,
                            sourceUrl = value.optString("sourceUrl").takeIf { it.isNotBlank() },
                            rootNames = rootNames,
                            declaredType = value.optString("declaredType").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrNull()
    }

    private fun writeProjectIndex(tree: Uri, values: List<ProjectIndexEntry>) {
        val projects = JSONArray().apply {
            values.forEach { entry ->
                put(JSONObject().apply {
                    put("documentId", entry.summary.documentId)
                    put("folderName", entry.folderName)
                    put("name", entry.summary.name)
                    put("description", entry.summary.description)
                    put("entry", entry.summary.entry)
                    put("run", entry.summary.run)
                    put("source", entry.summary.source)
                    put("sourceUrl", entry.sourceUrl ?: "")
                    put("declaredType", entry.declaredType ?: "")
                    put("rootNames", JSONArray().apply {
                        entry.rootNames.forEach { rootName -> put(rootName) }
                    })
                })
            }
        }
        val root = JSONObject().apply {
            put("version", PROJECT_INDEX_VERSION)
            put("rootUri", tree.toString())
            put("projects", projects)
        }
        indexPrefs.edit().putString(PROJECT_INDEX_KEY, root.toString()).apply()
    }

    private fun invalidateProjectIndex() {
        indexPrefs.edit().remove(PROJECT_INDEX_KEY).apply()
    }

    /**
     * Full runtime facts are loaded only when a Runtime action needs them. Runtime Center refresh uses
     * root-only normalization so large repositories are not recursively scanned merely to render cards.
     */
    fun runtimeFacts(projectDocumentId: String): RuntimeFacts {
        val objectValue = metadata(projectDocumentId)
        val nodes = projectStore.listProjectTree(projectDocumentId)
        return RuntimeFacts(
            relativePaths = nodes.map { it.relativePath },
            declaredType = objectValue?.optString("type")?.takeIf { it.isNotBlank() },
            declaredRun = objectValue?.optString("run")?.takeIf { it.isNotBlank() },
            declaredEntry = objectValue?.optString("entry")?.takeIf { it.isNotBlank() },
            hasExternalDependencyRequirement = requiresExternalPythonEnvironment(nodes),
        )
    }

    /** Read one bounded root metadata file at action time without exposing SAF to policy code. */
    fun readProjectRootText(
        projectDocumentId: String,
        fileName: String,
        maxBytes: Int = MAX_ROOT_TEXT_BYTES,
    ): String? {
        require(fileName in setOf("pyproject.toml", "requirements.txt")) {
            "只有 pyproject.toml / requirements.txt 可通过此接口读取"
        }
        require(maxBytes in 1..MAX_ROOT_TEXT_BYTES) { "根文件读取上限无效" }
        val file = projectStore.listProjectChildren(projectDocumentId)
            .firstOrNull {
                !it.isDirectory &&
                    it.relativePath == fileName
            } ?: return null
        val bytes = projectStore.readProjectFileBytes(file, maxBytes + 1)
        require(bytes.size <= maxBytes) {
            "$fileName 超过 ${maxBytes / 1024} KB，当前启动解析器不会读取"
        }
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrElse {
            error("$fileName 不是有效的 UTF-8 文本")
        }
    }

    /** Resolve the one entrypoint M is willing to hand to Embedded R. R never scans or guesses. */
    fun resolveEmbeddedPythonEntrypoint(projectDocumentId: String): String? {
        val objectValue = metadata(projectDocumentId)
        val declaredEntry = objectValue?.optString("entry")?.takeIf { it.isNotBlank() }
        val filePaths = projectStore
            .listProjectTreeForStaging(projectDocumentId)
            .filterNot { it.isDirectory }
            .map { it.relativePath }
        return EmbeddedPythonEntrypointPolicy.resolve(declaredEntry, filePaths)
    }

    fun runtimeSharedRootRelativePath(): String? {
        val tree = rootUri() ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return null
        if (!treeId.startsWith("primary:")) return null
        return treeId.removePrefix("primary:").trim('/')
    }

    fun folderExists(name: String): Boolean {
        val tree = rootUri() ?: return false
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        return children(tree, rootId).any {
            it.mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                it.name.equals(name, ignoreCase = true)
        }
    }

    fun importPython(source: Uri, projectName: String, sourceFilename: String): RuntimeProject {
        validateProjectName(projectName)
        require(sourceFilename.lowercase().endsWith(".py")) { "请选择 .py 文件" }
        val tree = rootUri() ?: error("请先选择项目目录")
        val projectUri = createProjectDirectory(tree, projectName)
        val projectId = DocumentsContract.getDocumentId(projectUri)
        try {
            val filename = safeName(sourceFilename)
            val input = resolver.openInputStream(source) ?: error("无法读取 $sourceFilename")
            input.use {
                createStreamFile(tree, projectId, filename, "text/x-python", it, MAX_PY_BYTES, null)
            }
            createText(tree, projectId, "requirements.txt", "")
            val metadata = JSONObject().apply {
                put("name", projectName)
                put("description", "")
                put("entry", filename)
                put("run", "python ${shellQuote(filename)}")
                put("type", "python")
                put("source", JSONObject().apply {
                    put("type", "file")
                    put("label", "本地 PY")
                    put("filename", sourceFilename)
                    put("importedAt", Instant.now().toString())
                })
            }
            createText(tree, projectId, ".project.json", metadata.toString(2) + "\n")
            return runtimeProject(projectId)
        } catch (e: Throwable) {
            deleteQuietly(projectUri)
            throw e
        }
    }

    fun importZip(source: Uri, projectName: String, sourceFilename: String): RuntimeProject {
        validateProjectName(projectName)
        val tree = rootUri() ?: error("请先选择项目目录")
        val stripRoot = scanZipCommonRoot(source)
        val projectUri = createProjectDirectory(tree, projectName)
        val projectId = DocumentsContract.getDocumentId(projectUri)
        try {
            val dirs = mutableMapOf("" to projectId)
            val paths = mutableSetOf<String>()
            val total = longArrayOf(0L)
            var files = 0

            resolver.openInputStream(source)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val original = safeZipParts(entry.name)
                        val parts = if (stripRoot != null && original.firstOrNull() == stripRoot) {
                            original.drop(1)
                        } else {
                            original
                        }
                        if (parts.isNotEmpty()) {
                            val relative = parts.joinToString("/")
                            if (!entry.isDirectory) {
                                require(paths.add(relative)) { "ZIP 中存在重复路径：$relative" }
                                files += 1
                                require(files <= MAX_ZIP_FILES) { "ZIP 文件数量超过 $MAX_ZIP_FILES" }
                                val parent = ensureDirs(tree, dirs, parts.dropLast(1))
                                createStreamFile(
                                    tree,
                                    parent,
                                    parts.last(),
                                    mime(parts.last()),
                                    zip,
                                    MAX_ZIP_FILE_BYTES,
                                    total,
                                )
                            } else {
                                ensureDirs(tree, dirs, parts)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取 ZIP")
            require(files > 0) { "ZIP 中没有可导入文件" }

            upsertSource(
                projectId,
                projectName,
                JSONObject().apply {
                    put("type", "zip")
                    put("label", "ZIP 导入")
                    put("filename", sourceFilename)
                    put("importedAt", Instant.now().toString())
                },
            )
            return runtimeProject(projectId)
        } catch (e: Throwable) {
            deleteQuietly(projectUri)
            throw e
        }
    }

    fun attachGitHubSource(
        folderName: String,
        sourceUrl: String,
        branch: String,
    ): RuntimeProject {
        val tree = rootUri() ?: error("项目目录不可用")
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val folder = children(tree, rootId).firstOrNull {
            it.mime == DocumentsContract.Document.MIME_TYPE_DIR && it.name == folderName
        } ?: error("项目目录尚未出现：$folderName")
        val repo = sourceUrl.removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        upsertSource(
            folder.id,
            folder.name,
            JSONObject().apply {
                put("type", "github")
                put("label", "GitHub")
                put("repository", repo)
                put("url", sourceUrl)
                put("branch", branch)
                put("importedAt", Instant.now().toString())
            },
        )
        return runtimeProject(folder.id)
    }

    fun sourceUrl(projectDocumentId: String): String? =
        metadata(projectDocumentId)
            ?.optJSONObject("source")
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }

    private fun runtimeProject(projectId: String): RuntimeProject {
        invalidateProjectIndex()
        val storedSummary = projectStore.listProjects().firstOrNull { it.documentId == projectId }
            ?: error("项目创建成功，但暂时无法重新读取")
        val summary = normalizeRuntimeSummary(storedSummary)
        return RuntimeProject(
            summary = summary,
            folderName = documentDisplayName(projectId) ?: summary.name,
            sourceUrl = sourceUrl(projectId),
            runtimeSelection = runtimeSelection(projectId),
        )
    }

    /**
     * Runtime Center only needs root evidence to present a card. Full tree facts remain deferred to
     * the action-time execution planner, so a refresh never becomes a recursive project scan.
     */
    private fun runtimeSelection(projectDocumentId: String): ProjectRuntimeExecutionPlanner.Selection {
        val tree = rootUri() ?: return ProjectRuntimeExecutionPlanner.select(emptyList())
        val rootItems = children(tree, projectDocumentId)
        val metadata = rootItems.firstOrNull { it.name == ".project.json" }
            ?.let { readText(tree, it.id) }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        return ProjectRuntimeExecutionPlanner.select(
            relativePaths = rootItems.map { it.name },
            declaredType = metadata?.optString("type")?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * ProjectStore predates Multi-Runtime execution and therefore keeps a Python fallback for legacy
     * metadata. Correct that presentation at the gateway boundary using root-only authoritative facts,
     * without recursively scanning large repositories during every Runtime Center refresh.
     */
    private fun normalizeRuntimeSummary(summary: ProjectStore.ProjectSummary): ProjectStore.ProjectSummary {
        val tree = rootUri() ?: return summary
        val rootItems = children(tree, summary.documentId)
        val objectValue = rootItems.firstOrNull { it.name == ".project.json" }
            ?.let { readText(tree, it.id) }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val declaredType = objectValue?.optString("type")?.takeIf { it.isNotBlank() }
        val selection = ProjectRuntimeExecutionPlanner.select(
            relativePaths = rootItems.map { it.name },
            declaredType = declaredType,
        )
        if (selection !is ProjectRuntimeExecutionPlanner.Selection.Resolved) return summary
        if (selection.primary != RuntimeKind.NODE_JS) return summary

        val declaredEntry = objectValue?.optString("entry")?.takeIf { it.isNotBlank() }
        val declaredRun = objectValue?.optString("run")?.takeIf { it.isNotBlank() }
        val packageStart = rootNodePackageHasStart(tree, rootItems)
        val start = NodeStartContractPolicy.resolve(declaredRun, packageStart)
        val run = (start as? NodeStartContractPolicy.Result.Resolved)?.command.orEmpty()
        return summary.copy(
            entry = declaredEntry ?: "package.json",
            run = run,
        )
    }

    private fun rootNodePackageHasStart(tree: Uri, rootItems: List<Child>): Boolean {
        val packageJson = rootItems.firstOrNull { it.name == "package.json" } ?: return false
        val objectValue = runCatching { JSONObject(readText(tree, packageJson.id)) }.getOrNull() ?: return false
        return objectValue.optJSONObject("scripts")
            ?.optString("start")
            ?.isNotBlank() == true
    }

    private fun createProjectDirectory(tree: Uri, name: String): Uri {
        require(!folderExists(name)) { "项目 $name 已存在" }
        return DocumentsContract.createDocument(
            resolver,
            rootDocumentUri(tree),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: error("无法创建项目目录")
    }

    private fun upsertSource(projectId: String, folderName: String, source: JSONObject) {
        val tree = rootUri() ?: error("项目目录不可用")
        val childItems = children(tree, projectId)
        val metadataChild = childItems.firstOrNull { it.name == ".project.json" }
        val objectValue = metadataChild
            ?.let { readText(tree, it.id) }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject()

        if (objectValue.optString("name").isBlank()) objectValue.put("name", folderName)
        if (!objectValue.has("description")) objectValue.put("description", "")

        // Runtime-neutral import is deliberate: do not fabricate Python metadata for a repository
        // that may be Node/JVM/Go/Rust. Explicit upstream .project.json values are preserved; otherwise
        // the execution planner derives the Runtime from authoritative project evidence at action time.
        objectValue.put("source", source)

        val content = objectValue.toString(2) + "\n"
        if (metadataChild == null) {
            createText(tree, projectId, ".project.json", content)
        } else {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, metadataChild.id)
            resolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
                it.flush()
            } ?: error("无法写入 .project.json")
        }
    }

    private fun metadata(projectId: String): JSONObject? {
        val tree = rootUri() ?: return null
        val child = children(tree, projectId).firstOrNull { it.name == ".project.json" } ?: return null
        return runCatching { JSONObject(readText(tree, child.id)) }.getOrNull()
    }

    private fun scanZipCommonRoot(source: Uri): String? {
        var common: String? = null
        var canStrip = true
        var files = 0
        resolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val parts = safeZipParts(entry.name)
                    if (!entry.isDirectory && parts.isNotEmpty()) {
                        files += 1
                        require(files <= MAX_ZIP_FILES) { "ZIP 文件数量超过 $MAX_ZIP_FILES" }
                        if (parts.size < 2) canStrip = false
                        else if (common == null) common = parts.first()
                        else if (common != parts.first()) canStrip = false
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("无法读取 ZIP")
        require(files > 0) { "ZIP 中没有文件" }
        return common.takeIf { canStrip }
    }

    private fun safeZipParts(raw: String): List<String> {
        require(raw.isNotBlank()) { "ZIP 中存在空路径" }
        require(!raw.startsWith("/") && !raw.startsWith("\\")) { "ZIP 中存在绝对路径" }
        val normalized = raw.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return emptyList()
        val parts = normalized.split('/').filter { it.isNotBlank() }
        require(parts.none { it == "." || it == ".." }) { "ZIP 中存在不安全路径：$raw" }
        parts.forEach { safeName(it) }
        return parts
    }

    private fun ensureDirs(tree: Uri, map: MutableMap<String, String>, parts: List<String>): String {
        var parentPath = ""
        var parentId = map.getValue("")
        for (part in parts) {
            val path = if (parentPath.isBlank()) part else "$parentPath/$part"
            val existing = map[path]
            if (existing != null) {
                parentId = existing
            } else {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
                val folder = DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    part,
                ) ?: error("无法创建文件夹 $path")
                parentId = DocumentsContract.getDocumentId(folder)
                map[path] = parentId
            }
            parentPath = path
        }
        return parentId
    }

    private fun createStreamFile(
        tree: Uri,
        parentId: String,
        name: String,
        mime: String,
        input: java.io.InputStream,
        maxBytes: Long,
        total: LongArray?,
    ) {
        safeName(name)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val file = createExactDocument(parentUri, name, mime)
        var written = 0L
        resolver.openOutputStream(file, "wt")?.use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                written += count
                require(written <= maxBytes) { "$name 超过导入大小上限" }
                if (total != null) {
                    total[0] += count
                    require(total[0] <= MAX_ZIP_TOTAL_BYTES) { "ZIP 解包后超过 256 MB" }
                }
                output.write(buffer, 0, count)
            }
            output.flush()
        } ?: error("无法写入 $name")
    }

    private fun createText(tree: Uri, parentId: String, name: String, content: String) {
        safeName(name)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val file = createExactDocument(parentUri, name, "text/plain")
        resolver.openOutputStream(file, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
            it.flush()
        } ?: error("无法写入 $name")
    }

    private fun createExactDocument(parentUri: Uri, name: String, requestedMime: String): Uri {
        val createMime = if (
            requestedMime.startsWith("text/") ||
            name.startsWith(".") ||
            name.substringAfterLast('.', "").lowercase() in SOURCE_LIKE_EXTENSIONS
        ) {
            "application/octet-stream"
        } else {
            requestedMime
        }

        var uri = DocumentsContract.createDocument(resolver, parentUri, createMime, name)
            ?: error("无法创建 $name")
        var actualName = displayName(uri)
        if (actualName != null && actualName != name) {
            uri = DocumentsContract.renameDocument(resolver, uri, name) ?: uri
            actualName = displayName(uri)
        }
        require(actualName == null || actualName == name) {
            "Android 文件提供器修改了文件名：$name -> $actualName"
        }
        return uri
    }

    private fun requiresExternalPythonEnvironment(nodes: List<ProjectStore.FileNode>): Boolean {
        val rootFiles = nodes.filter { !it.isDirectory && '/' !in it.relativePath }
        val rootNames = rootFiles.map { it.name.lowercase() }.toSet()
        if (rootNames.any { it in EMBEDDED_R_UNSUPPORTED_PYTHON_MANIFESTS }) return true

        val otherRootManifest = rootFiles.firstOrNull {
            isPythonDependencyManifest(it.name) &&
                !it.name.equals("requirements.txt", ignoreCase = true)
        }
        if (otherRootManifest != null) return true

        val nestedDependencyManifest = nodes.any { node ->
            !node.isDirectory &&
                '/' in node.relativePath &&
                isPythonDependencyManifest(node.name)
        }
        if (nestedDependencyManifest) return true

        val requirements = rootFiles.firstOrNull { it.name.equals("requirements.txt", ignoreCase = true) }
            ?: return false
        val bytes = runCatching {
            projectStore.readProjectFileBytes(requirements, MAX_DEPENDENCY_PROBE_BYTES + 1)
        }.getOrNull() ?: return true
        if (bytes.size > MAX_DEPENDENCY_PROBE_BYTES) return true
        return bytes.toString(Charsets.UTF_8).lineSequence().any { line ->
            val value = line.trim()
            value.isNotBlank() && !value.startsWith("#")
        }
    }

    private fun isPythonDependencyManifest(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "pyproject.toml" ||
            lower == "setup.py" ||
            lower == "setup.cfg" ||
            lower == "pipfile" ||
            lower == "pipfile.lock" ||
            lower == "poetry.lock" ||
            (lower.startsWith("requirements") && lower.endsWith(".txt"))
    }

    private fun displayName(uri: Uri): String? {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun children(tree: Uri, parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val result = mutableListOf<Child>()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val name = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                result += Child(cursor.getString(id), cursor.getString(name) ?: "", cursor.getString(mime) ?: "")
            }
        }
        return result
    }

    private fun documentDisplayName(documentId: String): String? {
        val tree = rootUri() ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun readText(tree: Uri, id: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun rootDocumentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun validateProjectName(name: String) {
        require(PROJECT_NAME.matches(name)) {
            "项目名只能包含英文字母、数字、点、下划线和短横线"
        }
    }

    private fun safeName(raw: String): String {
        val name = raw.trim()
        require(name.isNotBlank()) { "名称不能为空" }
        require(name.length <= 180) { "名称过长" }
        require(name != "." && name != "..") { "名称无效" }
        require('/' !in name && '\\' !in name && '\u0000' !in name && '\n' !in name && '\r' !in name) {
            "名称包含不支持的字符"
        }
        return name
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun mime(name: String): String {
        val ext = name.lowercase().substringAfterLast('.', "")
        return when (ext) {
            "py", "txt", "md", "json", "toml", "yaml", "yml", "ini", "cfg", "sh", "sql",
            "html", "css", "js", "ts", "kt", "java", "xml", "env", "gitignore" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun deleteQuietly(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }

    companion object {
        private const val PROJECT_INDEX_PREFS = "siftalpha_project_index_cache_v1"
        private const val PROJECT_INDEX_KEY = "current_root_index"
        private const val PROJECT_INDEX_VERSION = 1
        private val ENTRY_PRIORITY = listOf("main.py", "app.py", "run.py", "manage.py")
        private val NODE_DECLARED_TYPES = setOf("node", "nodejs", "javascript", "js")
        private val PYTHON_ROOT_EVIDENCE = setOf(
            "requirements.txt",
            "pyproject.toml",
            "setup.py",
            "setup.cfg",
            "pipfile",
            "poetry.lock",
            "main.py",
            "app.py",
            "run.py",
            "manage.py",
        )
        private val PROJECT_NAME = Regex("^[A-Za-z0-9._-]+$")
        private val SOURCE_LIKE_EXTENSIONS = setOf(
            "py", "txt", "md", "json", "toml", "yaml", "yml", "ini", "cfg", "sh", "sql",
            "html", "css", "js", "ts", "kt", "java", "xml", "env", "gitignore",
        )
        private const val MAX_PY_BYTES = 8L * 1024 * 1024
        private const val MAX_ZIP_FILES = 4000
        private const val MAX_ZIP_FILE_BYTES = 64L * 1024 * 1024
        private const val MAX_ZIP_TOTAL_BYTES = 256L * 1024 * 1024
        private const val MAX_DEPENDENCY_PROBE_BYTES = 64 * 1024
        private const val MAX_ROOT_TEXT_BYTES = 512 * 1024
        private val EMBEDDED_R_UNSUPPORTED_PYTHON_MANIFESTS = setOf(
            "pyproject.toml",
            "setup.py",
            "setup.cfg",
            "pipfile",
            "pipfile.lock",
            "poetry.lock",
        )
    }
}
