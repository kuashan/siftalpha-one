package com.siftalpha.studio.siftalphax

import java.net.URI
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Alpha45 Pure-Python Environment v1 lock contract.
 *
 * This parser deliberately stops before dependency resolution, network fetch, wheel extraction,
 * environment installation, or project execution. It consumes only an already-resolved pylock.toml
 * and returns bounded, typed facts for later R environment preparation slices.
 */
enum class EmbeddedPythonPylockV1FailureCode {
    TOML_PARSE_ERROR,
    LOCK_INVALID,
    PACKAGE_UNSUPPORTED,
}

sealed interface EmbeddedPythonPylockV1ParseResult {
    data class Accepted(
        val document: EmbeddedPythonPylockV1Document,
    ) : EmbeddedPythonPylockV1ParseResult

    data class Rejected(
        val code: EmbeddedPythonPylockV1FailureCode,
        val detail: String,
    ) : EmbeddedPythonPylockV1ParseResult
}

data class EmbeddedPythonPylockV1Document(
    val lockVersion: String,
    val requiresPython: String,
    val environments: List<String>,
    val createdBy: String,
    val packages: List<EmbeddedPythonPylockV1Package>,
)

data class EmbeddedPythonPylockV1Package(
    val name: String,
    val normalizedName: String,
    val version: String,
    val marker: String?,
    val requiresPython: String?,
    val wheels: List<EmbeddedPythonPylockV1Wheel>,
)

data class EmbeddedPythonPylockV1Wheel(
    val filename: String,
    val url: String?,
    val projectRelativePath: String?,
    val size: Long,
    val sha256: String,
)

object EmbeddedPythonPylockV1Parser {
    private const val SUPPORTED_LOCK_VERSION = "1.0"
    private const val MAX_LOCK_UTF8_BYTES = 4 * 1024 * 1024
    private const val MAX_PACKAGES = 4_096
    private const val MAX_WHEELS_PER_PACKAGE = 128
    private const val MAX_TEXT_FIELD_UTF8_BYTES = 8 * 1024

    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val distributionNamePattern =
        Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")
    private val nameNormalizer = Regex("[-_.]+")

    fun parse(text: String): EmbeddedPythonPylockV1ParseResult {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_LOCK_UTF8_BYTES) {
            return rejected(
                EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
                "lock exceeds $MAX_LOCK_UTF8_BYTES UTF-8 bytes",
            )
        }

        val parsed = Toml.parse(text)
        if (parsed.hasErrors()) {
            val summary = parsed.errors()
                .take(3)
                .joinToString(" | ") { it.toString().take(512) }
            return rejected(
                EmbeddedPythonPylockV1FailureCode.TOML_PARSE_ERROR,
                summary.ifBlank { "invalid TOML" },
            )
        }

