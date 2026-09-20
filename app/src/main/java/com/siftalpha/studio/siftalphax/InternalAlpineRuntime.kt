package com.siftalpha.studio.siftalphax

import android.content.Context
import android.system.Os
import com.siftalpha.studio.runtime.InternalRuntimeForegroundService
import com.siftalpha.studio.runtime.InternalRuntimeOwnedProcess
import com.siftalpha.studio.runtime.InterruptibleProjectTreeDelete
import com.siftalpha.studio.runtime.RuntimeOperationContract
import android.system.OsConstants
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.tomlj.Toml

enum class InternalPythonBackend {
    CPYTHON,
    ALPINE,
}

data class InternalAlpineDependencySource(
    val kind: Kind,
    val sourceFingerprint: String,
    val legacySourceFingerprint: String = sourceFingerprint,
    val requiresNodeVite: Boolean = false,
    val pythonInstallExtras: List<String> = emptyList(),
    val projectRequiresPython: String? = null,
) {
    enum class Kind { REQUIREMENTS_TXT, PYPROJECT_TOML, NONE }

    companion object {
        fun fromProjectFiles(
            requirementsText: String?,
            pyprojectText: String?,
            requiresNodeVite: Boolean = false,
            pythonInstallExtras: List<String> = emptyList(),
        ): InternalAlpineDependencySource {
            val kind: Kind
            val source: String
            val projectRequiresPython = pyprojectText?.let(::extractRequiresPython)
            when {
                requiresNodeVite && pyprojectText != null -> {
                    kind = Kind.PYPROJECT_TOML
                    source = "pyproject.toml\n" + pyprojectText.replace("\r\n", "\n").replace("\r", "\n")
                }
                requirementsText != null -> {
                    kind = Kind.REQUIREMENTS_TXT
                    source = "requirements.txt\n" + requirementsText.replace("\r\n", "\n").replace("\r", "\n")
                }
                pyprojectText != null -> {
                    kind = Kind.PYPROJECT_TOML
                    source = "pyproject.toml\n" + pyprojectText.replace("\r\n", "\n").replace("\r", "\n")
                }
                else -> {
                    kind = Kind.NONE
                    source = "none\n"
                }
            }
            val legacyFingerprintSource = source +
                "\nNODE_VITE=" + (if (requiresNodeVite) "1" else "0")
            val normalizedExtras = pythonInstallExtras
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            require(normalizedExtras.all { PYTHON_EXTRA_NAME.matches(it) }) {
                "Invalid Python install extra in Environment Plan"
            }
            val fingerprintSource = legacyFingerprintSource +
                "\nREQUIRES_PYTHON=" + projectRequiresPython.orEmpty() +
                "\nPYTHON_INSTALL_EXTRAS=" + normalizedExtras.joinToString(",")
            fun fingerprint(value: String): String =
                "sha256:" + MessageDigest.getInstance("SHA-256")
                    .digest(value.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return InternalAlpineDependencySource(
                kind = kind,
                sourceFingerprint = fingerprint(fingerprintSource),
                legacySourceFingerprint = fingerprint(legacyFingerprintSource),
                requiresNodeVite = requiresNodeVite,
                pythonInstallExtras = normalizedExtras,
                projectRequiresPython = projectRequiresPython,
            )
        }

        private val PYTHON_EXTRA_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

        private fun extractRequiresPython(pyprojectText: String): String? {
            val parsed = Toml.parse(pyprojectText)
            if (parsed.hasErrors()) return null
            return parsed.getTable("project")
                ?.getString("requires-python")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }
}

internal object InternalAlpinePythonRuntimeBootstrap {
    private const val DEFAULT_MAX_ATTEMPTS = 4

    fun installCommand(maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): String {
        require(maxAttempts >= 1) { "maxAttempts must be positive" }
        return """
            set -eu
            apk_attempt=1
            while :; do
              set +e
              apk add --no-cache ca-certificates python3 py3-pip py3-virtualenv
              apk_code=${'$'}?
              set -e
              if [ "${'$'}apk_code" -eq 0 ]; then
                break
              fi
              if [ "${'$'}apk_attempt" -ge "$maxAttempts" ]; then
                printf 'SIFTALPHA_INTERNAL_ALPINE_APK_FAILED attempt=%s/%s exit=%s\n' "${'$'}apk_attempt" "$maxAttempts" "${'$'}apk_code"
                exit "${'$'}apk_code"
              fi
              printf 'SIFTALPHA_INTERNAL_ALPINE_APK_RETRY attempt=%s/%s exit=%s\n' "${'$'}apk_attempt" "$maxAttempts" "${'$'}apk_code"
              sleep_seconds=${'$'}((apk_attempt * 2))
              sleep "${'$'}sleep_seconds"
              apk_attempt=${'$'}((apk_attempt + 1))
            done
            python3 --version
            virtualenv --version
        """.trimIndent()
    }
}

internal object InternalAlpineNodeRuntimeBootstrap {
    private const val DEFAULT_MAX_ATTEMPTS = 4

    fun installCommand(maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): String {
        require(maxAttempts >= 1) { "maxAttempts must be positive" }
        return """
            set -eu
            apk_attempt=1
            while :; do
              set +e
              apk add --no-cache nodejs npm
              apk_code=${'$'}?
              set -e
              if [ "${'$'}apk_code" -eq 0 ]; then
                break
              fi
              if [ "${'$'}apk_attempt" -ge "$maxAttempts" ]; then
                printf 'SIFTALPHA_INTERNAL_ALPINE_NODE_APK_FAILED attempt=%s/%s exit=%s\n' "${'$'}apk_attempt" "$maxAttempts" "${'$'}apk_code"
                exit "${'$'}apk_code"
              fi
              printf 'SIFTALPHA_INTERNAL_ALPINE_NODE_APK_RETRY attempt=%s/%s exit=%s\n' "${'$'}apk_attempt" "$maxAttempts" "${'$'}apk_code"
              sleep_seconds=${'$'}((apk_attempt * 2))
              sleep "${'$'}sleep_seconds"
              apk_attempt=${'$'}((apk_attempt + 1))
            done
            node --version
            npm --version
        """.trimIndent()
    }
}

internal object InternalAlpineViteBuildBootstrap {
    fun buildCommand(): String = """
        set -eu
        components_file="/tmp/siftalpha-vite-components-${'$'}${'$'}"
        trap 'rm -f "${'$'}components_file"' EXIT
        : >"${'$'}components_file"
        find /workspace -maxdepth 7 -type f -name package.json \
          ! -path '*/node_modules/*' ! -path '*/.git/*' \
          ! -path '*/dist/*' ! -path '*/build/*' 2>/dev/null | LC_ALL=C sort | \
        while IFS= read -r package_json; do
          package_dir="${'$'}{package_json%/package.json}"
          if [ -f "${'$'}package_dir/vite.config.ts" ] || \
             [ -f "${'$'}package_dir/vite.config.js" ] || \
             [ -f "${'$'}package_dir/vite.config.mts" ] || \
             [ -f "${'$'}package_dir/vite.config.mjs" ] || \
             [ -f "${'$'}package_dir/vite.config.cjs" ]; then
            printf '%s\n' "${'$'}package_json"
          fi
        done >"${'$'}components_file"

        component_count="${'$'}(wc -l <"${'$'}components_file" | tr -d ' ')"
        if [ "${'$'}component_count" -lt 1 ]; then
          echo 'SIFTALPHA_NODE_ENV=NOT_REQUIRED'
          exit 0
        fi

        printf 'SIFTALPHA_NODE_COMPONENTS=%s\n' "${'$'}component_count"
        while IFS= read -r package_json; do
          [ -n "${'$'}package_json" ] || continue
          package_dir="${'$'}{package_json%/package.json}"
          printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=VITE_INSTALL path=%s\n' "${'$'}package_dir"
          if [ -f "${'$'}package_dir/pnpm-lock.yaml" ] || [ -f "${'$'}package_dir/yarn.lock" ]; then
            echo 'SIFTALPHA_NODE_DIAG=UNSUPPORTED_PACKAGE_MANAGER'
            exit 68
          fi
          (
            cd "${'$'}package_dir"
            if [ -f package-lock.json ] || [ -f npm-shrinkwrap.json ]; then
              npm ci --no-audit --no-fund
            else
              npm install --no-audit --no-fund --package-lock=false
            fi
            printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=VITE_BUILD path=%s\n' "${'$'}package_dir"
            npm run build
          )
        done <"${'$'}components_file"
        echo 'SIFTALPHA_NODE_ENV=READY'
    """.trimIndent()
}
internal object InternalAlpineDependencyBootstrap {
    private const val PIP_COMMON =
        "--disable-pip-version-check --no-input --no-compile --timeout 30 --retries 4"

    fun installCommand(
        kind: InternalAlpineDependencySource.Kind,
        installExtras: List<String> = emptyList(),
    ): String = buildString {
        val normalizedExtras = installExtras
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        require(normalizedExtras.all { EXTRA_NAME.matches(it) }) {
            "Invalid Python install extra in Environment Plan"
        }
        append("set -eu\n")
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=CREATE_VENV\\n'\n")
        append("virtualenv /siftalpha-env/venv\n")
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=UPGRADE_PIP\\n'\n")
        append("/siftalpha-env/venv/bin/python -m pip install ")
        append(PIP_COMMON)
        append(" --upgrade pip\n")
        when (kind) {
            InternalAlpineDependencySource.Kind.REQUIREMENTS_TXT -> {
                append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=INSTALL_DEPENDENCIES\\n'\n")
                append("/siftalpha-env/venv/bin/python -m pip install ")
                append(PIP_COMMON)
                append(" -r /workspace/requirements.txt\n")
            }
            InternalAlpineDependencySource.Kind.PYPROJECT_TOML -> {
                append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=INSTALL_DEPENDENCIES\\n'\n")
                val extras = normalizedExtras.joinToString(",")
                if (extras.isNotEmpty()) {
                    append("echo 'SIFTALPHA_PYPROJECT_EXTRAS=")
                    append(extras)
                    append("'\n")
                    append("/siftalpha-env/venv/bin/python -m pip install ")
                    append(PIP_COMMON)
                    append(" '/workspace[")
                    append(extras)
                    append("]'\n")
                } else {
                    append("echo 'SIFTALPHA_PYPROJECT_EXTRAS=none'\n")
                    append("/siftalpha-env/venv/bin/python -m pip install ")
                    append(PIP_COMMON)
                    append(" /workspace\n")
                }
            }
            InternalAlpineDependencySource.Kind.NONE -> Unit
        }
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=PIP_CHECK\\n'\n")
        append("/siftalpha-env/venv/bin/python -m pip check\n")
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=VERIFY_PYTHON\\n'\n")
        append("/siftalpha-env/venv/bin/python -c 'import sys; print(sys.version)'\n")
    }

    private val EXTRA_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")
}

internal object InternalAlpinePrepareWatchdog {
    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val OUTPUT_IDLE_TIMEOUT_MS = 12L * 60L * 1_000L
    const val HARD_TIMEOUT_MS = 30L * 60L * 1_000L

    fun violation(elapsedMillis: Long, outputIdleMillis: Long): String? = when {
        elapsedMillis >= HARD_TIMEOUT_MS -> "HARD_TIMEOUT"
        outputIdleMillis >= OUTPUT_IDLE_TIMEOUT_MS -> "OUTPUT_IDLE_TIMEOUT"
        else -> null
    }
}

internal object InternalAlpineProcessControl {
    private const val MAX_DESCENDANTS = 2048

    fun throwIfCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Internal Alpine operation cancelled")
        }
    }

    fun terminate(
        managed: InternalAlpineManagedProcess,
        gracefulMillis: Long = 1500L,
    ) {
        val process = managed.process
        val procRoot = File("/proc")
        val rootPid = managed.hostPid()?.toLong()
        val descendants = rootPid?.let { descendantPids(procRoot, it) }.orEmpty()

        descendants.forEach { signal(it, OsConstants.SIGTERM) }
        rootPid?.toInt()?.let { signal(it, OsConstants.SIGTERM) }
        runCatching { process.destroy() }
        runCatching { process.waitFor(gracefulMillis, TimeUnit.MILLISECONDS) }

        val rootAlive = rootPid?.toInt()?.let { pidAlive(procRoot, it) } == true
        val childAlive = descendants.any { pidAlive(procRoot, it) }
        if (process.isAlive || rootAlive || childAlive) {
            val refreshed = rootPid?.let { descendantPids(procRoot, it) }.orEmpty()
            (descendants + refreshed).distinct().forEach {
                signal(it, OsConstants.SIGKILL)
            }
            rootPid?.toInt()?.let { signal(it, OsConstants.SIGKILL) }
            if (process.isAlive) {
                runCatching { process.destroyForcibly() }
                runCatching {
                    process.waitFor(gracefulMillis, TimeUnit.MILLISECONDS)
                }
            }
        }
        managed.cleanup()
    }

    internal fun descendantPids(procRoot: File, rootPid: Long): List<Int> {
        if (rootPid !in 1..Int.MAX_VALUE.toLong()) return emptyList()
        val root = rootPid.toInt()
        val visited = linkedSetOf<Int>()
        val result = mutableListOf<Int>()

        fun visit(parent: Int) {
            if (visited.size >= MAX_DESCENDANTS || !visited.add(parent)) return
            val childrenFile = File(procRoot, "$parent/task/$parent/children")
            val children = runCatching { childrenFile.readText() }
                .getOrDefault("")
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.toIntOrNull() }
                .filter { it > 0 }
            children.forEach { child ->
                visit(child)
                if (child != root && child !in result) result += child
            }
        }

        visit(root)
        return result
    }

    private fun pidAlive(procRoot: File, pid: Int): Boolean =
        pid > 0 && File(procRoot, pid.toString()).exists()

    private fun signal(pid: Int, signal: Int) {
        if (pid <= 0) return
        runCatching { Os.kill(pid, signal) }
    }
}

