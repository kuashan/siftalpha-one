package com.siftalpha.studio

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
 * The full RuntimeStorageActivity remains the frozen Developer Mode surface. This screen reuses
 * the same storage/runtime controllers but deliberately exposes only totals, per-project usage and
 * project-environment cleanup.
 */
class NormalRuntimeStorageActivity : StudioActivity() {

    private sealed interface Pending {
        object Snapshot : Pending
        data class CleanExternal(val folderName: String) : Pending
    }

    private lateinit var backend: TermuxBackend
    private lateinit var gateway: V04ProjectGateway
    private lateinit var runtime: ProjectRuntimeController
    private lateinit var externalStorage: RuntimeStorageController
    private lateinit var internalStorage: InternalRuntimeStorageController
    private lateinit var externalPreflight: ExternalProviderProbeCoordinator

    private lateinit var status: TextView
    private lateinit var internalContainer: LinearLayout
    private lateinit var externalContainer: LinearLayout

    private val pending = mutableMapOf<Int, Pending>()
    private val executor = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var projects: List<V04ProjectGateway.RuntimeProject> = emptyList()

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
                        errorDialog(result.stderr.ifBlank { result.stdout }.takeLast(800))
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
        setContentView(buildUi())
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

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 19, 24))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }
        scroll.addView(root)

        root.addView(title(getString(R.string.user_storage_title)))
        root.addView(hint(getString(R.string.user_storage_summary)))
        root.addView(button(getString(R.string.common_back)) { finish() })
        root.addView(button(getString(R.string.user_storage_refresh)) { refreshStorage() })

        status = hint(getString(R.string.user_storage_scanning))
        root.addView(status)

        root.addView(section(getString(R.string.user_storage_internal_title)))
        internalContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(internalContainer)

        root.addView(section(getString(R.string.user_storage_external_title)))
        externalContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(externalContainer)

        return scroll
    }

    private fun refreshStorage() {
        projects = if (gateway.rootUri() == null) {
            emptyList()
        } else {
            runCatching { gateway.projects() }.getOrDefault(emptyList())
        }
        status.text = getString(R.string.user_storage_scanning)

        val currentGeneration = ++generation
        internalContainer.removeAllViews()
        internalContainer.addView(hint(getString(R.string.user_storage_scanning_internal)))
        executor.execute {
            val snapshot = runCatching { internalStorage.snapshot(projects) }
            runOnUiThread {
                if (
                    currentGeneration != generation ||
                    isFinishing ||
                    isDestroyed
                ) return@runOnUiThread
                snapshot.onSuccess(::renderInternal)
                    .onFailure {
                        internalContainer.removeAllViews()
                        internalContainer.addView(hint(getString(R.string.user_storage_scan_failed)))
                    }
            }
        }

        externalContainer.removeAllViews()
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
        externalContainer.removeAllViews()
        externalContainer.addView(hint(getString(R.string.user_storage_scanning_external)))
        runCatching {
            val id = backend.execute(externalStorage.snapshot(projects))
            pending[id] = Pending.Snapshot
            TermuxResultBus.consume(id)?.let(resultListener)
        }.onFailure {
            renderExternalUnavailable()
        }
    }

    private fun renderExternalChecking() {
        externalContainer.removeAllViews()
        externalContainer.addView(hint(getString(R.string.user_storage_external_checking)))
    }

    private fun renderInternal(snapshot: InternalRuntimeStorageController.Snapshot) {
        internalContainer.removeAllViews()
        internalContainer.addView(
            metric(
                getString(
                    R.string.user_storage_total,
                    formatSize(snapshot.totalKb),
                ),
            ),
        )
        internalContainer.addView(
            metric(
                getString(
                    R.string.user_storage_project_total,
                    formatSize(snapshot.projectEnvironmentKb),
                ),
            ),
        )
        if (snapshot.projects.isEmpty()) {
            internalContainer.addView(hint(getString(R.string.user_storage_no_project_space)))
        } else {
            snapshot.projects.forEach { usage ->
                val project = projects.firstOrNull {
                    it.summary.documentId == usage.documentId
                }
                val card = card()
                card.addView(
                    metric(
                        getString(
                            R.string.user_storage_project_row,
                            usage.displayName,
                            formatSize(usage.totalKb),
                        ),
                    ),
                )
                if (project != null) {
                    card.addView(
                        button(getString(R.string.user_storage_clean_project)) {
                            confirmCleanInternal(project)
                        },
                    )
                }
                internalContainer.addView(card)
            }
        }
        updateReadyStatus()
    }

    private fun renderExternal(snapshot: RuntimeStorageController.Snapshot) {
        externalContainer.removeAllViews()
        if (!snapshot.ubuntuInstalled) {
            externalContainer.addView(hint(getString(R.string.user_storage_external_not_ready)))
            updateReadyStatus()
            return
        }
        externalContainer.addView(
            metric(
                getString(
                    R.string.user_storage_total,
                    formatSize(snapshot.ubuntuTotalKb),
                ),
            ),
        )
        externalContainer.addView(
            metric(
                getString(
                    R.string.user_storage_project_total,
                    formatSize(snapshot.projectRuntimeKb),
                ),
            ),
        )
        if (snapshot.projects.isEmpty()) {
            externalContainer.addView(hint(getString(R.string.user_storage_no_project_space)))
        } else {
            snapshot.projects.forEach { usage ->
                val project = projects.firstOrNull { it.folderName == usage.folderName }
                val card = card()
                card.addView(
                    metric(
                        getString(
                            R.string.user_storage_project_row,
                            project?.summary?.name ?: usage.folderName,
                            formatSize(usage.sizeKb),
                        ),
                    ),
                )
                if (project != null) {
                    card.addView(
                        button(getString(R.string.user_storage_clean_project)) {
                            confirmCleanExternal(project)
                        },
                    )
                }
                externalContainer.addView(card)
            }
        }
        updateReadyStatus()
    }

    private fun renderExternalUnavailable() {
        externalContainer.removeAllViews()
        externalContainer.addView(hint(getString(R.string.user_storage_external_unavailable)))
        updateReadyStatus()
    }

    private fun confirmCleanInternal(project: V04ProjectGateway.RuntimeProject) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.user_storage_clean_title, project.summary.name))
            .setMessage(getString(R.string.user_storage_clean_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.user_storage_clean_confirm)) { _, _ ->
                status.text = getString(R.string.user_storage_cleaning)
                executor.execute {
                    val result = runCatching { runtime.cleanEmbeddedPythonEnvironment(project) }
                    runOnUiThread {
                        result.onSuccess {
                            toast(getString(R.string.user_storage_clean_done))
                            refreshStorage()
                        }.onFailure { error ->
                            errorDialog(
                                error.message ?: getString(R.string.user_storage_clean_failed),
                            )
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmCleanExternal(project: V04ProjectGateway.RuntimeProject) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.user_storage_clean_title, project.summary.name))
            .setMessage(getString(R.string.user_storage_clean_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.user_storage_clean_confirm)) { _, _ ->
                status.text = getString(R.string.user_storage_cleaning)
                runCatching {
                    val id = backend.execute(runtime.clean(project))
                    pending[id] = Pending.CleanExternal(project.folderName)
                    TermuxResultBus.consume(id)?.let(resultListener)
                }.onFailure { error ->
                    errorDialog(error.message ?: getString(R.string.user_storage_clean_failed))
                }
            }
            .show()
    }

    private fun updateReadyStatus() {
        status.text = getString(R.string.user_storage_ready)
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 25f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun section(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun hint(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.rgb(176, 184, 198))
        setPadding(0, dp(4), 0, dp(10))
    }

    private fun metric(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(226, 230, 238))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun formatSize(kb: Long): String =
        RuntimeStorageSizeFormatter.formatKilobytes(kb)

    private fun errorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.user_storage_error))
            .setMessage(message)
            .setPositiveButton(getString(R.string.common_confirm), null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
