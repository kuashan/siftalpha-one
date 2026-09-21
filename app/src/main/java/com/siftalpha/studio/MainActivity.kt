package com.siftalpha.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.siftalpha.studio.project.ProjectStore
import com.siftalpha.studio.project.SharedGitHubImportService
import com.siftalpha.studio.project.UnifiedImportKind
import com.siftalpha.studio.project.UnifiedImportPolicy
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.runtime.ExternalProviderProbeCoordinator
import com.siftalpha.studio.runtime.ExternalProviderReadiness
import com.siftalpha.studio.runtime.ProjectRuntimeController
import com.siftalpha.studio.runtime.RuntimeCommand
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.runtime.TermuxContract
import com.siftalpha.studio.runtime.TermuxResultBus
import com.siftalpha.studio.ui.theme.StudioTheme

/**
 * The single product entry point.
 *
 * W1C moves the home surface to Compose while keeping the already-tested runtime, editor and
 * storage activities as stable destinations. Runtime commands and SAF identity remain owned here
 * until the project workspace is introduced in W2.
 */
class MainActivity : StudioComposeActivity() {

    private lateinit var backend: TermuxBackend
    private lateinit var projectStore: ProjectStore
    private lateinit var projectGateway: V04ProjectGateway
    private lateinit var projectRuntime: ProjectRuntimeController
    private lateinit var externalPreflight: ExternalProviderProbeCoordinator
    private val homeState = mutableStateOf(HomeState())
    private var autoBridgeProbeStarted = false
    private val pendingGitHubImports =
        mutableMapOf<Int, ProjectRuntimeController.GitHubCloneSpec>()

