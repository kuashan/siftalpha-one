package com.siftalpha.studio.runtime

import android.content.Context
import com.siftalpha.studio.storage.SiftAlphaStorage
import org.json.JSONObject
import java.io.File

class ResultWebStore(context: Context) {

    data class ResultRef(
        val id: String,
        val projectKey: String,
        val projectName: String,
        val createdAtEpochMs: Long,
        val summary: String,
        val fingerprint: String,
    )

    data class SaveResult(
        val ref: ResultRef,
        val changed: Boolean,
    )

    private val appContext = context.applicationContext
    private val root = SiftAlphaStorage.resultsRoot(appContext.filesDir).apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveIfChanged(
        projectKey: String,
        projectName: String,
        document: AdaptiveResultDocument,
        html: String,
    ): SaveResult {
        val fingerprint = ResultFingerprint.sha256(
            projectKey + "\n" + document.rawText + "\n" + document.stderr,
        )
        val projectToken = ResultFingerprint.sha256(projectKey).take(24)
        val latestFingerprint = prefs.getString(fingerprintKey(projectToken), null)
        val latestId = prefs.getString(latestIdKey(projectToken), null)
        if (latestFingerprint == fingerprint && !latestId.isNullOrBlank()) {
            readRef(latestId)?.let { existing ->
                if (htmlFile(existing.id).isFile) {
                    return SaveResult(existing, changed = false)
                }
            }
        }

        val now = System.currentTimeMillis()
        val id = now.toString(36) + "-" + fingerprint.take(12)
        val ref = ResultRef(
            id = id,
            projectKey = projectKey,
            projectName = projectName,
            createdAtEpochMs = now,
            summary = document.summary,
            fingerprint = fingerprint,
        )
        htmlFile(id).writeText(html, Charsets.UTF_8)
        metaFile(id).writeText(
            JSONObject().apply {
                put("id", ref.id)
                put("projectKey", ref.projectKey)
                put("projectName", ref.projectName)
                put("createdAtEpochMs", ref.createdAtEpochMs)
                put("summary", ref.summary)
                put("fingerprint", ref.fingerprint)
            }.toString(),
            Charsets.UTF_8,
        )
        prefs.edit()
            .putString(latestIdKey(projectToken), id)
            .putString(fingerprintKey(projectToken), fingerprint)
            .apply()
        trimHistory()
        return SaveResult(ref, changed = true)
    }

    fun latest(projectKey: String): ResultRef? {
        val projectToken = ResultFingerprint.sha256(projectKey).take(24)
        val id = prefs.getString(latestIdKey(projectToken), null) ?: return null
        val ref = readRef(id) ?: return null
        return ref.takeIf { htmlFile(it.id).isFile }
    }

    fun readHtml(id: String): String? {
        if (!SAFE_ID.matches(id)) return null
        val file = htmlFile(id)
        if (!file.isFile || file.length() > MAX_HTML_BYTES) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun listRecent(limit: Int = 50): List<ResultRef> =
        root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(META_SUFFIX) }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(META_SUFFIX)
                readRef(id)
            }
            .filter { htmlFile(it.id).isFile }
            .sortedByDescending { it.createdAtEpochMs }
            .take(limit.coerceIn(1, MAX_RESULTS))
            .toList()

    private fun readRef(id: String): ResultRef? {
        if (!SAFE_ID.matches(id)) return null
        val file = metaFile(id)
        if (!file.isFile || file.length() > MAX_META_BYTES) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val parsed = ResultRef(
                id = json.getString("id"),
                projectKey = json.getString("projectKey"),
                projectName = json.getString("projectName"),
                createdAtEpochMs = json.getLong("createdAtEpochMs"),
                summary = json.optString("summary"),
                fingerprint = json.getString("fingerprint"),
            )
            parsed.takeIf { SAFE_ID.matches(it.id) }
        }.getOrNull()
    }

    private fun trimHistory() {
        val all = root.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(META_SUFFIX) }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(META_SUFFIX)
                readRef(id)
            }
            .sortedByDescending { it.createdAtEpochMs }
        all.drop(MAX_RESULTS).forEach { old ->
            runCatching { metaFile(old.id).delete() }
            runCatching { htmlFile(old.id).delete() }
        }
    }

    private fun htmlFile(id: String): File = File(root, id + HTML_SUFFIX)
    private fun metaFile(id: String): File = File(root, id + META_SUFFIX)
    private fun latestIdKey(projectToken: String): String = "latest_id:" + projectToken
    private fun fingerprintKey(projectToken: String): String = "latest_fingerprint:" + projectToken

    companion object {
        private const val PREFS = "siftalpha_result_web_store_v1"
        private const val HTML_SUFFIX = ".html"
        private const val META_SUFFIX = ".meta.json"
        private const val MAX_RESULTS = 100
        private const val MAX_HTML_BYTES = 4L * 1024L * 1024L
        private const val MAX_META_BYTES = 32L * 1024L
        private val SAFE_ID = Regex("^[a-z0-9-]{8,80}$")
    }
}
