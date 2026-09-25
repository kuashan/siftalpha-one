package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit

enum class MacContainerInstallPlanKind {
    INSTALL_MANAGED_DOCKER,
    START_MANAGED_DOCKER,
    INSTALL_DOCKER_COMPOSE,
}

data class MacContainerInstallPlan(
    val id: String,
    val kind: MacContainerInstallPlanKind,
    val title: String,
    val detail: String,
    val providerKind: MacContainerProviderKind,
    val components: List<String>,
    val sideEffects: List<String>,
    val automatic: Boolean = true,
)

enum class MacContainerInstallPhase {
    IDLE,
    DOWNLOADING,
    INSTALLING,
    STARTING,
    VERIFYING,
    PREPARING_PROJECT,
    COMPLETE,
    FAILED,
}

data class MacContainerInstallProgress(
    val phase: MacContainerInstallPhase,
    val message: String,
    val logLines: List<String> = emptyList(),
) {
    val active: Boolean
        get() = phase in setOf(
            MacContainerInstallPhase.DOWNLOADING,
            MacContainerInstallPhase.INSTALLING,
            MacContainerInstallPhase.STARTING,
            MacContainerInstallPhase.VERIFYING,
            MacContainerInstallPhase.PREPARING_PROJECT,
        )
}

data class MacContainerInstallResult(
    val success: Boolean,
    val detail: String? = null,
)

interface MacContainerEnvironmentInstaller {
    fun install(
        plan: MacContainerInstallPlan,
        progress: (MacContainerInstallPhase, String) -> Unit,
        log: (String) -> Unit,
    ): MacContainerInstallResult
}

object MacContainerInstallPlanner {
    fun plan(
        facts: MacSystemFacts,
        providers: Collection<MacContainerProviderSnapshot>,
        userHome: File = File(System.getProperty("user.home")),
    ): MacContainerInstallPlan? {
        if ((facts.osMajor ?: return null) < 13) return null
        val arch = MacManagedContainerToolchain.normalizeArchitecture(facts.architecture) ?: return null

        val ready = providers.any {
            it.availability == CapabilityAvailability.AVAILABLE && it.composeAvailable
        }
        if (ready) return null

        val docker = providers.firstOrNull { it.kind == MacContainerProviderKind.DOCKER }
        val podman = providers.firstOrNull { it.kind == MacContainerProviderKind.PODMAN }

        val managedRoot = MacManagedContainerToolchain.currentRoot(userHome)
        val dockerIsManaged = docker?.executablePath?.let { executable ->
            managedRoot?.let { root ->
                runCatching { File(executable).canonicalPath }
                    .getOrNull()
                    ?.startsWith(runCatching { root.canonicalPath + File.separator }.getOrDefault("")) == true
            } == true
        } == true

        if (
            docker?.availability == CapabilityAvailability.AVAILABLE &&
            !docker.composeAvailable &&
            dockerIsManaged
        ) {
            return MacContainerInstallPlan(
                id = "managed-compose-" + MacManagedContainerToolchain.COMPOSE_VERSION + "-" + arch,
                kind = MacContainerInstallPlanKind.INSTALL_DOCKER_COMPOSE,
                title = "补齐 Docker Compose",
                detail = "当前 Docker Runtime 已可用，SiftAlpha 可以安装经过校验的 Compose CLI 插件，然后自动重新检测并继续准备项目。",
                providerKind = MacContainerProviderKind.DOCKER,
                components = listOf("Docker Compose " + MacManagedContainerToolchain.COMPOSE_VERSION),
                sideEffects = listOf(
                    "下载官方 Compose 发布文件到 SiftAlpha 数据目录",
                    "使用 SiftAlpha 自己的 Docker 配置目录，不覆盖用户已有 Docker 配置",
                ),
            )
        }

        val managed = managedRoot
        val managedDocker = managed?.resolve("bin/docker")?.takeIf { it.isFile }
        if (
            docker?.availability == CapabilityAvailability.UNKNOWN &&
            managedDocker != null &&
            docker.executablePath?.let { runCatching { File(it).canonicalFile }.getOrNull() } ==
            runCatching { managedDocker.canonicalFile }.getOrNull()
        ) {
            return MacContainerInstallPlan(
                id = "start-managed-colima-" + arch,
                kind = MacContainerInstallPlanKind.START_MANAGED_DOCKER,
                title = "启动 SiftAlpha 容器环境",
                detail = "SiftAlpha 已找到之前安装的托管 Docker 工具，但容器虚拟机当前没有运行。确认后会重新启动它并继续准备项目。",
                providerKind = MacContainerProviderKind.DOCKER,
                components = listOf("SiftAlpha Managed Colima / Docker"),
                sideEffects = listOf("启动当前用户的本地 Linux 容器虚拟机"),
            )
        }

        val externalProviderPresent = providers.any {
            it.executablePath != null && it.availability == CapabilityAvailability.UNKNOWN
        }
        if (externalProviderPresent) return null

        if (podman?.availability == CapabilityAvailability.AVAILABLE && !podman.composeAvailable) {
            return null
        }

        return MacContainerInstallPlan(
            id = "managed-colima-docker-" + MacManagedContainerToolchain.STACK_VERSION + "-" + arch,
            kind = MacContainerInstallPlanKind.INSTALL_MANAGED_DOCKER,
            title = "安装推荐容器环境",
            detail = "SiftAlpha 将安装自己的轻量 Docker 兼容环境。它不依赖 Homebrew/MacPorts，不修改系统级 Docker 配置，也不会强制设置 CPU 或内存。",
            providerKind = MacContainerProviderKind.DOCKER,
            components = listOf(
                "Colima " + MacManagedContainerToolchain.COLIMA_VERSION,
                "Lima " + MacManagedContainerToolchain.LIMA_VERSION,
                "Docker CLI " + MacManagedContainerToolchain.DOCKER_VERSION,
                "Docker Compose " + MacManagedContainerToolchain.COMPOSE_VERSION,
                "Docker Buildx " + MacManagedContainerToolchain.BUILDX_VERSION,
            ),
            sideEffects = listOf(
                "下载并校验官方发布文件到 SiftAlpha 用户数据目录",
                "创建当前用户的本地 Linux 容器虚拟机（首次启动会继续下载 VM 镜像）",
                "使用 SiftAlpha 自己的 Docker/Colima/Lima 数据目录，不覆盖用户已有配置",
                "只在用户主动执行“准备环境”或开发者环境修复时执行",
            ),
        )
    }
}

