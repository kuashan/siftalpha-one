package com.siftalpha.studio.siftalphax

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

data class EmbeddedPythonPackageMetadataV1(
    val normalizedName: String,
    val version: String,
    val requiresPython: String?,
    val requiresDist: List<String>,
)

interface EmbeddedPythonPackageIndexV1 {
    fun listWheels(normalizedName: String): List<EmbeddedPythonIndexWheelV1>
    fun metadata(normalizedName: String, version: String): EmbeddedPythonPackageMetadataV1
}

class PypiEmbeddedPythonPackageIndexV1 : EmbeddedPythonPackageIndexV1 {
    override fun listWheels(normalizedName: String): List<EmbeddedPythonIndexWheelV1> {
        require(normalizedName.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
            "normalized package name is invalid"
        }
        val endpoint = "https://pypi.org/simple/" + encode(normalizedName) + "/"
        val root = JSONObject(readHttps(endpoint, SIMPLE_JSON_LIMIT, SIMPLE_ACCEPT))
        val files = root.optJSONArray("files") ?: JSONArray()
        return buildList {
            for (index in 0 until files.length()) {
                val item = files.optJSONObject(index) ?: continue
                val filename = item.optString("filename")
                if (!filename.endsWith(".whl")) continue
                val hashes = item.optJSONObject("hashes") ?: continue
                val sha256 = hashes.optString("sha256")
                if (!sha256.matches(Regex("[0-9a-f]{64}"))) continue
                val absoluteUrl = URI(endpoint).resolve(item.optString("url")).toString()
                requireTrustedArtifactUrl(absoluteUrl)
                val size = if (item.has("size") && !item.isNull("size")) {
                    item.optLong("size").takeIf { it > 0L }
                } else {
                    null
                }
                val requiresPython = item.optString("requires-python")
                    .takeIf { it.isNotBlank() && it != "null" }
                val yankedValue = item.opt("yanked")
                val yanked = when (yankedValue) {
                    null, JSONObject.NULL, false -> false
                    is String -> yankedValue.isNotBlank()
                    else -> true
                }
                add(
                    EmbeddedPythonIndexWheelV1(
                        filename = filename,
                        url = absoluteUrl,
                        sha256 = sha256,
                        size = size,
                        requiresPython = requiresPython,
                        yanked = yanked,
                    ),
                )
            }
        }
    }

    override fun metadata(
        normalizedName: String,
        version: String,
    ): EmbeddedPythonPackageMetadataV1 {
        require(normalizedName.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        require(version.matches(Regex("[A-Za-z0-9._+!-]+"))) { "package version is invalid" }
        val endpoint = "https://pypi.org/pypi/" + encode(normalizedName) + "/" + encode(version) + "/json"
        val root = JSONObject(readHttps(endpoint, METADATA_JSON_LIMIT, "application/json"))
        val info = root.getJSONObject("info")
        val requiresDist = info.optJSONArray("requires_dist")?.let { array ->
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.orEmpty()
        return EmbeddedPythonPackageMetadataV1(
            normalizedName = normalizedName,
            version = info.optString("version").ifBlank { version },
            requiresPython = info.optString("requires_python")
                .takeIf { it.isNotBlank() && it != "null" },
            requiresDist = requiresDist,
        )
    }

    private fun readHttps(url: String, maxBytes: Int, accept: String): String {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val uri = URI(current)
            require(uri.scheme == "https" && uri.userInfo == null && !uri.host.isNullOrBlank()) {
                "package index URL must be HTTPS"
            }
            val connection = URL(current).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("package index redirect has no Location")
                current = uri.resolve(location).toString()
                connection.disconnect()
                return@repeat
            }
            check(code in 200..299) { "package index HTTP $code for $current" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "package index response exceeds $maxBytes bytes" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            connection.disconnect()
            return bytes.toString(Charsets.UTF_8)
        }
        error("too many package index redirects")
    }

    companion object {
        const val USER_AGENT = "SiftAlpha-InternalPython/1"
        private const val SIMPLE_ACCEPT = "application/vnd.pypi.simple.v1+json"
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val SIMPLE_JSON_LIMIT = 16 * 1024 * 1024
        private const val METADATA_JSON_LIMIT = 4 * 1024 * 1024

        fun requireTrustedArtifactUrl(url: String) {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.userInfo == null) {
                "wheel artifact URL must be HTTPS without credentials"
            }
            val host = uri.host?.lowercase().orEmpty()
            require(
                host == "files.pythonhosted.org" ||
                    host == "pypi.org" ||
                    host.endsWith(".pythonhosted.org")
            ) {
                "wheel artifact host is not trusted: $host"
            }
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
