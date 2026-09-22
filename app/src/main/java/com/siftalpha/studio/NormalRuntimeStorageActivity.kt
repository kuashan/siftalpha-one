package com.siftalpha.studio

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siftalpha.studio.project.EmbeddedPythonProjectStager
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.runtime.ExternalProviderPreflightResult
import com.siftalpha.studio.runtime.ExternalProviderProbeCoordinator
import com.siftalpha.studio.runtime.ExternalProviderReadiness
import com.siftalpha.studio.runtime.InternalRuntimeStorageController
import com.siftalpha.studio.runtime.ProjectRuntimeController
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.RuntimeStorageController
import com.siftalpha.studio.runtime.RuntimeStorageSizeFormatter
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.runtime.TermuxResultBus
import com.siftalpha.studio.siftalphax.EmbeddedPythonEnvironmentManager
import com.siftalpha.studio.siftalphax.EmbeddedPythonSession
import com.siftalpha.studio.siftalphax.InternalAlpineEnvironmentManager
import com.siftalpha.studio.siftalphax.InternalAlpineSession
import java.util.concurrent.Executors

/**
 * Product-facing Runtime storage view for Normal Mode.
 *
 * R48-D3 only changes presentation. Storage controllers, project-scoped cleanup semantics and
 * External Provider readiness remain the R48-D2 baseline behavior.
 */
class NormalRuntimeStorageActivity : StudioComposeActivity() {

    private sealed interface Pending {
        object Snapshot : Pending
        data class CleanExternal(val folderName: String) : Pending
    }

    private enum class StorageKind {
        INTERNAL,
        EXTERNAL,
    }

    private data class ProjectUsageUi(
        val key: String,
        val displayName: String,
        val sizeLabel: String,
        val project: V04ProjectGateway.RuntimeProject?,
        val kind: StorageKind,
    )

    private data class SectionUi(
        val loading: Boolean = false,
        val totalLabel: String? = null,
        val projectTotalLabel: String? = null,
        val message: String? = null,
        val rows: List<ProjectUsageUi> = emptyList(),
    )

    private data class ScreenUi(
        val status: String = "",
        val internal: SectionUi = SectionUi(),
        val external: SectionUi = SectionUi(),
    )

    private sealed interface CleanTarget {
        data class Internal(val project: V04ProjectGateway.RuntimeProject) : CleanTarget
        data class External(val project: V04ProjectGateway.RuntimeProject) : CleanTarget
    }

    private lateinit var backend: TermuxBackend
    private lateinit var gateway: V04ProjectGateway
    private lateinit var runtime: ProjectRuntimeController
    private lateinit var externalStorage: RuntimeStorageController
    private lateinit var internalStorage: InternalRuntimeStorageController
    private lateinit var externalPreflight: ExternalProviderProbeCoordinator

    private val pending = mutableMapOf<Int, Pending>()
    private val executor = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var projects: List<V04ProjectGateway.RuntimeProject> = emptyList()

    private val uiState = mutableStateOf(ScreenUi())
    private val cleanTarget = mutableStateOf<CleanTarget?>(null)
    private val errorMessage = mutableStateOf<String?>(null)

    private val preflightListener: (ExternalProviderPreflightResult) -> Unit = { result ->
        runOnUiThread {
            when {
                result.ready -> requestExternalSnapshot()
                result.readiness == ExternalProviderReadiness.BRIDGE_CHECKING ->
                    renderExternalChecking()
                else -> renderExternalUnavailable()
            }
        }
    }