object MacManagedContainerToolchain {
    const val COLIMA_VERSION = "0.10.3"
    const val LIMA_VERSION = "2.2.0"
    const val DOCKER_VERSION = "29.8.1"
    const val COMPOSE_VERSION = "5.5.1"
    const val BUILDX_VERSION = "0.37.1"
    const val STACK_VERSION = "c0.10.3-l2.2.0-d29.8.1-p5.5.1-b0.37.1"

    fun buildxReleaseSha256(arch: String): String = when (arch) {
        "x86_64" -> "7003a7bae20e7741283db1e23dafdcb957776a8be85de3f459630b1dd4c19db0"
        "arm64" -> "c3cbbc820d578b0aa8158dd62ef1af25a0c8a75ef53331dbe4e219471e1dbe8c"
        else -> error("unsupported Buildx architecture: " + arch)
    }

    fun normalizeArchitecture(value: String): String? = when (value.lowercase()) {
        "x86_64", "amd64" -> "x86_64"
        "arm64", "aarch64" -> "arm64"
        else -> null
    }

    fun root(userHome: File = File(System.getProperty("user.home"))): File =
        File(userHome, "Library/Application Support/SiftAlpha X/container-runtime")

    fun currentRoot(userHome: File = File(System.getProperty("user.home"))): File? {
        val base = root(userHome)
        val pointer = File(base, "current.txt")
        if (!pointer.isFile) return null
        val name = pointer.readText().trim()
        if (name.isBlank() || '/' in name || '\\' in name) return null
        val result = File(base, name)
        return result.takeIf { it.isDirectory }
    }

    fun managedDockerExecutable(userHome: File = File(System.getProperty("user.home"))): File? =
        currentRoot(userHome)?.resolve("bin/docker")?.takeIf { it.isFile && it.canExecute() }

    fun managedBin(userHome: File = File(System.getProperty("user.home"))): File? =
        currentRoot(userHome)?.resolve("bin")?.takeIf { it.isDirectory }