class InternalAlpineEnvironmentManager(context: Context) {
    private val appContext = context.applicationContext

    enum class Outcome { READY_REUSED, READY_ENVIRONMENT_INSTALLED }

    data class PreparationResult(
        val ready: Boolean,
        val outcome: Outcome,
        val environmentRoot: File,
        val environmentKey: String,
    )

    data class LoadBinding(val environmentRoot: File, val environmentKey: String)

    private data class PythonRuntimeIdentity(
        val fullVersion: String,
        val identity: String,
    )

    @Synchronized
    fun prepare(
        projectIdentity: String,
        stagedProject: File,
        source: InternalAlpineDependencySource,
        progress: ((String) -> Unit)? = null,
    ): PreparationResult {
        require(projectIdentity.isNotBlank())
        InternalAlpineProcessControl.throwIfCancelled()
        progress?.invoke(progressText(projectIdentity, "ALPINE_RUNTIME", ""))
        val layout = InternalAlpineFiles.prepare(appContext)
        InternalAlpineProcessControl.throwIfCancelled()
        val pythonRuntime = ensurePythonRuntime(layout, projectIdentity, progress)
        requirePythonCompatibility(source, pythonRuntime)
        InternalAlpineProcessControl.throwIfCancelled()
        if (source.requiresNodeVite) {
            ensureNodeRuntime(layout, projectIdentity, progress)
            InternalAlpineProcessControl.throwIfCancelled()
        } else {
            loadBinding(projectIdentity, source)?.let {
                return PreparationResult(true, Outcome.READY_REUSED, it.environmentRoot, it.environmentKey)
            }
        }

        val environmentRoot = InternalAlpineFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val parent = environmentRoot.parentFile ?: error("Internal Alpine environment has no parent")
        val temp = File(parent, environmentRoot.name + ".install-" + UUID.randomUUID())
        check(temp.mkdirs())
        val log = File(temp, "prepare.log")
        try {
            if (source.requiresNodeVite) {
                runCommand(
                    builder = InternalAlpineFiles.buildCommand(
                        appContext,
                        layout,
                        InternalAlpineViteBuildBootstrap.buildCommand(),
                        binds = listOf(stagedProject to "/workspace"),
                        workingDirectory = "/workspace",
                    ),
                    logFile = log,
                    failurePrefix = "INTERNAL_ALPINE_VITE_PREPARE_FAILED",
                    projectIdentity = projectIdentity,
                    stage = "ALPINE_VITE_BUILD",
                    progress = progress,
                )
                InternalAlpineProcessControl.throwIfCancelled()
            }
            val command = InternalAlpineDependencyBootstrap.installCommand(
                kind = source.kind,
                installExtras = source.pythonInstallExtras,
            )
            runCommand(
                builder = InternalAlpineFiles.buildCommand(
                    appContext,
                    layout,
                    command,
                    binds = listOf(stagedProject to "/workspace", temp to "/siftalpha-env"),
                    workingDirectory = "/workspace",
                ),
                logFile = log,
                failurePrefix = "INTERNAL_ALPINE_DEPENDENCY_PREPARE_FAILED",
                projectIdentity = projectIdentity,
                stage = "ALPINE_PROJECT_DEPENDENCIES",
                progress = progress,
            )
            val environmentKey = environmentKey(
                projectIdentity = projectIdentity,
                fingerprint = source.sourceFingerprint,
                runtimeIdentity = pythonRuntime.identity,
            )
            File(temp, READY_MARKER).writeText(
                "BACKEND=ALPINE\nSOURCE_FINGERPRINT=" + source.sourceFingerprint +
                    "\nPYTHON_VERSION=" + pythonRuntime.fullVersion +
                    "\nRUNTIME_IDENTITY=" + pythonRuntime.identity +
                    "\nENVIRONMENT_KEY=" + environmentKey + "\n",
            )
            if (environmentRoot.exists()) {
                InterruptibleProjectTreeDelete.delete(
                    environmentRoot = environmentRoot,
                    allowedParent = checkNotNull(environmentRoot.parentFile),
                    deadlineNanos = System.nanoTime() +
                        RuntimeOperationContract.PREPARE_TIMEOUT_MS * 1_000_000L,
                )
            }
            check(temp.renameTo(environmentRoot)) { "Unable to activate Internal Alpine environment" }
            return PreparationResult(true, Outcome.READY_ENVIRONMENT_INSTALLED, environmentRoot, environmentKey)
        } catch (error: Throwable) {
            temp.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun cleanProjectEnvironment(projectIdentity: String) {
        require(projectIdentity.isNotBlank())
        InternalAlpineProcessControl.throwIfCancelled()
        val root = InternalAlpineFiles.projectEnvironmentRoot(appContext, projectIdentity)
        if (root.exists()) {
            InterruptibleProjectTreeDelete.delete(
                environmentRoot = root,
                allowedParent = checkNotNull(root.parentFile),
                deadlineNanos = System.nanoTime() +
                    RuntimeOperationContract.CLEAN_TIMEOUT_MS * 1_000_000L,
            )
        }
        InternalAlpineProcessControl.throwIfCancelled()
    }

    @Synchronized
    fun loadBinding(
        projectIdentity: String,
        source: InternalAlpineDependencySource,
    ): LoadBinding? {
        val root = InternalAlpineFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val marker = File(root, READY_MARKER)
        if (!marker.isFile) return null
        val values = readMarker(marker)
        if (values["BACKEND"] != "ALPINE") return null
        val key = values["ENVIRONMENT_KEY"]?.takeIf { it.isNotBlank() } ?: return null
        val python = File(root, "venv/bin/python")
        if (!Files.exists(python.toPath(), LinkOption.NOFOLLOW_LINKS)) return null

        val runtimeRoot = InternalAlpineFiles.rootfsDirectory(appContext)
        val runtime = readPythonRuntimeIdentity(runtimeRoot)
        if (runtime != null) {
            if (!pythonRequirementMatches(source.projectRequiresPython, runtime.fullVersion)) return null
            if (
                values["SOURCE_FINGERPRINT"] == source.sourceFingerprint &&
                values["PYTHON_VERSION"] == runtime.fullVersion &&
                values["RUNTIME_IDENTITY"] == runtime.identity
            ) {
                return LoadBinding(root, key)
            }
            if (
                !values.containsKey("PYTHON_VERSION") &&
                !values.containsKey("RUNTIME_IDENTITY") &&
                values["SOURCE_FINGERPRINT"] == source.legacySourceFingerprint &&
                InternalAlpineFiles.rootfsMatchesCurrentAssets(appContext)
            ) {
                writeProjectBindingMarker(marker, source.sourceFingerprint, runtime, key)
                return LoadBinding(root, key)
            }
            return null
        }

        return migrateLegacyBinding(
            root = root,
            marker = marker,
            values = values,
            key = key,
            source = source,
            runtimeRoot = runtimeRoot,
        )
    }

    private fun migrateLegacyBinding(
        root: File,
        marker: File,
        values: Map<String, String>,
        key: String,
        source: InternalAlpineDependencySource,
        runtimeRoot: File,
    ): LoadBinding? {
        if (values.containsKey("PYTHON_VERSION") || values.containsKey("RUNTIME_IDENTITY")) return null
        if (values["SOURCE_FINGERPRINT"] != source.legacySourceFingerprint) return null
        if (!InternalAlpineFiles.rootfsMatchesCurrentAssets(appContext)) return null

        val runtimeMarker = File(runtimeRoot, PYTHON_READY_MARKER)
        val runtimeValues = readMarker(runtimeMarker)
        if (runtimeValues["READY"] != "1") return null
        if (
            runtimeValues.containsKey("PYTHON_VERSION") ||
            runtimeValues.containsKey("RUNTIME_IDENTITY")
        ) return null

        val pythonVersion = readLegacyVenvPythonVersion(root) ?: return null
        if (!pythonRequirementMatches(source.projectRequiresPython, pythonVersion)) return null
        val runtimeIdentity = InternalAlpineFiles.pythonRuntimeIdentity(pythonVersion)

        runtimeMarker.writeText(
            "READY=1\nPYTHON_VERSION=" + pythonVersion +
                "\nRUNTIME_IDENTITY=" + runtimeIdentity + "\n",
        )
        writeProjectBindingMarker(
            marker = marker,
            sourceFingerprint = source.sourceFingerprint,
            runtime = PythonRuntimeIdentity(pythonVersion, runtimeIdentity),
            key = key,
        )
        return LoadBinding(root, key)
    }

    private fun writeProjectBindingMarker(
        marker: File,
        sourceFingerprint: String,
        runtime: PythonRuntimeIdentity,
        key: String,
    ) {
        marker.writeText(
            "BACKEND=ALPINE\nSOURCE_FINGERPRINT=" + sourceFingerprint +
                "\nPYTHON_VERSION=" + runtime.fullVersion +
                "\nRUNTIME_IDENTITY=" + runtime.identity +
                "\nENVIRONMENT_KEY=" + key + "\n",
        )
    }

    private fun readLegacyVenvPythonVersion(root: File): String? {
        val config = File(root, "venv/pyvenv.cfg")
        if (!config.isFile) return null
        val text = runCatching { config.readText() }.getOrNull() ?: return null
        return legacyVenvPythonVersion(text)
    }

    private fun readMarker(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            file.readLines().mapNotNull {
                val index = it.indexOf('=')
                if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun ensurePythonRuntime(
        layout: InternalAlpineLayout,
        projectIdentity: String,
        progress: ((String) -> Unit)?,
    ): PythonRuntimeIdentity {
        InternalAlpineProcessControl.throwIfCancelled()
        val marker = File(layout.rootfs, PYTHON_READY_MARKER)
        val python = File(layout.rootfs, "usr/bin/python3")
        val virtualenv = File(layout.rootfs, "usr/bin/virtualenv")
        if (
            marker.isFile &&
            Files.exists(python.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            Files.exists(virtualenv.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            readPythonRuntimeIdentity(layout.rootfs)?.let { return it }
        }

        if (
            !Files.exists(python.toPath(), LinkOption.NOFOLLOW_LINKS) ||
            !Files.exists(virtualenv.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            val log = File(layout.rootfs.parentFile, "python-runtime-prepare.log")
            runCommand(
                builder = InternalAlpineFiles.buildCommand(
                    appContext,
                    layout,
                    InternalAlpinePythonRuntimeBootstrap.installCommand(),
                ),
                logFile = log,
                failurePrefix = "INTERNAL_ALPINE_PYTHON_RUNTIME_PREPARE_FAILED",
                projectIdentity = projectIdentity,
                stage = "ALPINE_PYTHON_RUNTIME",
                progress = progress,
            )
            InternalAlpineProcessControl.throwIfCancelled()
        }

        val runtime = probePythonRuntimeIdentity(layout, projectIdentity, progress)
        marker.writeText(
            "READY=1\nPYTHON_VERSION=" + runtime.fullVersion +
                "\nRUNTIME_IDENTITY=" + runtime.identity + "\n",
        )
        return runtime
    }

    private fun probePythonRuntimeIdentity(
        layout: InternalAlpineLayout,
        projectIdentity: String,
        progress: ((String) -> Unit)?,
    ): PythonRuntimeIdentity {
        val log = File(layout.rootfs.parentFile, "python-runtime-version.log")
        runCommand(
            builder = InternalAlpineFiles.buildCommand(
                appContext,
                layout,
                "python3 --version",
            ),
            logFile = log,
            failurePrefix = "INTERNAL_ALPINE_PYTHON_RUNTIME_IDENTITY_FAILED",
            projectIdentity = projectIdentity,
            stage = "ALPINE_PYTHON_RUNTIME_IDENTITY",
            progress = progress,
        )
        val fullVersion = Regex("""Python\s+([0-9]+\.[0-9]+\.[0-9]+)""")
            .find(log.readText())
            ?.groupValues
            ?.getOrNull(1)
            ?: error("INTERNAL_ALPINE_PYTHON_RUNTIME_VERSION_UNRESOLVED")
        return PythonRuntimeIdentity(
            fullVersion = fullVersion,
            identity = InternalAlpineFiles.pythonRuntimeIdentity(fullVersion),
        )
    }

    private fun readPythonRuntimeIdentity(rootfs: File): PythonRuntimeIdentity? {
        val marker = File(rootfs, PYTHON_READY_MARKER)
        if (!marker.isFile) return null
        val values = marker.readLines().mapNotNull {
            val index = it.indexOf('=')
            if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
        }.toMap()
        val version = values["PYTHON_VERSION"]
            ?.takeIf { it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) }
            ?: return null
        val identity = values["RUNTIME_IDENTITY"]?.takeIf { it.isNotBlank() } ?: return null
        if (identity != InternalAlpineFiles.pythonRuntimeIdentity(version)) return null
        return PythonRuntimeIdentity(version, identity)
    }

    private fun requirePythonCompatibility(
        source: InternalAlpineDependencySource,
        runtime: PythonRuntimeIdentity,
    ) {
        val required = source.projectRequiresPython ?: return
        check(pythonRequirementMatches(required, runtime.fullVersion)) {
            "PROJECT_REQUIRES_PYTHON_UNAVAILABLE: requires-python=" + required +
                " available=" + runtime.fullVersion + " backend=ALPINE"
        }
    }

    private fun pythonRequirementMatches(required: String?, fullVersion: String): Boolean =
        required == null ||
            runCatching {
                EmbeddedPythonRequirementParserV1.versionMatches(required, fullVersion)
            }.getOrDefault(false)

    private fun ensureNodeRuntime(
        layout: InternalAlpineLayout,
        projectIdentity: String,
        progress: ((String) -> Unit)?,
    ) {
        InternalAlpineProcessControl.throwIfCancelled()
        val marker = File(layout.rootfs, NODE_READY_MARKER)
        if (
            marker.isFile &&
            Files.exists(File(layout.rootfs, "usr/bin/node").toPath(), LinkOption.NOFOLLOW_LINKS) &&
            Files.exists(File(layout.rootfs, "usr/bin/npm").toPath(), LinkOption.NOFOLLOW_LINKS)
        ) return

        val log = File(layout.rootfs.parentFile, "node-runtime-prepare.log")
        runCommand(
            builder = InternalAlpineFiles.buildCommand(
                appContext,
                layout,
                InternalAlpineNodeRuntimeBootstrap.installCommand(),
            ),
            logFile = log,
            failurePrefix = "INTERNAL_ALPINE_NODE_RUNTIME_PREPARE_FAILED",
            projectIdentity = projectIdentity,
            stage = "ALPINE_NODE_RUNTIME",
            progress = progress,
        )
        InternalAlpineProcessControl.throwIfCancelled()
        marker.writeText("READY=1\n")
    }

    private fun runCommand(
        builder: InternalAlpineCommand,
        logFile: File,
        failurePrefix: String,
        projectIdentity: String,
        stage: String,
        progress: ((String) -> Unit)?,
    ) {
        logFile.parentFile?.mkdirs()
        builder.redirectErrorStream(true).redirectOutput(logFile)
        progress?.invoke(progressText(projectIdentity, stage, "", 0L, 0L, true))
        val managed = builder.start()
        val process = managed.process
        val startedAt = System.currentTimeMillis()
        var lastObservedTail = ""
        var lastOutputChangedAt = startedAt
        var lastPublishedAt = 0L

        fun publishProgress(force: Boolean = false) {
            val callback = progress ?: return
            val now = System.currentTimeMillis()
            val tail = readTail(logFile, PROGRESS_TAIL_CHARS)
            if (tail != lastObservedTail) {
                lastObservedTail = tail
                lastOutputChangedAt = now
            }
            val dueHeartbeat = now - lastPublishedAt >= InternalAlpinePrepareWatchdog.HEARTBEAT_INTERVAL_MS
            if (!force && !dueHeartbeat) return
            lastPublishedAt = now
            callback(
                progressText(
                    projectIdentity = projectIdentity,
                    stage = stage,
                    tail = tail,
                    elapsedMillis = now - startedAt,
                    outputIdleMillis = now - lastOutputChangedAt,
                    processAlive = process.isAlive,
                ),
            )
        }

        try {
            while (true) {
                if (process.waitFor(250, TimeUnit.MILLISECONDS)) break
                publishProgress()
                val now = System.currentTimeMillis()
                val violation = InternalAlpinePrepareWatchdog.violation(
                    elapsedMillis = now - startedAt,
                    outputIdleMillis = now - lastOutputChangedAt,
                )
                if (violation != null) {
                    InternalAlpineProcessControl.terminate(managed)
                    error(
                        failurePrefix +
                            ": " + violation +
                            " elapsed_sec=" + ((now - startedAt) / 1_000L) +
                            " output_idle_sec=" + ((now - lastOutputChangedAt) / 1_000L) +
                            "\n" + readTail(logFile, 12_000),
                    )
                }
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Internal Alpine operation cancelled")
                }
            }
            publishProgress(force = true)
        } catch (cancelled: InterruptedException) {
            InternalAlpineProcessControl.terminate(managed)
            Thread.currentThread().interrupt()
            throw IllegalStateException("INTERNAL_ALPINE_OPERATION_CANCELLED", cancelled)
        }
        managed.cleanup()
        if (process.exitValue() != 0) {
            error(failurePrefix + ": exit=" + process.exitValue() + "\n" + readTail(logFile, 12_000))
        }
    }

    private fun progressText(
        projectIdentity: String,
        stage: String,
        tail: String,
        elapsedMillis: Long = 0L,
        outputIdleMillis: Long = 0L,
        processAlive: Boolean = true,
    ): String = buildString {
        appendLine("SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R")
        appendLine("SIFTALPHA_X_PROJECT_ID=" + projectIdentity)
        appendLine("SIFTALPHA_X_ENVIRONMENT_STAGE=PREPARING")
        appendLine("SIFTALPHA_X_INTERNAL_PREPARE_STAGE=" + stage)
        appendLine("SIFTALPHA_X_INTERNAL_PREPARE_PROCESS_ALIVE=" + processAlive)
        appendLine("SIFTALPHA_X_INTERNAL_PREPARE_ELAPSED_SEC=" + (elapsedMillis / 1_000L))
        append("SIFTALPHA_X_INTERNAL_PREPARE_OUTPUT_IDLE_SEC=" + (outputIdleMillis / 1_000L))
        if (tail.isNotBlank()) {
            appendLine()
            appendLine()
            append(tail.trimEnd())
        }
    }

    private fun environmentKey(
        projectIdentity: String,
        fingerprint: String,
        runtimeIdentity: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                ("alpine\n" + projectIdentity + "\n" + fingerprint + "\n" + runtimeIdentity + "\n")
                    .toByteArray(),
            )
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "alpine:$digest"
    }

    companion object {
        private const val READY_MARKER = "siftalpha-alpine-environment-ready.txt"
        private const val PYTHON_READY_MARKER = ".siftalpha-python-runtime-ready"
        private const val NODE_READY_MARKER = ".siftalpha-node-runtime-ready"
        private const val PROGRESS_INTERVAL_MS = 750L
        private const val PROGRESS_TAIL_CHARS = 16_000

        internal fun legacyVenvPythonVersion(configText: String): String? =
            Regex(
                """(?m)^(?:version_info|version)\s*=\s*([0-9]+\.[0-9]+\.[0-9]+)""",
            ).find(configText)?.groupValues?.getOrNull(1)

        internal fun readTail(file: File, maxChars: Int): String {
            if (!file.isFile) return ""
            val text = runCatching { file.readText() }.getOrDefault("")
            return if (text.length <= maxChars) text else text.takeLast(maxChars)
        }
    }
}


internal object InternalRuntimeProcDiagnostics {
    fun cpuTicks(pid: Int): Long? {
        if (pid <= 0) return null
        val stat = runCatching { File("/proc/$pid/stat").readText() }.getOrNull() ?: return null
        return cpuTicksFromStat(stat)
    }

    internal fun cpuTicksFromStat(stat: String): Long? {
        val closingParen = stat.lastIndexOf(')')
        if (closingParen < 0 || closingParen + 2 >= stat.length) return null
        val fields = stat.substring(closingParen + 2)
            .trim()
            .split(Regex("""\s+"""))
        val userTicks = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val systemTicks = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return userTicks + systemTicks
    }
}

class InternalAlpineSession private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val nextGeneration = AtomicLong(0L)
    private val records = ConcurrentHashMap<String, Record>()

    private class Record(
        val sessionId: String,
        val projectIdentity: String,
        val executionRoot: File,
        val entrypoint: String,
        val generation: Long,
        val stdout: File,
        val stderr: File,
        val backgroundTelemetry: File,
        val startedAt: Long,
        val foregroundReadyAtEpochMs: Long,
        val foregroundServicePid: Int?,
        val runtimeHostPid: Int?,
        val runtimeCpuTicksAtLaunch: Long?,
        @Volatile var state: EmbeddedPythonState,
        @Volatile var finishedAt: Long? = null,
        @Volatile var exitCode: Int? = null,
        @Volatile var stopRequested: Boolean = false,
    )

    fun canStart(projectIdentity: String): Boolean {
        val state = records[projectIdentity]?.state ?: return true
        return EmbeddedPythonStatePolicy.isTerminal(state)
    }

    fun snapshot(projectIdentity: String): EmbeddedPythonSnapshot? =
        records[projectIdentity]?.let(::snapshotOf)

    fun webObservation(
        projectIdentity: String,
        expectedSessionId: String,
        expectedGeneration: Long,
        preferredHints: Collection<Int> = emptyList(),
    ): InternalAlpineWebObservation? {
        val record = records[projectIdentity] ?: return null
        if (!InternalAlpineWebDiscovery.belongsToExecution(
                expectedSessionId = expectedSessionId,
                expectedGeneration = expectedGeneration,
                currentSessionId = record.sessionId,
                currentGeneration = record.generation,
            )
        ) return null
        if (!EmbeddedPythonStatePolicy.canStop(record.state)) return null
        val hostPid = record.runtimeHostPid ?: return InternalAlpineWebObservation.empty()
        return InternalAlpineWebDiscovery.observe(
            procRoot = File("/proc"),
            rootPid = hostPid,
            preferredHints = preferredHints,
        )
    }

    fun start(
        projectIdentity: String,
        executionRoot: File,
        entrypoint: String,
        environmentRoot: File,
        arguments: List<String> = emptyList(),
        consoleScript: String? = null,
    ): EmbeddedPythonSnapshot {
        check(canStart(projectIdentity)) { "Internal Alpine project session is already active" }
        val layout = InternalAlpineFiles.prepare(appContext)
        val sessionId = "siftalpha-alpine-" + UUID.randomUUID()
        val sessionRoot = InternalAlpineFiles.sessionRoot(appContext, sessionId)
        val stdout = File(sessionRoot, "stdout.log")
        val stderr = File(sessionRoot, "stderr.log")
        val backgroundTelemetry = File(sessionRoot, "background-continuity.log")
        val generation = nextGeneration.incrementAndGet()
        require(arguments.size <= 64) { "too many Python arguments" }
        require(arguments.all { it.length <= 4096 && '\u0000' !in it }) {
            "invalid Python argument"
        }
        fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\"'\"'") + "'"
        val safeConsoleScript = consoleScript?.also {
            require(it.matches(Regex("^[A-Za-z0-9._-]+$")) && it != "." && it != "..") {
                "invalid Python console script"
            }
        }
        val commandParts = buildList {
            if (safeConsoleScript != null) {
                add("/siftalpha-env/venv/bin/" + safeConsoleScript)
            } else {
                add("/siftalpha-env/venv/bin/python")
                add("/workspace/" + entrypoint)
            }
            addAll(arguments)
        }
        val launchKind = if (safeConsoleScript != null) "CONSOLE_SCRIPT" else "PYTHON_FILE"
        val launchExecutable = safeConsoleScript ?: "python"
        val shell = buildString {
            append("printf 'SIFTALPHA_LAUNCH_KIND=%s\\n' ")
            append(shellQuote(launchKind))
            append("; printf 'SIFTALPHA_LAUNCH_EXECUTABLE=%s\\n' ")
            append(shellQuote(launchExecutable))
            append("; printf 'SIFTALPHA_LAUNCH_ARGUMENT_COUNT=%s\\n' ")
            append(shellQuote(arguments.size.toString()))
            append("; exec ")
            append(commandParts.joinToString(" ", transform = ::shellQuote))
        }
        val command = InternalAlpineFiles.buildCommand(
            appContext,
            layout,
            shell,
            binds = listOf(executionRoot to "/workspace", environmentRoot to "/siftalpha-env"),
            workingDirectory = "/workspace",
        )
        command.redirectOutput(stdout).redirectError(stderr)

        var startedRecord: Record? = null
        try {
            InternalRuntimeForegroundService.launchAndOwn(
                context = appContext,
                sessionLeaseId = sessionId,
                projectIdentity = projectIdentity,
                launcher = {
                    val managed = command.start()
                    InternalRuntimeOwnedProcess(
                        process = managed.process,
                        runtimePid = managed.hostPid(),
                        stdoutFile = stdout,
                        stderrFile = stderr,
                        telemetryFile = backgroundTelemetry,
                        terminate = { InternalAlpineProcessControl.terminate(managed) },
                        cleanup = { managed.cleanup() },
                    )
                },
                onStarted = { owned ->
                    val runtimeHostPid = owned.runtimePid
                    val record = Record(
                        sessionId = sessionId,
                        projectIdentity = projectIdentity,
                        executionRoot = executionRoot,
                        entrypoint = entrypoint,
                        generation = generation,
                        stdout = stdout,
                        stderr = stderr,
                        backgroundTelemetry = backgroundTelemetry,
                        startedAt = System.currentTimeMillis(),
                        foregroundReadyAtEpochMs = owned.foreground.readyAtEpochMs,
                        foregroundServicePid = owned.foreground.servicePid,
                        runtimeHostPid = runtimeHostPid,
                        runtimeCpuTicksAtLaunch = runtimeHostPid?.let(InternalRuntimeProcDiagnostics::cpuTicks),
                        state = EmbeddedPythonState.RUNNING,
                    )
                    records[projectIdentity] = record
                    startedRecord = record
                },
                onFinished = { code, stopRequested, finishedAtEpochMs ->
                    val current = records[projectIdentity]
                    if (current?.sessionId == sessionId) {
                        current.exitCode = code
                        current.finishedAt = finishedAtEpochMs
                        current.stopRequested = current.stopRequested || stopRequested
                        current.state = if (current.stopRequested) {
                            EmbeddedPythonState.STOPPED
                        } else if (code == 0) {
                            EmbeddedPythonState.SUCCEEDED
                        } else {
                            EmbeddedPythonState.FAILED
                        }
                    }
                },
            )
        } catch (error: Throwable) {
            startedRecord?.let { records.remove(projectIdentity, it) }
            throw error
        }
        return snapshotOf(checkNotNull(startedRecord))
    }

    fun requestStop(projectIdentity: String): Boolean {
        val record = records[projectIdentity] ?: return false
        if (!EmbeddedPythonStatePolicy.canStop(record.state)) return false
        val accepted = InternalRuntimeForegroundService.requestStopOwnedSession(
            projectIdentity = projectIdentity,
            sessionLeaseId = record.sessionId,
        )
        if (accepted) record.stopRequested = true
        return accepted
    }

    private fun snapshotOf(record: Record): EmbeddedPythonSnapshot =
        EmbeddedPythonSnapshot(
            engine = InternalPythonBackend.ALPINE,
            sessionId = record.sessionId,
            projectIdentity = record.projectIdentity,
            executionRoot = record.executionRoot.absolutePath,
            entrypoint = File(record.executionRoot, record.entrypoint).absolutePath,
            workingDirectory = record.executionRoot.absolutePath,
            generation = record.generation,
            state = record.state,
            runtimePhase = if (EmbeddedPythonStatePolicy.isTerminal(record.state)) {
                EmbeddedPythonRuntimePhase.TERMINAL
            } else {
                EmbeddedPythonRuntimePhase.RUNNING
            },
            stopPhase = if (record.stopRequested) EmbeddedPythonStopPhase.STOP_REQUEST_RETURNED else EmbeddedPythonStopPhase.IDLE,
            stopResult = if (record.stopRequested) EmbeddedPythonStopResult.REQUEST_ACCEPTED else EmbeddedPythonStopResult.NONE,
            startedAtEpochMs = record.startedAt,
            finishedAtEpochMs = record.finishedAt,
            exitCode = record.exitCode,
            stdout = buildString {
                appendLine(backgroundDiagnostics(record))
                val continuity = InternalAlpineEnvironmentManager.readTail(
                    record.backgroundTelemetry,
                    256 * 1024,
                )
                if (continuity.isNotBlank()) {
                    appendLine()
                    appendLine("=== SiftAlpha Background Continuity History ===")
                    appendLine(continuity.trimEnd())
                }
                val projectStdout = InternalAlpineEnvironmentManager.readTail(record.stdout, 512 * 1024)
                if (projectStdout.isNotBlank()) {
                    appendLine()
                    append(projectStdout)
                }
            }.trimEnd(),
            stderr = InternalAlpineEnvironmentManager.readTail(record.stderr, 512 * 1024),
        )

    private fun backgroundDiagnostics(record: Record): String {
        val foreground = InternalRuntimeForegroundService.diagnostics(record.sessionId)
        val ownership = InternalRuntimeForegroundService.ownershipDiagnostics(record.sessionId)
        val runtimePid = record.runtimeHostPid
        val runtimeAlive = runtimePid?.let { File("/proc/$it").exists() } == true
        val cpuTicksNow = runtimePid?.let(InternalRuntimeProcDiagnostics::cpuTicks)
        val cpuTicksDelta = if (
            record.runtimeCpuTicksAtLaunch != null &&
            cpuTicksNow != null &&
            cpuTicksNow >= record.runtimeCpuTicksAtLaunch
        ) {
            cpuTicksNow - record.runtimeCpuTicksAtLaunch
        } else null
        val heartbeatAgeMs = foreground.heartbeatAtEpochMs
            .takeIf { it > 0L }
            ?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }

        return listOf(
            "SIFTALPHA_X_FGS_REQUESTED=YES",
            "SIFTALPHA_X_FGS_ACTIVE=" + yesNo(foreground.foregroundActive),
            "SIFTALPHA_X_WAKE_LOCK_HELD=" + yesNo(foreground.wakeLockHeld),
            "SIFTALPHA_X_RUNTIME_LAUNCH_AFTER_FGS=YES",
            "SIFTALPHA_X_RUNTIME_OWNER=" + (
                ownership.owner ?: InternalRuntimeForegroundService.OWNER_FOREGROUND_SERVICE
            ),
            "SIFTALPHA_X_SESSION_OWNER_SERVICE_PID=" + (
                ownership.sessionOwnerServicePid ?: record.foregroundServicePid ?: -1
            ),
            "SIFTALPHA_X_RUNTIME_PROCESS_HELD=" + yesNo(ownership.processHeld),
            "SIFTALPHA_X_RUNTIME_MONITOR_ACTIVE=" + yesNo(ownership.monitorActive),
            "SIFTALPHA_X_FGS_READY_AT_EPOCH_MS=" + record.foregroundReadyAtEpochMs,
            "SIFTALPHA_X_SERVICE_PID=" + (record.foregroundServicePid ?: -1),
            "SIFTALPHA_X_RUNTIME_PID=" + (runtimePid ?: -1),
            "SIFTALPHA_X_RUNTIME_PID_ALIVE=" + yesNo(runtimeAlive),
            "SIFTALPHA_X_RUNTIME_CPU_TICKS_START=" + (record.runtimeCpuTicksAtLaunch ?: -1L),
            "SIFTALPHA_X_RUNTIME_CPU_TICKS_NOW=" + (cpuTicksNow ?: -1L),
            "SIFTALPHA_X_RUNTIME_CPU_TICKS_DELTA=" + (cpuTicksDelta ?: -1L),
            "SIFTALPHA_X_FGS_HEARTBEAT_EPOCH_MS=" + foreground.heartbeatAtEpochMs,
            "SIFTALPHA_X_FGS_HEARTBEAT_AGE_MS=" + (heartbeatAgeMs ?: -1L),
        ).joinToString("\n")
    }

    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"

    companion object {
        @Volatile
        private var sharedInstance: InternalAlpineSession? = null

        fun shared(context: Context): InternalAlpineSession =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: InternalAlpineSession(context).also { sharedInstance = it }
            }
    }
}