        return try {
            val lockVersion = requiredString(parsed, "lock-version", "lock-version")
            if (lockVersion != SUPPORTED_LOCK_VERSION) {
                invalid("lock-version must be $SUPPORTED_LOCK_VERSION")
            }

            // SiftAlpha v1 intentionally requires this field even though the base standard
            // permits omission. The worker must never guess interpreter compatibility.
            val requiresPython = requiredString(parsed, "requires-python", "requires-python")
            val createdBy = requiredString(parsed, "created-by", "created-by")
            val environments = optionalStringArray(parsed, "environments", "environments")

            requireEmptySelectionArray(parsed, "extras")
            requireEmptySelectionArray(parsed, "dependency-groups")
            requireEmptySelectionArray(parsed, "default-groups")

            if (!parsed.contains("packages") || !parsed.isArray("packages")) {
                invalid("packages must be an array of tables")
            }
            val packageArray = requireNotNull(parsed.getArray("packages"))
            if (packageArray.size() > MAX_PACKAGES) {
                invalid("packages exceeds $MAX_PACKAGES entries")
            }

            val packages = buildList(packageArray.size()) {
                for (index in 0 until packageArray.size()) {
                    val packageTable = packageArray.get(index) as? TomlTable
                        ?: invalid("packages[$index] must be a table")
                    add(parsePackage(packageTable, index))
                }
            }

            EmbeddedPythonPylockV1ParseResult.Accepted(
                EmbeddedPythonPylockV1Document(
                    lockVersion = lockVersion,
                    requiresPython = requiresPython,
                    environments = environments,
                    createdBy = createdBy,
                    packages = packages,
                ),
            )
        } catch (failure: ContractFailure) {
            EmbeddedPythonPylockV1ParseResult.Rejected(
                code = failure.code,
                detail = failure.message.orEmpty(),
            )
        }
    }

    private fun parsePackage(
        table: TomlTable,
        packageIndex: Int,
    ): EmbeddedPythonPylockV1Package {
        val path = "packages[$packageIndex]"
        val name = requiredString(table, "name", "$path.name")
        if (!distributionNamePattern.matches(name)) {
            invalid("$path.name is not a valid distribution name")
        }
        val normalizedName = nameNormalizer.replace(name.lowercase(), "-")
        val version = requiredString(table, "version", "$path.version")
        val marker = optionalString(table, "marker", "$path.marker")
        val requiresPython = optionalString(table, "requires-python", "$path.requires-python")

        val unsupportedSource = listOf("vcs", "directory", "archive", "sdist")
            .firstOrNull { table.contains(it) }
        if (unsupportedSource != null) {
            unsupported("$path.$unsupportedSource is outside Pure-Python Environment v1")
        }

        if (!table.contains("wheels") || !table.isArray("wheels")) {
            invalid("$path.wheels must contain at least one wheel")
        }
        val wheelArray = requireNotNull(table.getArray("wheels"))
        if (wheelArray.size() == 0) {
            invalid("$path.wheels must contain at least one wheel")
        }
        if (wheelArray.size() > MAX_WHEELS_PER_PACKAGE) {
            invalid("$path.wheels exceeds $MAX_WHEELS_PER_PACKAGE entries")
        }

        val wheels = buildList(wheelArray.size()) {
            for (wheelIndex in 0 until wheelArray.size()) {
                val wheelTable = wheelArray.get(wheelIndex) as? TomlTable
                    ?: invalid("$path.wheels[$wheelIndex] must be a table")
                add(parseWheel(wheelTable, "$path.wheels[$wheelIndex]"))
            }
        }

        return EmbeddedPythonPylockV1Package(
            name = name,
            normalizedName = normalizedName,
            version = version,
            marker = marker,
            requiresPython = requiresPython,
            wheels = wheels,
        )
    }

    private fun parseWheel(
        table: TomlTable,
        path: String,
    ): EmbeddedPythonPylockV1Wheel {
        val url = optionalString(table, "url", "$path.url")
        val projectRelativePath = optionalString(table, "path", "$path.path")
        if ((url == null) == (projectRelativePath == null)) {
            invalid("$path must contain exactly one of url or path")
        }

        if (url != null) {
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                invalid("$path.url is not a valid URI")
            }
            if (
                uri.scheme?.equals("https", ignoreCase = true) != true ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null
            ) {
                invalid("$path.url must be an HTTPS URL without embedded credentials")
            }
        }

        if (projectRelativePath != null) {
            validateProjectRelativePath(projectRelativePath, "$path.path")
        }

        val explicitFilename = optionalString(table, "name", "$path.name")
        val filename = explicitFilename
            ?: artifactBasename(url = url, projectRelativePath = projectRelativePath)
            ?: invalid("$path requires a wheel filename")
        if (
            filename.isBlank() ||
            filename.contains('/') ||
            filename.contains('\\') ||
            !filename.endsWith(".whl")
        ) {
            invalid("$path wheel filename must be a basename ending in .whl")
        }

        if (!table.contains("size") || !table.isLong("size")) {
            invalid("$path.size must be a positive integer")
        }
        val size = table.getLong("size")
        if (size <= 0L) {
            invalid("$path.size must be a positive integer")
        }

        if (!table.contains("hashes") || !table.isTable("hashes")) {
            invalid("$path.hashes must contain sha256")
        }
        val hashes = requireNotNull(table.getTable("hashes"))
        if (!hashes.contains("sha256") || !hashes.isString("sha256")) {
            invalid("$path.hashes.sha256 is required")
        }
        val sha256 = requireNotNull(hashes.getString("sha256"))
        if (!sha256Pattern.matches(sha256)) {
            invalid("$path.hashes.sha256 must be 64 lowercase hex characters")
        }

        return EmbeddedPythonPylockV1Wheel(
            filename = filename,
            url = url,
            projectRelativePath = projectRelativePath,
            size = size,
            sha256 = sha256,
        )
    }

    private fun validateProjectRelativePath(value: String, path: String) {
        if (
            value.startsWith("/") ||
            value.startsWith("\\") ||
            value.contains('\u0000') ||
            value.contains('\\')
        ) {
            invalid("$path must be a safe project-relative POSIX path")
        }
        val parts = value.split('/')
        if (parts.isEmpty() || parts.any { it.isBlank() || it == "." || it == ".." }) {
            invalid("$path must be a safe project-relative POSIX path")
        }
    }

    private fun artifactBasename(
        url: String?,
        projectRelativePath: String?,
    ): String? {
        if (projectRelativePath != null) {
            return projectRelativePath.substringAfterLast('/').takeIf { it.isNotBlank() }
        }
        if (url != null) {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            return uri.path.substringAfterLast('/').takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun requiredString(
        table: TomlTable,
        key: String,
        path: String,
    ): String {
        if (!table.contains(key) || !table.isString(key)) {
            invalid("$path must be a string")
        }
        return checkedText(requireNotNull(table.getString(key)), path, requireNonBlank = true)
    }

    private fun optionalString(
        table: TomlTable,
        key: String,
        path: String,
    ): String? {
        if (!table.contains(key)) return null
        if (!table.isString(key)) {
            invalid("$path must be a string")
        }
        return checkedText(requireNotNull(table.getString(key)), path, requireNonBlank = true)
    }

    private fun optionalStringArray(
        table: TomlTable,
        key: String,
        path: String,
    ): List<String> {
        if (!table.contains(key)) return emptyList()
        if (!table.isArray(key)) {
            invalid("$path must be an array of strings")
        }
        return stringArray(requireNotNull(table.getArray(key)), path)
    }

    private fun requireEmptySelectionArray(table: TomlTable, key: String) {
        if (!table.contains(key)) return
        if (!table.isArray(key)) {
            invalid("$key must be an array")
        }
        val values = stringArray(requireNotNull(table.getArray(key)), key)
        if (values.isNotEmpty()) {
            unsupported("$key is not supported by Pure-Python Environment v1")
        }
    }

    private fun stringArray(array: TomlArray, path: String): List<String> =
        buildList(array.size()) {
            for (index in 0 until array.size()) {
                val value = array.get(index) as? String
                    ?: invalid("$path[$index] must be a string")
                add(checkedText(value, "$path[$index]", requireNonBlank = true))
            }
        }

    private fun checkedText(
        value: String,
        path: String,
        requireNonBlank: Boolean,
    ): String {
        if (value.contains('\u0000')) {
            invalid("$path must not contain NUL")
        }
        if (value.toByteArray(Charsets.UTF_8).size > MAX_TEXT_FIELD_UTF8_BYTES) {
            invalid("$path exceeds $MAX_TEXT_FIELD_UTF8_BYTES UTF-8 bytes")
        }
        if (requireNonBlank && value.isBlank()) {
            invalid("$path must not be blank")
        }
        return value
    }

    private fun rejected(
        code: EmbeddedPythonPylockV1FailureCode,
        detail: String,
    ): EmbeddedPythonPylockV1ParseResult.Rejected =
        EmbeddedPythonPylockV1ParseResult.Rejected(code = code, detail = detail)

    private fun invalid(message: String): Nothing =
        throw ContractFailure(EmbeddedPythonPylockV1FailureCode.LOCK_INVALID, message)

    private fun unsupported(message: String): Nothing =
        throw ContractFailure(EmbeddedPythonPylockV1FailureCode.PACKAGE_UNSUPPORTED, message)

    private class ContractFailure(
        val code: EmbeddedPythonPylockV1FailureCode,
        message: String,
    ) : IllegalArgumentException(message)
}