    private const val MACOS_UNIX_PATH_MAX = 104
    private const val COLIMA_PROFILE = "sa"
    private const val LIMA_LONGEST_COLIMA_SOCKET_SUFFIX =
        "colima-sa/ssh.sock.1234567890123456"
    private val FALLBACK_STATE_PARENT = File("/private/var/tmp")

    fun managedStateRoot(
        userHome: File = File(System.getProperty("user.home")),
    ): File {
        val candidates = listOf(
            File(userHome, ".siftalpha"),
            File(userHome, ".sa"),
            File(FALLBACK_STATE_PARENT, "sax-" + stableHomeKey(userHome)),
        )
        return candidates.firstOrNull { candidate ->
            limaSocketPathBytes(File(candidate, "l")) < MACOS_UNIX_PATH_MAX
        } ?: error(
            "CONTAINER_RUNTIME_PATH_TOO_LONG: no safe Lima state root for current macOS user path",
        )
    }

    fun limaHome(
        userHome: File = File(System.getProperty("user.home")),
    ): File = File(managedStateRoot(userHome), "l")

    fun colimaHome(
        userHome: File = File(System.getProperty("user.home")),
    ): File = File(managedStateRoot(userHome), "c")

    fun projectedLimaSocketPathLength(
        userHome: File = File(System.getProperty("user.home")),
    ): Int = limaSocketPathBytes(limaHome(userHome))

    fun usesFallbackStateRoot(
        userHome: File = File(System.getProperty("user.home")),
    ): Boolean = managedStateRoot(userHome).parentFile == FALLBACK_STATE_PARENT

    private fun limaSocketPathBytes(limaHome: File): Int =
        File(limaHome, LIMA_LONGEST_COLIMA_SOCKET_SUFFIX)
            .absolutePath
            .toByteArray(Charsets.UTF_8)
            .size

    private fun stableHomeKey(userHome: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(userHome.absolutePath.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }

    fun environment(
        root: File,
        base: Map<String, String> = System.getenv(),
        userHome: File = File(System.getProperty("user.home")),
    ): Map<String, String> = buildMap {
        putAll(base)
        put("PATH", File(root, "bin").absolutePath + File.pathSeparator + base["PATH"].orEmpty())
        put("COLIMA_HOME", colimaHome(userHome).absolutePath)
        put("COLIMA_CACHE_HOME", File(root, "cache/colima").absolutePath)
        put("LIMA_HOME", limaHome(userHome).absolutePath)
        put("DOCKER_CONFIG", File(root, "docker-config").absolutePath)
        put("COLIMA_PROFILE", COLIMA_PROFILE)
    }

    fun environmentForExecutable(
        executable: String,
        userHome: File = File(System.getProperty("user.home")),
        base: Map<String, String> = System.getenv(),
    ): Map<String, String> {
        val root = currentRoot(userHome) ?: return base
        val managed = runCatching { File(executable).canonicalPath }.getOrNull()
        val prefix = runCatching { root.canonicalPath + File.separator }.getOrNull()
        return if (managed != null && prefix != null && managed.startsWith(prefix)) {
            environment(root, base)
        } else {
            base
        }
    }
}

internal object MacManagedContainerChecksum {
    private val checksumLine = Regex("^([0-9a-fA-F]{64})(?:\\s+[*]?(.+))?$")

    fun expectedFor(
        checksumText: String,
        fileName: String,
    ): String? {
        val entries = checksumText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val match = checksumLine.matchEntire(line) ?: return@mapNotNull null
                val digest = match.groupValues[1].lowercase()
                val listedName = match.groupValues.getOrNull(2)
                    ?.trim()
                    ?.removePrefix("*")
                    ?.removePrefix("./")
                    ?.takeIf(String::isNotBlank)
                digest to listedName
            }
            .toList()

        entries.firstOrNull { (_, listedName) ->
            listedName == fileName
        }?.let { return it.first }

        return entries
            .singleOrNull { it.second == null }
            ?.first
    }
}