    private val resultListener: (RuntimeResult) -> Unit = { result ->
        runOnUiThread {
            val action = pending.remove(result.executionId) ?: return@runOnUiThread
            TermuxResultBus.consume(result.executionId)
            when (action) {
                Pending.Snapshot -> {
                    if (result.successful) {
                        renderExternal(RuntimeStorageController.parseSnapshot(result.stdout))
                    } else {
                        renderExternalUnavailable()
                    }
                }

                is Pending.CleanExternal -> {
                    if (result.successful) {
                        toast(getString(R.string.user_storage_clean_done))
                        refreshStorage()
                    } else {
                        showError(result.stderr.ifBlank { result.stdout }.takeLast(800))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = TermuxBackend(this)
        gateway = V04ProjectGateway(this)
        runtime = ProjectRuntimeController(
            gateway = gateway,
            embeddedPythonSession = EmbeddedPythonSession.shared(this),
            embeddedPythonProjectStager = EmbeddedPythonProjectStager(this),
            embeddedPythonEnvironmentManager = EmbeddedPythonEnvironmentManager(this),
            internalAlpineEnvironmentManager = InternalAlpineEnvironmentManager(this),
            internalAlpineSession = InternalAlpineSession.shared(this),
        )
        externalStorage = RuntimeStorageController(gateway)
        internalStorage = InternalRuntimeStorageController(filesDir)
        externalPreflight = ExternalProviderProbeCoordinator.shared(this)

        enableEdgeToEdge()
        setContent {
            SiftAlphaNormalTheme {
                NormalRuntimeStorageScreen(
                    state = uiState.value,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onRefresh = { refreshStorage() },
                    onClean = { cleanTarget.value = it },
                )

                cleanTarget.value?.let { target ->
                    val project = when (target) {
                        is CleanTarget.Internal -> target.project
                        is CleanTarget.External -> target.project
                    }
                    StorageConfirmDialog(
                        projectName = project.summary.name,
                        onDismiss = { cleanTarget.value = null },
                        onConfirm = {
                            cleanTarget.value = null
                            when (target) {
                                is CleanTarget.Internal -> performCleanInternal(target.project)
                                is CleanTarget.External -> performCleanExternal(target.project)
                            }
                        },
                    )
                }

                errorMessage.value?.let { message ->
                    AlertDialog(
                        onDismissRequest = { errorMessage.value = null },
                        title = {
                            Text(
                                stringResource(R.string.user_storage_error),
                                color = StorageText,
                            )
                        },
                        text = {
                            Text(message, color = StorageMuted)
                        },
                        confirmButton = {
                            TextButton(onClick = { errorMessage.value = null }) {
                                Text(stringResource(R.string.common_confirm))
                            }
                        },
                        containerColor = StoragePanel,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TermuxResultBus.addListener(resultListener)
        externalPreflight.addListener(preflightListener)
        pending.keys.toList().forEach { id ->
            TermuxResultBus.consume(id)?.let(resultListener)
        }
        refreshStorage()
    }

    override fun onStop() {
        externalPreflight.removeListener(preflightListener)
        TermuxResultBus.removeListener(resultListener)
        super.onStop()
    }

    override fun onDestroy() {
        generation += 1L
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshStorage() {
        projects = if (gateway.rootUri() == null) {
            emptyList()
        } else {
            runCatching { gateway.projects() }.getOrDefault(emptyList())
        }

        uiState.value = uiState.value.copy(
            status = getString(R.string.user_storage_scanning),
            internal = SectionUi(
                loading = true,
                message = getString(R.string.user_storage_scanning_internal),
            ),
            external = SectionUi(
                loading = true,
                message = getString(R.string.user_storage_scanning_external),
            ),
        )

        val currentGeneration = ++generation
        executor.execute {
            val snapshot = runCatching { internalStorage.snapshot(projects) }
            runOnUiThread {
                if (
                    currentGeneration != generation ||
                    isFinishing ||
                    isDestroyed
                ) {
                    return@runOnUiThread
                }
                snapshot.onSuccess(::renderInternal)
                    .onFailure {
                        uiState.value = uiState.value.copy(
                            internal = SectionUi(
                                message = getString(R.string.user_storage_scan_failed),
                            ),
                        )
                    }
            }
        }

        val readiness = externalPreflight.ensureReady()
        when {
            readiness.ready -> requestExternalSnapshot()
            readiness.readiness == ExternalProviderReadiness.BRIDGE_CHECKING ->
                renderExternalChecking()
            else -> renderExternalUnavailable()
        }
    }

    private fun requestExternalSnapshot() {
        if (pending.values.any { it == Pending.Snapshot }) return
        uiState.value = uiState.value.copy(
            external = SectionUi(
                loading = true,
                message = getString(R.string.user_storage_scanning_external),
            ),
        )
        runCatching {
            val id = backend.execute(externalStorage.snapshot(projects))
            pending[id] = Pending.Snapshot
            TermuxResultBus.consume(id)?.let(resultListener)
        }.onFailure {
            renderExternalUnavailable()
        }
    }

    private fun renderExternalChecking() {
        uiState.value = uiState.value.copy(
            external = SectionUi(
                loading = true,
                message = getString(R.string.user_storage_external_checking),
            ),
        )
    }

    private fun renderInternal(snapshot: InternalRuntimeStorageController.Snapshot) {
        val rows = snapshot.projects.map { usage ->
            val project = projects.firstOrNull {
                it.summary.documentId == usage.documentId
            }
            ProjectUsageUi(
                key = "internal:${usage.documentId}",
                displayName = usage.displayName,
                sizeLabel = formatSize(usage.totalKb),
                project = project,
                kind = StorageKind.INTERNAL,
            )
        }

        uiState.value = uiState.value.copy(
            internal = SectionUi(
                totalLabel = getString(
                    R.string.user_storage_total,
                    formatSize(snapshot.totalKb),
                ),
                projectTotalLabel = getString(
                    R.string.user_storage_project_total,
                    formatSize(snapshot.projectEnvironmentKb),
                ),
                message = if (rows.isEmpty()) {
                    getString(R.string.user_storage_no_project_space)
                } else {
                    null
                },
                rows = rows,
            ),
            status = getString(R.string.user_storage_ready),
        )
    }

    private fun renderExternal(snapshot: RuntimeStorageController.Snapshot) {
        if (!snapshot.ubuntuInstalled) {
            uiState.value = uiState.value.copy(
                external = SectionUi(
                    message = getString(R.string.user_storage_external_not_ready),
                ),
                status = getString(R.string.user_storage_ready),
            )
            return
        }

        val rows = snapshot.projects.map { usage ->
            val project = projects.firstOrNull { it.folderName == usage.folderName }
            ProjectUsageUi(
                key = "external:${usage.folderName}",
                displayName = project?.summary?.name ?: usage.folderName,
                sizeLabel = formatSize(usage.sizeKb),
                project = project,
                kind = StorageKind.EXTERNAL,
            )
        }

        uiState.value = uiState.value.copy(
            external = SectionUi(
                totalLabel = getString(
                    R.string.user_storage_total,
                    formatSize(snapshot.ubuntuTotalKb),
                ),
                projectTotalLabel = getString(
                    R.string.user_storage_project_total,
                    formatSize(snapshot.projectRuntimeKb),
                ),
                message = if (rows.isEmpty()) {
                    getString(R.string.user_storage_no_project_space)
                } else {
                    null
                },
                rows = rows,
            ),
            status = getString(R.string.user_storage_ready),
        )
    }

    private fun renderExternalUnavailable() {
        uiState.value = uiState.value.copy(
            external = SectionUi(
                message = getString(R.string.user_storage_external_unavailable),
            ),
            status = getString(R.string.user_storage_ready),
        )
    }

    private fun performCleanInternal(project: V04ProjectGateway.RuntimeProject) {
        uiState.value = uiState.value.copy(
            status = getString(R.string.user_storage_cleaning),
        )
        executor.execute {
            val result = runCatching { runtime.cleanEmbeddedPythonEnvironment(project) }
            runOnUiThread {
                result.onSuccess {
                    toast(getString(R.string.user_storage_clean_done))
                    refreshStorage()
                }.onFailure { error ->
                    showError(error.message ?: getString(R.string.user_storage_clean_failed))
                }
            }
        }
    }

    private fun performCleanExternal(project: V04ProjectGateway.RuntimeProject) {
        uiState.value = uiState.value.copy(
            status = getString(R.string.user_storage_cleaning),
        )
        runCatching {
            val id = backend.execute(runtime.clean(project))
            pending[id] = Pending.CleanExternal(project.folderName)
            TermuxResultBus.consume(id)?.let(resultListener)
        }.onFailure { error ->
            showError(error.message ?: getString(R.string.user_storage_clean_failed))
        }
    }

    private fun showError(message: String) {
        errorMessage.value = message
    }

    private fun formatSize(kb: Long): String =
        RuntimeStorageSizeFormatter.formatKilobytes(kb)

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NormalRuntimeStorageScreen(
        state: ScreenUi,
        onBack: () -> Unit,
        onRefresh: () -> Unit,
        onClean: (CleanTarget) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            StorageInk,
                            Color(0xFF071A35),
                            StorageInk,
                        ),
                    ),
                ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.siftalpha_launcher_art),
                                    contentDescription = stringResource(
                                        R.string.brand_mark_content_description,
                                    ),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(Modifier.size(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.user_storage_title),
                                        color = StorageText,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        color = StorageMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            Surface(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(44.dp)
                                    .clickable(role = Role.Button, onClick = onBack),
                                shape = CircleShape,
                                color = Color.Transparent,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "‹",
                                        color = StorageText,
                                        fontSize = 30.sp,
                                    )
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    item {
                        StorageHero(
                            status = state.status,
                            onRefresh = onRefresh,
                        )
                    }

                    item {
                        StorageSection(
                            title = stringResource(R.string.user_storage_internal_title),
                            section = state.internal,
                            onClean = { row ->
                                row.project?.let {
                                    onClean(CleanTarget.Internal(it))
                                }
                            },
                        )
                    }

                    item {
                        StorageSection(
                            title = stringResource(R.string.user_storage_external_title),
                            section = state.external,
                            onClean = { row ->
                                row.project?.let {
                                    onClean(CleanTarget.External(it))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StorageHero(
        status: String,
        onRefresh: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = StoragePanel.copy(alpha = .95f),
            border = BorderStroke(1.dp, StorageBorder),
        ) {
            Column(Modifier.padding(17.dp)) {
                Text(
                    text = stringResource(R.string.user_storage_summary),
                    color = StorageText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = status,
                    color = StorageMuted,
                    fontSize = 11.sp,
                )
                if (
                    status == stringResource(R.string.user_storage_scanning) ||
                    status == stringResource(R.string.user_storage_cleaning)
                ) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = StorageCyan,
                        trackColor = Color.White.copy(alpha = .08f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StorageBorder),
                ) {
                    Text(
                        stringResource(R.string.user_storage_refresh),
                        color = StorageText,
                    )
                }
            }
        }
    }

    @Composable
    private fun StorageSection(
        title: String,
        section: SectionUi,
        onClean: (ProjectUsageUi) -> Unit,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = StoragePanel.copy(alpha = .95f),
            border = BorderStroke(1.dp, StorageBorder),
        ) {
            Column(
                modifier = Modifier.padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    title,
                    color = StorageText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                section.totalLabel?.let {
                    StorageMetric(it, strong = true)
                }
                section.projectTotalLabel?.let {
                    StorageMetric(it, strong = false)
                }

                if (section.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = StorageCyan,
                        trackColor = Color.White.copy(alpha = .08f),
                    )
                }

                section.message?.let {
                    Text(
                        it,
                        color = StorageMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }

                section.rows.forEach { row ->
                    StorageProjectCard(row = row, onClean = { onClean(row) })
                }
            }
        }
    }

    @Composable
    private fun StorageMetric(
        value: String,
        strong: Boolean,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = StorageRaised,
            border = BorderStroke(1.dp, StorageBorder),
        ) {
            Text(
                text = value,
                color = if (strong) StorageText else StorageMuted,
                fontSize = if (strong) 13.sp else 11.sp,
                fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            )
        }
    }

    @Composable
    private fun StorageProjectCard(
        row: ProjectUsageUi,
        onClean: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = StorageRaised,
            border = BorderStroke(1.dp, StorageBorder),
        ) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.displayName,
                            color = StorageText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = row.sizeLabel,
                            color = StorageMuted,
                            fontSize = 10.sp,
                        )
                    }
                    if (row.project != null) {
                        TextButton(onClick = onClean) {
                            Text(
                                stringResource(R.string.user_storage_clean_project),
                                color = StorageCyan,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StorageConfirmDialog(
        projectName: String,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(R.string.user_storage_clean_title, projectName),
                    color = StorageText,
                )
            },
            text = {
                Text(
                    stringResource(R.string.user_storage_clean_message),
                    color = StorageMuted,
                )
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = StorageMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StorageBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.user_storage_clean_confirm))
                }
            },
            containerColor = StoragePanel,
        )
    }

    companion object {
        private val StorageInk = Color(0xFF040817)
        private val StoragePanel = Color(0xFF0B1631)
        private val StorageRaised = Color(0xFF101D3D)
        private val StorageBorder = Color(0xFF263A60)
        private val StorageText = Color(0xFFF7FAFF)
        private val StorageMuted = Color(0xFF9BAAD0)
        private val StorageBlue = Color(0xFF387DFF)
        private val StorageCyan = Color(0xFF39DFFF)
    }
}
