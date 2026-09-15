package com.siftalpha.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.siftalpha.studio.project.ConfigurationEvidence
import com.siftalpha.studio.project.ConfigurationItem
import com.siftalpha.studio.project.ConfigurationSeverity
import com.siftalpha.studio.project.ConfigurationSource
import com.siftalpha.studio.project.LegacyProjectConfigurationBridge
import com.siftalpha.studio.project.ProjectConfigurationInspector
import com.siftalpha.studio.project.ProjectSecretPolicyInspector
import com.siftalpha.studio.runtime.ProjectConfigurationPreflight
import com.siftalpha.studio.runtime.ProjectSecretStore
import com.siftalpha.studio.runtime.RuntimeConfigurationDiagnostic

/**
 * Product-level project configuration UX for Runtime Center.
 *
 * `.project.json.requiredEnv` is the authoritative generic configuration contract. The older
 * explicit `secrets.binanceApi=true` contract is bridged into the same model for backward
 * compatibility. `.env.example` and static Python inspection can surface useful candidates, but
 * they do not block the first run. A variable explicitly reported as missing by the running
 * project becomes required for the current repair cycle. Values saved through Studio stay in
 * Android Keystore-backed storage and are injected at runtime.
 */
class ProjectConfigurationUiController(
    private val activity: Activity,
    private val inspector: ProjectConfigurationInspector,
    private val store: ProjectSecretStore,
    private val onChanged: () -> Unit,
) {

    data class Snapshot(
        val profile: ProjectConfigurationInspector.Profile,
        val protectedKeys: Set<String>,
        val preflight: ProjectConfigurationPreflight.Result,
        val runtimeHints: Set<String>,
        val runtimeConfigurationDiscovered: Boolean,
    ) {
        val allCandidateNames: List<String>
            get() = (
                profile.optional
                    .filterNot { it.name in runtimeHints }
                    .map { it.name } +
                    profile.credentialCandidates
            )
                .distinct()
                .sorted()
    }

    private val runtimeHints = mutableMapOf<String, LinkedHashSet<String>>()
    private val runtimeDiscoveryFolders = mutableSetOf<String>()
    private val discoveryPrefs = activity.getSharedPreferences(DISCOVERY_PREFS, Context.MODE_PRIVATE)
    private val legacyPolicyInspector = ProjectSecretPolicyInspector(activity.applicationContext)

    private data class EditorField(
        val item: ConfigurationItem,
        val input: EditText,
    )

    internal data class ConfigurationSavePlan(
        val missingRequiredKeys: List<String>,
        val valuesToSave: Map<String, String>,
    ) {
        val canSave: Boolean
            get() = missingRequiredKeys.isEmpty()
    }

    fun snapshot(projectDocumentId: String, folderName: String): Snapshot {
        val inspected = runCatching { inspector.inspect(projectDocumentId) }
            .getOrElse { ProjectConfigurationInspector.emptyProfile() }
        val legacyPolicy = runCatching { legacyPolicyInspector.inspect(projectDocumentId) }
            .getOrElse {
                ProjectSecretPolicyInspector.Policy(ProjectSecretPolicyInspector.BinanceApiPolicy.UNSPECIFIED)
            }
        val profile = LegacyProjectConfigurationBridge.augment(inspected, legacyPolicy)
        val protected = runCatching { store.configuredEnvironmentKeys(folderName) }
            .getOrDefault(emptySet())
        val persistedHints = discoveryPrefs.getStringSet(hintsKey(folderName), emptySet()).orEmpty()
        if (persistedHints.isNotEmpty()) {
            runtimeHints.getOrPut(folderName) { linkedSetOf() }.addAll(persistedHints)
        }
        val knownHints = runtimeHints[folderName].orEmpty()
        return Snapshot(
            profile = profile,
            protectedKeys = protected,
            preflight = ProjectConfigurationPreflight.evaluate(
                profile = profile,
                protectedConfiguredKeys = protected,
                runtimeRequiredNames = knownHints,
            ),
            runtimeHints = knownHints,
            runtimeConfigurationDiscovered = folderName in runtimeDiscoveryFolders ||
                discoveryPrefs.getBoolean(discoveredKey(folderName), false),
        )
    }

    fun summaryText(snapshot: Snapshot): String = buildString {
        if (snapshot.preflight.missingRequired.isNotEmpty()) {
            append(
                activity.getString(
                    R.string.runtime_configuration_summary_required_missing,
                    snapshot.preflight.missingRequired.size,
                ),
            )
        } else if (snapshot.preflight.requiredCount > 0) {
            append(activity.getString(R.string.runtime_configuration_summary_required_complete))
        } else {
            append(activity.getString(R.string.runtime_configuration_summary_required_none))
        }

        when {
            snapshot.preflight.optionalMissingCount > 0 -> {
                append('\n')
                append(
                    activity.getString(
                        R.string.runtime_configuration_summary_optional_missing,
                        snapshot.preflight.optionalMissingCount,
                    ),
                )
            }
            snapshot.preflight.optionalCount > 0 -> {
                append('\n')
                append(activity.getString(R.string.runtime_configuration_summary_optional_complete))
            }
        }
    }

    fun statusText(snapshot: Snapshot): String = summaryText(snapshot)

    fun statusIsWarning(snapshot: Snapshot): Boolean =
        snapshot.preflight.missingRequired.isNotEmpty()

    fun hasOptionalReminders(snapshot: Snapshot): Boolean =
        snapshot.preflight.optionalMissingCount > 0

    fun clearRuntimeDiscovery(folderName: String) {
        runtimeHints.remove(folderName)
        runtimeDiscoveryFolders.remove(folderName)
        discoveryPrefs.edit()
            .remove(discoveredKey(folderName))
            .remove(hintsKey(folderName))
            .apply()
    }

    fun showConfiguration(
        projectName: String,
        projectDocumentId: String,
        folderName: String,
        onCompleted: () -> Unit = {},
    ) {
        val snapshot = snapshot(projectDocumentId, folderName)
        val items = buildItems(snapshot)
        if (items.isEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.runtime_configuration_editor_title, projectName))
                .setMessage(
                    summaryText(snapshot) + "\n\n" +
                        activity.getString(R.string.runtime_configuration_none),
                )
                .setPositiveButton(R.string.common_close, null)
                .show()
            return
        }

        // Configuration opens directly as an editor. REQUIRED remains enforceable by
        // ProjectActionPolicy, while OPTIONAL remains a reminder and never blocks START.
        showConfigurationEditor(
            projectName = projectName,
            projectDocumentId = projectDocumentId,
            folderName = folderName,
            currentSnapshot = snapshot,
            items = items,
            onCompleted = onCompleted,
        )
    }

    private fun showConfigurationEditor(
        projectName: String,
        projectDocumentId: String,
        folderName: String,
        currentSnapshot: Snapshot,
        items: List<ConfigurationItem>,
        onCompleted: () -> Unit,
    ) {
        lateinit var dialog: AlertDialog
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(4))
        }
        val fields = items.map { item ->
            val itemTitle = TextView(activity).apply {
                text = buildString {
                    append(item.key)
                    append(" · ")
                    append(
                        if (item.required) {
                            activity.getString(R.string.runtime_configuration_required_section)
                        } else {
                            activity.getString(R.string.runtime_configuration_item_optional)
                        },
                    )
                }
                textSize = 16f
                setPadding(0, dp(10), 0, dp(2))
            }
            val details = TextView(activity).apply {
                text = listOf(
                    stateLabel(currentSnapshot, item.key, item.required),
                    sourceLabel(item.source),
                    item.description.orEmpty(),
                    evidenceText(item.evidence),
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                textSize = 12f
                setPadding(0, 0, 0, dp(4))
            }
            val input = EditText(activity).apply {
                hint = activity.getString(
                    R.string.runtime_configuration_keep_existing_hint,
                    item.key,
                )
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or if (item.secret) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                }
            }
            content.addView(
                itemTitle,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            content.addView(
                details,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            content.addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(4)
                },
            )

            val configuredInStudio = runCatching {
                store.hasEnvironmentValue(folderName, item.key)
            }.getOrDefault(false)
            if (configuredInStudio) {
                content.addView(
                    Button(activity).apply {
                        text = activity.getString(R.string.runtime_configuration_clear)
                        setOnClickListener {
                            runCatching {
                                store.clearEnvironmentValue(folderName, item.key)
                            }.onSuccess {
                                toast(
                                    activity.getString(
                                        R.string.runtime_configuration_cleared,
                                        item.key,
                                    ),
                                )
                                dialog.dismiss()
                                onChanged()
                                showConfiguration(
                                    projectName = projectName,
                                    projectDocumentId = projectDocumentId,
                                    folderName = folderName,
                                    onCompleted = onCompleted,
                                )
                            }.onFailure {
                                errorDialog(
                                    activity.getString(R.string.runtime_configuration_clear_failed),
                                    it.message ?: it.javaClass.simpleName,
                                )
                            }
                        }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = dp(6)
                    },
                )
            }
            EditorField(item = item, input = input)
        }

        val scroll = ScrollView(activity).apply {
            addView(content)
        }
        val requiredWasMissing = items.any { it.required && !it.isConfigured }
        val message = activity.getString(R.string.runtime_configuration_editor_message) +
            "\n\n" + summaryText(currentSnapshot)
        dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.runtime_configuration_editor_title, projectName))
            .setMessage(message)
            .setView(scroll)
            .setNegativeButton(R.string.common_close, null)
            .setPositiveButton(R.string.runtime_configuration_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val enteredValues = fields.associate { field ->
                    field.item.key to field.input.text.toString()
                }
                val plan = planConfigurationSave(items, enteredValues)
                val missingRequired = plan.missingRequiredKeys.firstOrNull()
                if (missingRequired != null) {
                    toast(
                        activity.getString(
                            R.string.runtime_configuration_blank_value,
                            missingRequired,
                        ),
                    )
                    fields.firstOrNull { it.item.key == missingRequired }?.input?.requestFocus()
                    return@setOnClickListener
                }

                var savedCount = 0
                var failure: Throwable? = null
                for ((key, value) in plan.valuesToSave) {
                    try {
                        store.saveEnvironmentValue(folderName, key, value)
                        savedCount += 1
                    } catch (error: Throwable) {
                        failure = error
                        break
                    }
                }
                if (failure != null) {
                    if (savedCount > 0) onChanged()
                    errorDialog(
                        activity.getString(R.string.runtime_configuration_save_failed),
                        failure?.message ?: failure?.javaClass?.simpleName.orEmpty(),
                    )
                    return@setOnClickListener
                }

                dialog.dismiss()
                if (savedCount > 0) {
                    toast(activity.getString(R.string.runtime_configuration_editor_saved))
                    onChanged()
                    val refreshed = snapshot(projectDocumentId, folderName)
                    if (
                        requiredWasMissing &&
                        refreshed.preflight.missingRequired.isEmpty()
                    ) {
                        onCompleted()
                    }
                }
            }
        }
        dialog.show()
    }

    /** Returns true when a high-confidence runtime configuration finding was shown. */
    fun showRuntimeFindingIfAny(
        projectName: String,
        projectDocumentId: String,
        folderName: String,
        output: String,
        presentDialog: Boolean = true,
        onConfigurationCompleted: () -> Unit = {},
    ): Boolean {
        val finding = RuntimeConfigurationDiagnostic.inspect(output)
        if (!finding.hasActionableFinding) return false

        runtimeDiscoveryFolders += folderName

        if (finding.missingEnvironmentNames.isNotEmpty()) {
            val hints = runtimeHints.getOrPut(folderName) { linkedSetOf() }
            hints += finding.missingEnvironmentNames
            discoveryPrefs.edit()
                .putBoolean(discoveredKey(folderName), true)
                .putStringSet(hintsKey(folderName), hints.toSet())
                .apply()
            if (presentDialog) {
                val names = finding.missingEnvironmentNames.joinToString("\n") { "• $it" }
                AlertDialog.Builder(activity)
                    .setTitle(R.string.runtime_configuration_runtime_missing_title)
                    .setMessage(activity.getString(R.string.runtime_configuration_runtime_missing_message, names))
                    .setNegativeButton(R.string.common_close, null)
                    .setPositiveButton(R.string.runtime_configuration_button) { _, _ ->
                        showConfiguration(
                            projectName = projectName,
                            projectDocumentId = projectDocumentId,
                            folderName = folderName,
                            onCompleted = onConfigurationCompleted,
                        )
                    }
                    .show()
            }
            onChanged()
            return true
        }

        discoveryPrefs.edit()
            .putBoolean(discoveredKey(folderName), true)
            .apply()
        if (presentDialog) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.runtime_configuration_runtime_unnamed_title)
                .setMessage(R.string.runtime_configuration_runtime_unnamed_message)
                .setNegativeButton(R.string.common_close, null)
                .setPositiveButton(R.string.runtime_configuration_button) { _, _ ->
                    showConfiguration(
                        projectName = projectName,
                        projectDocumentId = projectDocumentId,
                        folderName = folderName,
                        onCompleted = onConfigurationCompleted,
                    )
                }
                .show()
        }
        onChanged()
        return true
    }

    private fun buildItems(snapshot: Snapshot): List<ConfigurationItem> {
        val result = linkedMapOf<String, ConfigurationItem>()

        fun add(requirement: ProjectConfigurationInspector.Requirement) {
            result.putIfAbsent(
                requirement.name,
                ConfigurationItem(
                    key = requirement.name,
                    isConfigured = isConfigured(snapshot, requirement.name),
                    severity = requirement.severity,
                    source = requirement.source,
                    description = requirement.description.takeIf { it.isNotBlank() },
                    evidence = requirement.evidence,
                    secret = requirement.secret,
                ),
            )
        }

        snapshot.profile.requirements.forEach(::add)
        snapshot.profile.configurationCandidates.forEach(::add)
        snapshot.profile.credentialCandidates.forEach { name ->
            if (name !in result) {
                add(
                    ProjectConfigurationInspector.Requirement(
                        name = name,
                        secret = true,
                        required = false,
                        description = "",
                        source = ConfigurationSource.STATIC_OPTIONAL_READ,
                    ),
                )
            }
        }

        // A variable explicitly reported as missing by the running project is REQUIRED for the
        // current repair cycle, even if static inspection originally classified it as optional.
        snapshot.runtimeHints.forEach { name ->
            val existing = result[name]
            result[name] = (existing ?: ConfigurationItem(
                key = name,
                isConfigured = isConfigured(snapshot, name),
                severity = ConfigurationSeverity.REQUIRED,
                source = ConfigurationSource.RUNTIME_DIAGNOSTIC,
                description = null,
                evidence = null,
                secret = ProjectConfigurationInspector.looksSensitive(name),
            )).copy(
                isConfigured = isConfigured(snapshot, name),
                severity = ConfigurationSeverity.REQUIRED,
                source = ConfigurationSource.RUNTIME_DIAGNOSTIC,
                evidence = ConfigurationEvidence(
                    detail = "Runtime reported missing configuration",
                ),
            )
        }
        return result.values.toList()
    }

    private fun isConfigured(snapshot: Snapshot, name: String): Boolean =
        name in snapshot.protectedKeys || name in snapshot.profile.configuredProjectEnvKeys

    private fun stateLabel(snapshot: Snapshot, name: String, required: Boolean): String = when {
        name in snapshot.protectedKeys ->
            activity.getString(R.string.runtime_configuration_item_protected)
        name in snapshot.profile.configuredProjectEnvKeys ->
            activity.getString(R.string.runtime_configuration_item_project_env)
        required -> activity.getString(R.string.runtime_configuration_item_missing)
        else -> activity.getString(R.string.runtime_configuration_item_optional)
    }

    private fun sourceLabel(source: ConfigurationSource): String = activity.getString(
        when (source) {
            ConfigurationSource.PROJECT_DECLARED ->
                R.string.runtime_configuration_source_project_declared
            ConfigurationSource.STATIC_REQUIRED_READ ->
                R.string.runtime_configuration_source_static_required
            ConfigurationSource.STATIC_OPTIONAL_READ ->
                R.string.runtime_configuration_source_static_optional
            ConfigurationSource.ENV_EXAMPLE ->
                R.string.runtime_configuration_source_env_example
            ConfigurationSource.RUNTIME_DIAGNOSTIC ->
                R.string.runtime_configuration_source_runtime_diagnostic
        },
    )

    private fun evidenceText(evidence: ConfigurationEvidence?): String {
        if (evidence == null) return ""
        val location = when {
            !evidence.filePath.isNullOrBlank() && evidence.lineNumber != null ->
                activity.getString(
                    R.string.runtime_configuration_evidence_file_line,
                    evidence.filePath,
                    evidence.lineNumber,
                )
            !evidence.filePath.isNullOrBlank() ->
                activity.getString(R.string.runtime_configuration_evidence_file, evidence.filePath)
            else -> ""
        }
        return listOf(location, evidence.detail.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun errorDialog(title: String, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.common_confirm, null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    private fun discoveredKey(folderName: String): String = "discovered:$folderName"
    private fun hintsKey(folderName: String): String = "hints:$folderName"

    companion object {
        internal fun planConfigurationSave(
            items: List<ConfigurationItem>,
            enteredValues: Map<String, String>,
        ): ConfigurationSavePlan {
            val missingRequiredKeys = items
                .filter { item ->
                    item.required &&
                        !item.isConfigured &&
                        enteredValues[item.key].orEmpty().isBlank()
                }
                .map { it.key }
            val valuesToSave = linkedMapOf<String, String>()
            items.forEach { item ->
                val value = enteredValues[item.key] ?: return@forEach
                if (value.isNotBlank()) {
                    valuesToSave[item.key] = value
                }
            }
            return ConfigurationSavePlan(
                missingRequiredKeys = missingRequiredKeys,
                valuesToSave = valuesToSave,
            )
        }

        private const val DISCOVERY_PREFS = "siftalpha_runtime_configuration_discovery_v1"
    }
}