class MacManagedContainerInstaller(
    private val facts: MacSystemFacts = MacSystemFactsDiscovery.discover(),
    private val userHome: File = File(System.getProperty("user.home")),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
) : MacContainerEnvironmentInstaller {
    private data class Asset(
        val name: String,
        val url: String,
        val checksumUrl: String? = null,
        val checksumMatchName: String = name,
        val expectedSha256: String? = null,
    )

    override fun install(
        plan: MacContainerInstallPlan,
        progress: (MacContainerInstallPhase, String) -> Unit,
        log: (String) -> Unit,
    ): MacContainerInstallResult = when (plan.kind) {
        MacContainerInstallPlanKind.INSTALL_MANAGED_DOCKER ->
            installFullStack(progress, log)
        MacContainerInstallPlanKind.START_MANAGED_DOCKER ->
            startExisting(progress, log)
        MacContainerInstallPlanKind.INSTALL_DOCKER_COMPOSE ->
            installComposeOnly(progress, log)
    }

    private fun installFullStack(
        progress: (MacContainerInstallPhase, String) -> Unit,
        log: (String) -> Unit,
    ): MacContainerInstallResult {
        val arch = MacManagedContainerToolchain.normalizeArchitecture(facts.architecture)
            ?: return MacContainerInstallResult(false, "unsupported macOS architecture: " + facts.architecture)
        if ((facts.osMajor ?: 0) < 13) {
            return MacContainerInstallResult(false, "managed container installer requires macOS 13+")
        }

        val base = MacManagedContainerToolchain.root(userHome).apply { mkdirs() }
        val finalName = "managed-" + MacManagedContainerToolchain.STACK_VERSION + "-" + arch
        val final = File(base, finalName)
        val staging = File(base, finalName + ".tmp")
        val downloads = File(staging, "downloads")

        return try {
            staging.deleteRecursively()
            downloads.mkdirs()
            File(staging, "bin").mkdirs()
            File(staging, "docker-config/cli-plugins").mkdirs()
            File(staging, "cache/colima").mkdirs()
            ensureManagedStateDirectories(log)

            progress(MacContainerInstallPhase.DOWNLOADING, "正在下载并校验容器工具…")
            val assets = assets(arch)
            val downloaded = linkedMapOf<String, File>()
            assets.forEach { asset ->
                log("DOWNLOAD=" + asset.name)
                val destination = File(downloads, asset.name)
                downloadAndVerify(asset, destination, log)
                downloaded[asset.name] = destination
            }

            progress(MacContainerInstallPhase.INSTALLING, "正在安装 SiftAlpha 托管容器工具…")
            val lima = downloaded.getValue(limaAssetName(arch))
            runChecked(
                listOf("/usr/bin/tar", "-xzf", lima.absolutePath, "-C", staging.absolutePath),
                environmentFor(staging),
                120,
                log,
            )

            val dockerArchive = downloaded.getValue(dockerAssetName())
            val dockerExtract = File(staging, "docker-extract").apply { mkdirs() }
            runChecked(
                listOf("/usr/bin/tar", "-xzf", dockerArchive.absolutePath, "-C", dockerExtract.absolutePath),
                environmentFor(staging),
                120,
                log,
            )
            val dockerBinary = File(dockerExtract, "docker/docker")
            require(dockerBinary.isFile) { "Docker archive did not contain docker/docker" }
            dockerBinary.copyTo(File(staging, "bin/docker"), overwrite = true)

            downloaded.getValue(colimaAssetName(arch))
                .copyTo(File(staging, "bin/colima"), overwrite = true)
            downloaded.getValue(composeAssetName(arch))
                .copyTo(File(staging, "docker-config/cli-plugins/docker-compose"), overwrite = true)
            downloaded.getValue(buildxAssetName(arch))
                .copyTo(File(staging, "docker-config/cli-plugins/docker-buildx"), overwrite = true)

            listOf(
                File(staging, "bin/colima"),
                File(staging, "bin/docker"),
                File(staging, "bin/limactl"),
                File(staging, "bin/lima"),
                File(staging, "docker-config/cli-plugins/docker-compose"),
                File(staging, "docker-config/cli-plugins/docker-buildx"),
            ).filter { it.exists() }.forEach { file ->
                check(file.setExecutable(true, true) || file.canExecute()) {
                    "failed to mark executable: " + file.absolutePath
                }
            }

            File(staging, "manifest.txt").writeText(
                buildString {
                    appendLine("SIFTALPHA_MANAGED_CONTAINER=1")
                    appendLine("ARCH=" + arch)
                    appendLine("COLIMA=" + MacManagedContainerToolchain.COLIMA_VERSION)
                    appendLine("LIMA=" + MacManagedContainerToolchain.LIMA_VERSION)
                    appendLine("DOCKER=" + MacManagedContainerToolchain.DOCKER_VERSION)
                    appendLine("COMPOSE=" + MacManagedContainerToolchain.COMPOSE_VERSION)
                    appendLine("BUILDX=" + MacManagedContainerToolchain.BUILDX_VERSION)
                },
            )
            downloads.deleteRecursively()
            dockerExtract.deleteRecursively()

            if (final.exists()) final.deleteRecursively()
            try {
                Files.move(staging.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Throwable) {
                Files.move(staging.toPath(), final.toPath())
            }
            commitCurrent(base, final.name)
            registerComposePlugin(final, log)

            startManaged(final, progress, log)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            progress(MacContainerInstallPhase.FAILED, "容器环境安装失败")
            MacContainerInstallResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun startExisting(
        progress: (MacContainerInstallPhase, String) -> Unit,
        log: (String) -> Unit,
    ): MacContainerInstallResult {
        val current = MacManagedContainerToolchain.currentRoot(userHome)
            ?: return MacContainerInstallResult(false, "managed container toolchain is missing")
        return startManaged(current, progress, log)
    }

    private fun installComposeOnly(
        progress: (MacContainerInstallPhase, String) -> Unit,
        log: (String) -> Unit,
    ): MacContainerInstallResult {
        val arch = MacManagedContainerToolchain.normalizeArchitecture(facts.architecture)
            ?: return MacContainerInstallResult(false, "unsupported macOS architecture: " + facts.architecture)
        return try {
            val base = MacManagedContainerToolchain.root(userHome).apply { mkdirs() }
            val existing = MacManagedContainerToolchain.currentRoot(userHome)
            val dir = existing ?: File(
                base,
                "managed-compose-" + MacManagedContainerToolchain.COMPOSE_VERSION + "-" + arch,
            ).apply {
                File(this, "bin").mkdirs()
                File(this, "docker-config/cli-plugins").mkdirs()
            }
            val plugin = File(dir, "docker-config/cli-plugins/docker-compose")
            progress(MacContainerInstallPhase.DOWNLOADING, "正在下载并校验 Docker Compose…")
            downloadAndVerify(composeAsset(arch), plugin, log)
            plugin.setExecutable(true, true)
            registerComposePluginTarget(plugin, log)
            if (existing == null) commitCurrent(base, dir.name)
            progress(MacContainerInstallPhase.VERIFYING, "正在重新检测 Compose…")
            MacContainerInstallResult(true)
        } catch (error: Throwable) {
            progress(MacContainerInstallPhase.FAILED, "Compose 安装失败")
            MacContainerInstallResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun startManaged(
        root: File,
        progress: (MacContainerInstallPhase, String) -> Unit,
        log: (String) -> Unit,
    ): MacContainerInstallResult {
        val colima = File(root, "bin/colima")
        val docker = File(root, "bin/docker")
        if (!colima.isFile || !docker.isFile) {
            return MacContainerInstallResult(false, "managed container executables are incomplete")
        }

        return try {
            registerComposePlugin(root, log)
            ensureManagedStateDirectories(log)
            val managedEnvironment = environmentFor(root)
            log("COLIMA_HOME=MANAGED|" + managedEnvironment["COLIMA_HOME"].orEmpty())
            log("LIMA_HOME=MANAGED|" + managedEnvironment["LIMA_HOME"].orEmpty())
            log(
                "LIMA_SOCKET_PATH_LENGTH=" +
                    MacManagedContainerToolchain.projectedLimaSocketPathLength(userHome),
            )
            progress(MacContainerInstallPhase.STARTING, "正在启动本地容器环境…")
            runChecked(
                listOf(colima.absolutePath, "start", "--runtime", "docker"),
                managedEnvironment,
                30 * 60,
                log,
            )

            progress(MacContainerInstallPhase.VERIFYING, "正在验证 Docker 与 Compose…")
            runChecked(
                listOf(docker.absolutePath, "--version"),
                managedEnvironment,
                30,
                log,
            )
            runChecked(
                listOf(docker.absolutePath, "info", "--format", "{{.ServerVersion}}"),
                managedEnvironment,
                60,
                log,
            )
            runChecked(
                listOf(docker.absolutePath, "compose", "version"),
                managedEnvironment,
                60,
                log,
            )
            runChecked(
                listOf(docker.absolutePath, "buildx", "version"),
                managedEnvironment,
                60,
                log,
            )
            MacContainerInstallResult(true)
        } catch (error: Throwable) {
            progress(MacContainerInstallPhase.FAILED, "容器环境启动或验证失败")
            MacContainerInstallResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun assets(arch: String): List<Asset> = listOf(
        colimaAsset(arch),
        limaAsset(arch),
        dockerAsset(arch),
        composeAsset(arch),
        buildxAsset(arch),
    )

    private fun colimaAsset(arch: String): Asset {
        val name = colimaAssetName(arch)
        val base = "https://github.com/abiosoft/colima/releases/download/v" +
            MacManagedContainerToolchain.COLIMA_VERSION + "/"
        return Asset(name, base + name, base + name + ".sha256sum", name)
    }

    private fun limaAsset(arch: String): Asset {
        val name = limaAssetName(arch)
        val base = "https://github.com/lima-vm/lima/releases/download/v" +
            MacManagedContainerToolchain.LIMA_VERSION + "/"
        return Asset(name, base + name, base + "SHA256SUMS", name)
    }

    private fun dockerAsset(arch: String): Asset {
        val name = dockerAssetName()
        val dockerArch = if (arch == "arm64") "aarch64" else "x86_64"
        val base = "https://download.docker.com/mac/static/stable/" + dockerArch + "/"
        // Docker's macOS static archive directory does not publish a per-file checksum
        // sidecar. Keep the URL version-pinned over HTTPS and verify the extracted CLI
        // version before accepting the managed environment.
        return Asset(name, base + name)
    }

    private fun composeAsset(arch: String): Asset {
        val name = composeAssetName(arch)
        val base = "https://github.com/docker/compose/releases/download/v" +
            MacManagedContainerToolchain.COMPOSE_VERSION + "/"
        return Asset(name, base + name, base + name + ".sha256", name)
    }

    private fun buildxAsset(arch: String): Asset {
        val name = buildxAssetName(arch)
        val base = "https://github.com/docker/buildx/releases/download/v" +
            MacManagedContainerToolchain.BUILDX_VERSION + "/"
        val expectedSha256 = MacManagedContainerToolchain.buildxReleaseSha256(arch)
        // Buildx v0.37.1 intentionally omits Darwin binaries from checksums.txt.
        // GitHub Release metadata publishes a SHA-256 digest for each Darwin asset,
        // so pin those official digests in the managed toolchain catalog.
        return Asset(
            name = name,
            url = base + name,
            expectedSha256 = expectedSha256,
        )
    }

    private fun colimaAssetName(arch: String): String =
        "colima-Darwin-" + if (arch == "arm64") "arm64" else "x86_64"

    private fun limaAssetName(arch: String): String =
        "lima-" + MacManagedContainerToolchain.LIMA_VERSION + "-Darwin-" +
            if (arch == "arm64") "arm64.tar.gz" else "x86_64.tar.gz"

    private fun dockerAssetName(): String =
        "docker-" + MacManagedContainerToolchain.DOCKER_VERSION + ".tgz"

    private fun composeAssetName(arch: String): String =
        "docker-compose-darwin-" + if (arch == "arm64") "aarch64" else "x86_64"

    private fun buildxAssetName(arch: String): String =
        "buildx-v" + MacManagedContainerToolchain.BUILDX_VERSION + ".darwin-" +
            if (arch == "arm64") "arm64" else "amd64"

    private fun downloadAndVerify(
        asset: Asset,
        destination: File,
        log: (String) -> Unit,
    ) {
        destination.parentFile.mkdirs()
        download(asset.url, destination)
        val actual = sha256(destination)
        val pinned = asset.expectedSha256
        val checksumUrl = asset.checksumUrl
        when {
            pinned != null -> {
                check(actual == pinned.lowercase()) {
                    "checksum mismatch for " + asset.name +
                        ": expected=" + pinned.lowercase() + " actual=" + actual
                }
                log("VERIFY_SHA256=PASS_PINNED_RELEASE_DIGEST|" + asset.name + "|" + actual)
            }
            checksumUrl != null -> {
                val checksumText = downloadText(checksumUrl)
                val expected = MacManagedContainerChecksum.expectedFor(
                    checksumText = checksumText,
                    fileName = asset.checksumMatchName,
                ) ?: error("checksum not found for " + asset.name)
                check(actual == expected) {
                    "checksum mismatch for " + asset.name + ": expected=" + expected + " actual=" + actual
                }
                log("VERIFY_SHA256=PASS|" + asset.name + "|" + actual)
            }
            else -> {
                log("VERIFY_SHA256=UPSTREAM_NOT_PUBLISHED|" + asset.name + "|" + actual)
            }
        }
    }

    private fun download(url: String, destination: File) {
        val temp = File(destination.parentFile, destination.name + ".part")
        temp.delete()
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "SiftAlpha-X/" + MacManagedContainerToolchain.STACK_VERSION)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(temp.toPath()))
        check(response.statusCode() in 200..299) {
            "download failed HTTP " + response.statusCode() + ": " + url
        }
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun downloadText(url: String): String {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofMinutes(2))
            .header("User-Agent", "SiftAlpha-X/" + MacManagedContainerToolchain.STACK_VERSION)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "checksum download failed HTTP " + response.statusCode() + ": " + url
        }
        return response.body()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun registerComposePlugin(root: File, log: (String) -> Unit) {
        val plugin = File(root, "docker-config/cli-plugins/docker-compose")
        check(plugin.isFile) { "managed Compose plugin is missing" }
        registerComposePluginTarget(plugin, log)
    }

    private fun registerComposePluginTarget(plugin: File, log: (String) -> Unit) {
        check(plugin.isFile) { "managed Compose plugin is missing" }
        check(plugin.canExecute() || plugin.setExecutable(true, true)) {
            "managed Compose plugin is not executable"
        }
        log("COMPOSE_PLUGIN=MANAGED|" + plugin.absolutePath)
    }

    private fun commitCurrent(base: File, name: String) {
        val temp = File(base, "current.txt.tmp")
        temp.writeText(name + "\n")
        Files.move(
            temp.toPath(),
            File(base, "current.txt").toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun environmentFor(root: File): Map<String, String> =
        MacManagedContainerToolchain.environment(root, userHome = userHome)

    private fun ensureManagedStateDirectories(log: (String) -> Unit) {
        val stateRoot = MacManagedContainerToolchain.managedStateRoot(userHome)
        listOf(
            stateRoot,
            MacManagedContainerToolchain.colimaHome(userHome),
            MacManagedContainerToolchain.limaHome(userHome),
        ).forEach { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "failed to create managed container state directory: " + directory.absolutePath
            }
            directory.setReadable(false, false)
            directory.setWritable(false, false)
            directory.setExecutable(false, false)
            check(
                directory.setReadable(true, true) &&
                    directory.setWritable(true, true) &&
                    directory.setExecutable(true, true),
            ) {
                "failed to restrict managed container state directory: " + directory.absolutePath
            }
        }
        log("CONTAINER_STATE_ROOT=MANAGED|" + stateRoot.absolutePath)
        log(
            "CONTAINER_STATE_ROOT_FALLBACK=" +
                MacManagedContainerToolchain.usesFallbackStateRoot(userHome),
        )
    }

    private fun runChecked(
        command: List<String>,
        environment: Map<String, String>,
        timeoutSeconds: Long,
        log: (String) -> Unit,
    ) {
        val output = File.createTempFile("siftalpha-container-install-", ".log")
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .apply { environment().putAll(environment) }
                .start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() >= deadline) {
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                    error("command timed out: " + command.firstOrNull().orEmpty())
                }
            }
            val text = output.readText().takeLast(256 * 1024)
            text.lineSequence().filter(String::isNotBlank).forEach {
                log("installer: " + it.take(1500))
            }
            check(process.exitValue() == 0) {
                "command failed exit=" + process.exitValue() + ": " +
                    command.take(3).joinToString(" ")
            }
        } finally {
            output.delete()
        }
    }
}
