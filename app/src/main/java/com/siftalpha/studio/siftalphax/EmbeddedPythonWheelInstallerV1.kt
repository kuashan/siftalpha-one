package com.siftalpha.studio.siftalphax

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

class EmbeddedPythonWheelInstallerV1(
    private val cacheRoot: File,
) {
    data class InstalledEnvironment(
        val sitePackages: File,
        val environmentKey: String,
    )

    fun install(
        projectIdentity: String,
        environmentRoot: File,
        plan: EmbeddedPythonDependencyPlanV1,
    ): InstalledEnvironment {
        require(projectIdentity.isNotBlank())
        check(cacheRoot.mkdirs() || cacheRoot.isDirectory) { "unable to create wheel cache" }
        check(environmentRoot.mkdirs() || environmentRoot.isDirectory) {
            "unable to create project environment"
        }
        val target = File(environmentRoot, SITE_PACKAGES)
        val staging = File(environmentRoot, SITE_PACKAGES + ".install-" + UUID.randomUUID())
        val backup = File(environmentRoot, SITE_PACKAGES + ".backup")
        if (staging.exists()) check(staging.deleteRecursively())
        check(staging.mkdirs()) { "unable to create transactional site-packages staging" }

        try {
            val occupied = mutableSetOf<String>()
            var totalExtracted = 0L
            plan.packages.forEach { pkg ->
                val wheelFile = cachedWheel(pkg.wheel.wheel)
                totalExtracted += extractWheel(wheelFile, staging, occupied)
                require(totalExtracted <= MAX_ENVIRONMENT_BYTES) {
                    "installed environment exceeds $MAX_ENVIRONMENT_BYTES bytes"
                }
            }

            if (backup.exists()) check(backup.deleteRecursively()) {
                "unable to clear previous environment backup"
            }
            if (target.exists()) {
                check(target.renameTo(backup)) { "unable to stage previous site-packages backup" }
            }
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("unable to activate prepared site-packages")
            }
            if (backup.exists()) check(backup.deleteRecursively()) {
                "unable to remove previous site-packages backup"
            }

            val environmentKey = environmentKey(projectIdentity, plan.resolvedFingerprint)
            writeManifest(environmentRoot, projectIdentity, plan, environmentKey)
            File(environmentRoot, READY_MARKER).writeText(SCHEMA + "\n")
            File(environmentRoot, STATE_FILE).writeText("READY\n")
            return InstalledEnvironment(target.canonicalFile, environmentKey)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            File(environmentRoot, READY_MARKER).delete()
            throw error
        }
    }

    fun readReadyBinding(
        projectIdentity: String,
        environmentRoot: File,
        sourceFingerprint: String,
    ): InstalledEnvironment? {
        val marker = File(environmentRoot, READY_MARKER)
        val manifestFile = File(environmentRoot, MANIFEST_FILE)
        val sitePackages = File(environmentRoot, SITE_PACKAGES)
        if (!marker.isFile || !manifestFile.isFile || !sitePackages.isDirectory) return null
        val manifest = runCatching { JSONObject(manifestFile.readText()) }.getOrNull() ?: return null
        if (manifest.optString("schema") != SCHEMA) return null
        if (manifest.optString("projectIdentity") != projectIdentity) return null
        if (manifest.optString("sourceFingerprint") != sourceFingerprint) return null
        val environmentKey = manifest.optString("environmentKey")
        if (!environmentKey.matches(Regex("sha256:[0-9a-f]{64}"))) return null
        return InstalledEnvironment(sitePackages.canonicalFile, environmentKey)
    }

    private fun cachedWheel(wheel: EmbeddedPythonIndexWheelV1): File {
        PypiEmbeddedPythonPackageIndexV1.requireTrustedArtifactUrl(wheel.url)
        val target = File(cacheRoot, wheel.sha256 + ".whl")
        if (target.isFile && verifySha256(target, wheel.sha256)) return target
        if (target.exists()) target.delete()
        val temp = File(cacheRoot, wheel.sha256 + ".part-" + UUID.randomUUID())
        try {
            download(wheel.url, temp, wheel.size)
            check(verifySha256(temp, wheel.sha256)) {
                "WHEEL_HASH_MISMATCH: " + wheel.filename
            }
            check(temp.renameTo(target)) { "unable to activate verified wheel cache artifact" }
            return target
        } finally {
            temp.delete()
        }
    }

    private fun download(url: String, destination: File, expectedSize: Long?) {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            PypiEmbeddedPythonPackageIndexV1.requireTrustedArtifactUrl(current)
            val uri = URI(current)
            val connection = URL(current).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty(
                "User-Agent",
                PypiEmbeddedPythonPackageIndexV1.USER_AGENT,
            )
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("wheel redirect has no Location")
                current = uri.resolve(location).toString()
                connection.disconnect()
                return@repeat
            }
            check(code in 200..299) { "wheel download HTTP $code" }
            val contentLength = connection.contentLengthLong.takeIf { it > 0L }
            val declared = expectedSize ?: contentLength
            if (declared != null) {
                require(declared <= MAX_WHEEL_BYTES) { "wheel artifact exceeds size limit" }
            }
            connection.inputStream.use { raw ->
                BufferedInputStream(raw).use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_WHEEL_BYTES) {
                                "wheel artifact exceeds size limit"
                            }
                            output.write(buffer, 0, read)
                        }
                        expectedSize?.let { check(total == it) { "wheel artifact size mismatch" } }
                    }
                }
            }
            connection.disconnect()
            return
        }
        error("too many wheel download redirects")
    }

    private fun extractWheel(
        wheelFile: File,
        staging: File,
        occupied: MutableSet<String>,
    ): Long {
        var extracted = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(wheelFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries++
                require(entries <= MAX_WHEEL_ENTRIES) { "wheel contains too many entries" }
                val mapped = mappedWheelPath(entry.name)
                if (mapped != null && !entry.isDirectory) {
                    require(occupied.add(mapped)) { "wheel file collision: $mapped" }
                    val target = safeTarget(staging, mapped)
                    val parent = checkNotNull(target.parentFile)
                    check(parent.mkdirs() || parent.isDirectory) {
                        "unable to create wheel directory"
                    }
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var fileBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            extracted += read
                            require(fileBytes <= MAX_ENTRY_BYTES) {
                                "wheel entry exceeds size limit: $mapped"
                            }
                            require(extracted <= MAX_ENVIRONMENT_BYTES) {
                                "wheel extraction exceeds environment limit"
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return extracted
    }

    private fun mappedWheelPath(raw: String): String? {
        require(raw.isNotBlank() && !raw.startsWith("/") && '\\' !in raw && '\u0000' !in raw) {
            "unsafe wheel path"
        }
        val components = raw.split('/')
        require(components.none { it.isBlank() || it == "." || it == ".." }) {
            "unsafe wheel path"
        }
        val dataIndex = components.indexOfFirst { it.endsWith(".data") }
        if (dataIndex < 0) return raw
        require(dataIndex + 1 < components.size) { "invalid wheel .data layout" }
        return when (components[dataIndex + 1]) {
            "purelib", "platlib" -> components.drop(dataIndex + 2).joinToString("/")
                .takeIf { it.isNotBlank() }
            else -> error("UNSUPPORTED_WHEEL_LAYOUT: $raw")
        }
    }

    private fun safeTarget(root: File, relative: String): File {
        val target = File(root, relative)
        val rootPath = root.canonicalPath.trimEnd(File.separatorChar)
        val targetPath = target.canonicalPath
        require(targetPath.startsWith(rootPath + File.separator)) {
            "wheel path escaped staging root"
        }
        return target
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return actual == expected
    }

    private fun writeManifest(
        environmentRoot: File,
        projectIdentity: String,
        plan: EmbeddedPythonDependencyPlanV1,
        environmentKey: String,
    ) {
        val packages = JSONArray()
        plan.packages.forEach { pkg ->
            packages.put(
                JSONObject()
                    .put("name", pkg.normalizedName)
                    .put("version", pkg.version)
                    .put("wheel", pkg.wheel.wheel.filename)
                    .put("sha256", pkg.wheel.wheel.sha256)
                    .put("nativeAndroid", pkg.wheel.nativeAndroid),
            )
        }
        val manifest = JSONObject()
            .put("schema", SCHEMA)
            .put("projectIdentity", projectIdentity)
            .put("sourceFingerprint", plan.sourceFingerprint)
            .put("resolvedFingerprint", plan.resolvedFingerprint)
            .put("environmentKey", environmentKey)
            .put("packages", packages)
        File(environmentRoot, MANIFEST_FILE).writeText(manifest.toString(2) + "\n")
    }

    private fun environmentKey(projectIdentity: String, resolvedFingerprint: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((projectIdentity + "\n" + resolvedFingerprint).toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        const val SCHEMA = "siftalpha.internal-python-environment.v2"
        const val READY_MARKER = ".siftalpha_internal_environment_ready"
        const val STATE_FILE = "state-v1.txt"
        const val MANIFEST_FILE = "manifest-v2.json"
        const val SITE_PACKAGES = "site-packages"
        private const val MAX_REDIRECTS = 5
        private const val MAX_WHEEL_BYTES = 96L * 1024L * 1024L
        private const val MAX_ENTRY_BYTES = 128L * 1024L * 1024L
        private const val MAX_ENVIRONMENT_BYTES = 768L * 1024L * 1024L
        private const val MAX_WHEEL_ENTRIES = 20_000
    }
}