    private val resultListener: (RuntimeResult) -> Unit = { result ->
        runOnUiThread {
            val githubSpec = pendingGitHubImports.remove(result.executionId)
            val output = buildString {
                appendLine("executionId = " + result.executionId)
                appendLine("exitCode = " + result.exitCode)
                appendLine("termuxError = " + result.internalErrorCode)
                if (result.internalErrorMessage.isNotBlank()) {
                    appendLine("errmsg = " + result.internalErrorMessage)
                }
                if (result.stdout.isNotBlank()) {
                    appendLine()
                    appendLine("--- stdout ---")
                    append(result.stdout.trimEnd())
                }
                if (result.stderr.isNotBlank()) {
                    appendLine()
                    appendLine()
                    appendLine("--- stderr ---")
                    append(result.stderr.trimEnd())
                }
            }
            val bridgeOk = result.internalErrorMessage.isBlank() &&
                (result.exitCode == 0 || result.internalErrorCode == Activity.RESULT_OK)
            homeState.value = homeState.value.copy(
                commandOutput = output.take(MAX_OUTPUT_CHARS),
                bridgeState = if (bridgeOk) {
                    HomeBridgeState.CONNECTED
                } else {
                    HomeBridgeState.ABNORMAL
                },
            )
            refreshTermuxState()
            if (githubSpec != null) {
                handleGitHubImportResult(githubSpec, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = TermuxBackend(this)
        projectStore = ProjectStore(this)
        projectGateway = V04ProjectGateway(this)
        projectRuntime = ProjectRuntimeController(projectGateway)
        externalPreflight = ExternalProviderProbeCoordinator.shared(this)
        enableEdgeToEdge()
        setContent {
            StudioTheme {
                HomeScreen(
                    state = homeState.value,
                    versionName = appVersionName(),
                    onSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onConnectAcode = { chooseProjectRoot(preferAcodeProjects = true) },
                    onChooseRoot = { chooseProjectRoot(preferAcodeProjects = false) },
                    onNewProject = { showCreateProjectDialog() },
                    onImportProject = { chooseUnifiedImportFile() },
                    onImportGitHub = { showGitHubImportDialog() },
                    onUserStorage = {
                        startActivity(Intent(this, NormalRuntimeStorageActivity::class.java))
                    },
                    onRefreshProjects = { refreshProjects() },
                    onOpenProject = { openProject(it) },
                    onShowDetails = { showProjectDetails(it) },
                    onDeleteProject = { confirmDeleteProject(it) },
                    onRuntimeCenter = {
                        startActivity(Intent(this, V04Activity::class.java))
                    },
                    onEmbeddedPython = {
                        startActivity(
                            Intent(
                                this,
                                com.siftalpha.studio.siftalphax.EmbeddedPythonTestActivity::class.java,
                            ),
                        )
                    },
                    onEnvironment = {
                        startActivity(Intent(this, RuntimeStorageActivity::class.java))
                    },
                    onTerminal = {
                        startActivity(Intent(this, V05Activity::class.java))
                    },
                    onProbeEnvironment = {
                        runCommand(TermuxBackend.ENVIRONMENT_PROBE)
                    },
                    onTestTermux = {
                        runCommand(TermuxBackend.CONNECTION_TEST)
                    },
                    onRequestPermission = { requestRunCommandPermission() },
                    onCopySetup = { copyTermuxSetup() },
                    onOpenTermux = { openTermux() },
                    onCopyOutput = { copyOutput() },
                )
            }
        }
        refreshDeveloperMode()
        refreshTermuxState()
        refreshProjects()
    }

    override fun onStart() {
        super.onStart()
        TermuxResultBus.addListener(resultListener)
        refreshDeveloperMode()
        refreshTermuxState()
        if (homeState.value.developerModeEnabled) maybeAutoProbeBridge()
    }

    override fun onResume() {
        super.onResume()
        refreshDeveloperMode()
        if (::projectStore.isInitialized) refreshProjects()
    }

    override fun onStop() {
        TermuxResultBus.removeListener(resultListener)
        super.onStop()
    }

    private fun requestRunCommandPermission() {
        if (!backend.isTermuxInstalled()) {
            toast(getString(R.string.home_termux_not_found))
            refreshTermuxState()
            return
        }
        if (backend.hasRunCommandPermission()) {
            toast(getString(R.string.home_run_command_granted))
            refreshTermuxState()
            autoBridgeProbeStarted = false
            maybeAutoProbeBridge()
            return
        }
        requestPermissions(arrayOf(TermuxContract.RUN_COMMAND_PERMISSION), REQUEST_RUN_COMMAND)
    }

    private fun maybeAutoProbeBridge() {
        if (autoBridgeProbeStarted) return
        if (!backend.isTermuxInstalled()) {
            homeState.value = homeState.value.copy(bridgeState = HomeBridgeState.UNAVAILABLE)
            return
        }
        if (!backend.hasRunCommandPermission()) {
            homeState.value = homeState.value.copy(bridgeState = HomeBridgeState.WAITING_PERMISSION)
            return
        }
        autoBridgeProbeStarted = true
        homeState.value = homeState.value.copy(bridgeState = HomeBridgeState.DETECTING)
        runCommand(TermuxBackend.CONNECTION_TEST)
    }

    private fun chooseProjectRoot(preferAcodeProjects: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (preferAcodeProjects) {
                val initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:AcodeProjects",
                )
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }
        if (preferAcodeProjects) toast(getString(R.string.home_locating_acode))
        startActivityForResult(intent, REQUEST_PROJECT_ROOT)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQUEST_PROJECT_ROOT -> {
                val uri = data?.data ?: return
                val permissionFlags = data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                try {
                    contentResolver.takePersistableUriPermission(uri, permissionFlags)
                    projectStore.saveRootUri(uri)
                    toast(getString(R.string.home_root_saved))
                    refreshProjects()
                } catch (error: Throwable) {
                    toast(
                        getString(
                            R.string.home_root_save_failed,
                            error.message ?: error.javaClass.simpleName,
                        ),
                    )
                }
            }
            REQUEST_UNIFIED_IMPORT -> handleUnifiedImportResult(data)
        }
    }

    private fun showGitHubImportDialog() {
        if (projectStore.rootUri() == null) {
            toast(getString(R.string.home_import_select_location_first))
            return
        }
        if (!ensureGitHubExternalProviderReady()) return

        val url = EditText(this).apply {
            hint = "https://github.com/owner/repository"
            setSingleLine(true)
        }
        val branch = EditText(this).apply {
            setText("main")
            hint = getString(R.string.runtime_github_branch_hint)
            setSingleLine(true)
        }
        val name = EditText(this).apply {
            hint = getString(R.string.runtime_github_name_hint)
            setSingleLine(true)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
            addView(url)
            addView(branch)
            addView(name)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_github_title))
            .setMessage(getString(R.string.runtime_github_message))
            .setView(layout)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_github_start)) { _, _ ->
                val spec = runCatching {
                    SharedGitHubImportService.parse(
                        raw = url.text.toString(),
                        rawBranch = branch.text.toString(),
                        rawName = name.text.toString(),
                    ) { reason ->
                        when (reason) {
                            SharedGitHubImportService.ParseError.ENTER_ADDRESS ->
                                getString(R.string.runtime_github_enter_address)
                            SharedGitHubImportService.ParseError.ONLY_GITHUB_SUPPORTED ->
                                getString(R.string.runtime_github_only_supported)
                            SharedGitHubImportService.ParseError.ADDRESS_RULE ->
                                getString(R.string.runtime_github_address_rule)
                            SharedGitHubImportService.ParseError.PROJECT_NAME_RULE ->
                                getString(R.string.runtime_project_name_rule)
                        }
                    }
                }.getOrElse { error ->
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.runtime_github_parameters_invalid))
                        .setMessage(error.message ?: getString(R.string.runtime_github_cannot_import))
                        .setPositiveButton(getString(R.string.common_confirm), null)
                        .show()
                    return@setPositiveButton
                }

                if (projectGateway.folderExists(spec.projectName)) {
                    toast(getString(R.string.runtime_github_project_exists, spec.projectName))
                    return@setPositiveButton
                }

                runCatching {
                    val id = backend.execute(projectRuntime.cloneGitHub(spec))
                    pendingGitHubImports[id] = spec
                    homeState.value = homeState.value.copy(
                        commandOutput = getString(
                            R.string.runtime_github_importing_detail,
                            spec.sourceUrl,
                            spec.branch,
                        ),
                        bridgeState = HomeBridgeState.RUNNING,
                    )
                    TermuxResultBus.consume(id)?.let(resultListener)
                }.onFailure { error ->
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.runtime_github_import_failed))
                        .setMessage(error.message ?: getString(R.string.runtime_github_cannot_import))
                        .setPositiveButton(getString(R.string.common_confirm), null)
                        .show()
                }
            }
            .show()
    }

    private fun ensureGitHubExternalProviderReady(): Boolean {
        val result = externalPreflight.ensureReady()
        if (result.ready) return true
        when (result.readiness) {
            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.runtime_termux_missing_title))
                .setMessage(result.detail ?: getString(R.string.runtime_termux_missing_message))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.home_open_termux)) { _, _ -> openTermux() }
                .show()

            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED ->
                requestRunCommandPermission()

            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
            ExternalProviderReadiness.BRIDGE_CHECKING,
            -> toast(getString(R.string.normal_external_provider_checking))

            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.runtime_termux_missing_title))
                .setMessage(getString(R.string.normal_external_provider_no_response))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.home_open_termux)) { _, _ -> openTermux() }
                .show()

            ExternalProviderReadiness.UNAVAILABLE ->
                toast(result.detail ?: getString(R.string.normal_external_provider_unavailable))

            ExternalProviderReadiness.READY -> Unit
        }
        return false
    }

    private fun handleGitHubImportResult(
        spec: ProjectRuntimeController.GitHubCloneSpec,
        result: RuntimeResult,
    ) {
        val cloned = result.successful && "SIFTALPHA_CLONE=OK" in result.stdout
        if (!cloned) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.runtime_github_import_failed))
                .setMessage(
                    result.stderr
                        .ifBlank { result.stdout }
                        .ifBlank { getString(R.string.runtime_github_cannot_import) }
                        .takeLast(1200),
                )
                .setPositiveButton(getString(R.string.common_confirm), null)
                .show()
            return
        }

        Thread {
            val attached = runCatching {
                SharedGitHubImportService.attachMetadata(projectGateway, spec)
            }
            runOnUiThread {
                val project = attached.getOrNull()
                if (project != null) {
                    refreshProjects()
                    toast(getString(R.string.runtime_project_imported, spec.projectName))
                    openProject(project.summary)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.runtime_github_import_failed))
                        .setMessage(
                            attached.exceptionOrNull()?.message
                                ?: getString(R.string.runtime_unknown_error),
                        )
                        .setPositiveButton(getString(R.string.common_confirm), null)
                        .show()
                }
            }
        }.start()
    }

    private fun chooseUnifiedImportFile() {
        if (projectStore.rootUri() == null) {
            toast(getString(R.string.home_import_select_location_first))
            return
        }
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "text/x-python",
                    "text/plain",
                    "application/octet-stream",
                ),
            )
        }
        startActivityForResult(picker, REQUEST_UNIFIED_IMPORT)
    }

    private fun handleUnifiedImportResult(data: Intent?) {
        val uri = data?.data ?: return
        val readFlag = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (
            readFlag != 0 &&
            (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        ) {
            runCatching { contentResolver.takePersistableUriPermission(uri, readFlag) }
        }
        val fileName = displayName(uri) ?: getString(R.string.home_import_unknown_file)
        val kind = UnifiedImportPolicy.classify(fileName)
        if (kind == UnifiedImportKind.UNSUPPORTED) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.home_import_unsupported_title))
                .setMessage(getString(R.string.home_import_unsupported_message, fileName))
                .setPositiveButton(getString(R.string.common_confirm), null)
                .show()
            return
        }
        showUnifiedImportNameDialog(uri, fileName, kind)
    }

    private fun showUnifiedImportNameDialog(
        uri: Uri,
        fileName: String,
        kind: UnifiedImportKind,
    ) {
        val nameInput = EditText(this).apply {
            setText(UnifiedImportPolicy.suggestedProjectName(fileName))
            setSelection(text.length)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.home_import_confirm_title))
            .setMessage(
                getString(
                    R.string.home_import_confirm_message,
                    fileName,
                    when (kind) {
                        UnifiedImportKind.PYTHON_FILE -> getString(R.string.home_import_kind_python)
                        UnifiedImportKind.ZIP_PROJECT -> getString(R.string.home_import_kind_zip)
                        UnifiedImportKind.UNSUPPORTED -> getString(R.string.home_import_kind_unknown)
                    },
                ),
            )
            .setView(nameInput)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.home_import_action)) { _, _ ->
                val projectName = nameInput.text.toString().trim()
                importProjectFile(uri, fileName, projectName, kind)
            }
            .show()
    }

    private fun importProjectFile(
        uri: Uri,
        fileName: String,
        projectName: String,
        kind: UnifiedImportKind,
    ) {
        if (projectName.isBlank()) {
            toast(getString(R.string.home_import_name_required))
            return
        }
        if (projectGateway.folderExists(projectName)) {
            toast(getString(R.string.home_import_project_exists, projectName))
            return
        }
        toast(getString(R.string.home_importing))
        Thread {
            runCatching {
                when (kind) {
                    UnifiedImportKind.PYTHON_FILE ->
                        projectGateway.importPython(uri, projectName, fileName)
                    UnifiedImportKind.ZIP_PROJECT ->
                        projectGateway.importZip(uri, projectName, fileName)
                    UnifiedImportKind.UNSUPPORTED ->
                        error(getString(R.string.home_import_unsupported_title))
                }
            }.onSuccess { imported ->
                runOnUiThread {
                    refreshProjects()
                    toast(getString(R.string.home_import_complete, imported.summary.name))
                    openProject(imported.summary)
                }
            }.onFailure { error ->
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.home_import_failed))
                        .setMessage(error.message ?: error.javaClass.simpleName)
                        .setPositiveButton(getString(R.string.common_confirm), null)
                        .show()
                }
            }
        }.start()
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun refreshProjects() {
        if (!::projectStore.isInitialized) return
        val treeUri = projectStore.rootUri()
        if (treeUri == null) {
            homeState.value = homeState.value.copy(
                rootSelected = false,
                rootName = null,
                projects = emptyList(),
                projectError = null,
            )
            return
        }

        try {
            homeState.value = homeState.value.copy(
                rootSelected = true,
                rootName = projectStore.rootDisplayName(),
                projects = projectStore.listProjects(),
                projectError = null,
            )
        } catch (error: Throwable) {
            homeState.value = homeState.value.copy(
                rootSelected = true,
                rootName = null,
                projects = emptyList(),
                projectError = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun showCreateProjectDialog() {
        if (projectStore.rootUri() == null) {
            toast(getString(R.string.home_select_root_first))
            return
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.home_project_name_hint)
            setSingleLine(true)
        }
        val descriptionInput = EditText(this).apply {
            hint = getString(R.string.home_project_description_hint)
            setSingleLine(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), 0)
            addView(nameInput)
            addView(descriptionInput)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.home_create_project_title))
            .setView(content)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.home_create)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                try {
                    projectStore.createProject(name, description)
                    toast(getString(R.string.home_project_created, name))
                    refreshProjects()
                } catch (error: Throwable) {
                    toast(error.message ?: getString(R.string.home_create_failed))
                }
            }
            .show()
    }

    private fun openProject(project: ProjectStore.ProjectSummary) {
        startActivity(Intent(this, NormalProjectWorkspaceActivity::class.java).apply {
            putExtra(V04Activity.EXTRA_PROJECT_DOCUMENT_ID, project.documentId)
        })
    }

    private fun showProjectDetails(project: ProjectStore.ProjectSummary) {
        AlertDialog.Builder(this)
            .setTitle(project.name)
            .setMessage(
                getString(
                    R.string.home_project_details,
                    project.description.ifBlank { getString(R.string.home_none) },
                    project.entry.ifBlank { getString(R.string.home_none) },
                    project.run.ifBlank { getString(R.string.home_none) },
                    project.source,
                ),
            )
            .setPositiveButton(getString(R.string.common_confirm), null)
            .show()
    }

    private fun confirmDeleteProject(project: ProjectStore.ProjectSummary) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.home_confirm_delete_title, project.name))
            .setMessage(getString(R.string.home_confirm_delete_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.home_confirm_delete)) { _, _ ->
                try {
                    projectStore.deleteProject(project)
                    toast(getString(R.string.home_project_deleted, project.name))
                    refreshProjects()
                } catch (error: Throwable) {
                    toast(getString(R.string.home_delete_failed, error.message ?: error.javaClass.simpleName))
                }
            }
            .show()
    }

    private fun runCommand(command: RuntimeCommand) {
        if (!backend.isTermuxInstalled()) {
            homeState.value = homeState.value.copy(
                commandOutput = getString(R.string.home_termux_not_installed),
                bridgeState = HomeBridgeState.UNAVAILABLE,
            )
            refreshTermuxState()
            return
        }
        if (!backend.hasRunCommandPermission()) {
            homeState.value = homeState.value.copy(
                commandOutput = getString(R.string.home_permission_missing_output),
                bridgeState = HomeBridgeState.WAITING_PERMISSION,
            )
            refreshTermuxState()
            return
        }
        try {
            val executionId = backend.execute(command)
            homeState.value = homeState.value.copy(
                commandOutput = getString(R.string.home_command_sent, executionId),
                bridgeState = HomeBridgeState.RUNNING,
            )
        } catch (error: Throwable) {
            homeState.value = homeState.value.copy(
                commandOutput = getString(R.string.home_send_failed, error.message ?: error.javaClass.simpleName),
                bridgeState = HomeBridgeState.SEND_FAILED,
            )
        }
    }

    private fun refreshDeveloperMode() {
        homeState.value = homeState.value.copy(
            developerModeEnabled = DeveloperModeStore(this).isEnabled(),
        )
    }

    private fun refreshTermuxState() {
        if (!::backend.isInitialized) return
        val installed = runCatching { backend.isTermuxInstalled() }.getOrDefault(false)
        val permissionGranted = installed &&
            runCatching { backend.hasRunCommandPermission() }.getOrDefault(false)
        homeState.value = homeState.value.copy(
            termuxInstalled = installed,
            permissionGranted = permissionGranted,
        )
    }

    private fun copyTermuxSetup() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.home_copy_termux_setup),
                TermuxBackend.FIRST_RUN_SETUP_COMMAND,
            ),
        )
        toast(getString(R.string.home_setup_copied))
    }

    private fun copyOutput() {
        val output = homeState.value.commandOutput
        if (output.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.home_section_command_output), output),
        )
        toast(getString(R.string.home_output_copied))
    }

    private fun openTermux() {
        val launch = packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
        if (launch != null) {
            startActivity(launch)
        } else {
            toast(getString(R.string.home_no_launchable_termux))
        }
    }

    private fun appVersionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
            .ifBlank { "?" }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RUN_COMMAND) return

        refreshTermuxState()
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.home_permission_granted_toast))
            autoBridgeProbeStarted = false
            maybeAutoProbeBridge()
        } else {
            toast(getString(R.string.home_permission_denied))
            openAppPermissionSettings()
        }
    }

    private fun openAppPermissionSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName),
            ),
        )
    }

    companion object {
        private const val REQUEST_RUN_COMMAND = 501
        private const val REQUEST_PROJECT_ROOT = 601
        private const val REQUEST_UNIFIED_IMPORT = 602
        private const val MAX_OUTPUT_CHARS = 12_000
    }
}
