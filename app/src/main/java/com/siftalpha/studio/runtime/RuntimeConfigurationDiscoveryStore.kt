package com.siftalpha.studio.runtime

import android.content.Context

/**
 * Shared, project-scoped persistence for runtime-discovered configuration facts.
 *
 * This deliberately uses the existing v1 preference contract that
 * ProjectConfigurationUiController already reads, so Normal Mode and Developer Mode see the same
 * runtime-discovered environment names / CLI tokens without creating a second configuration model.
 */
class RuntimeConfigurationDiscoveryStore(context: Context) {

    data class Snapshot(
        val discovered: Boolean,
        val environmentNames: Set<String>,
        val cliArguments: Set<String>,
    )

    data class RecordResult(
        val finding: RuntimeConfigurationDiagnostic.Result,
        val changed: Boolean,
    )

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(folderName: String): Snapshot = Snapshot(
        discovered = prefs.getBoolean(discoveredKey(folderName), false),
        environmentNames = prefs.getStringSet(hintsKey(folderName), emptySet()).orEmpty().toSet(),
        cliArguments = prefs.getStringSet(cliHintsKey(folderName), emptySet()).orEmpty().toSet(),
    )

    fun record(folderName: String, output: String): RecordResult {
        val finding = RuntimeConfigurationDiagnostic.inspect(output)
        if (!finding.hasActionableFinding) {
            return RecordResult(finding = finding, changed = false)
        }

        val before = read(folderName)
        val environmentNames = linkedSetOf<String>().apply {
            addAll(before.environmentNames)
            addAll(finding.missingEnvironmentNames)
        }
        val cliArguments = linkedSetOf<String>().apply {
            addAll(before.cliArguments)
            addAll(finding.missingCliArguments)
        }
        val changed =
            !before.discovered ||
                environmentNames != before.environmentNames ||
                cliArguments != before.cliArguments

        prefs.edit()
            .putBoolean(discoveredKey(folderName), true)
            .putStringSet(hintsKey(folderName), environmentNames)
            .putStringSet(cliHintsKey(folderName), cliArguments)
            .apply()

        return RecordResult(finding = finding, changed = changed)
    }

    fun clear(folderName: String) {
        prefs.edit()
            .remove(discoveredKey(folderName))
            .remove(hintsKey(folderName))
            .remove(cliHintsKey(folderName))
            .apply()
    }

    private fun discoveredKey(folderName: String): String = "discovered:" + folderName
    private fun hintsKey(folderName: String): String = "hints:" + folderName
    private fun cliHintsKey(folderName: String): String = "cli_hints:" + folderName

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_configuration_discovery_v1"
    }
}
