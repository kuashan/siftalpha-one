package com.siftalpha.studio.siftalphax

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream

data class InternalAlpineLayout(
    val rootfs: File,
    val proot: File,
    val loader: File,
    val tempDirectory: File,
    val sharedMemoryDirectory: File,
)

data class InternalAlpineManagedProcess(
    val process: Process,
    val pidFile: File,
) {
    fun hostPid(): Int? =
        runCatching { pidFile.readText().trim().toIntOrNull() }
            .getOrNull()
            ?.takeIf { it > 0 }

    fun cleanup() {
        runCatching { pidFile.delete() }
    }
}

class InternalAlpineCommand(
    private val builder: ProcessBuilder,
    private val pidFile: File,
) {
    fun redirectOutput(file: File): InternalAlpineCommand = apply {
        builder.redirectOutput(file)
    }

    fun redirectError(file: File): InternalAlpineCommand = apply {
        builder.redirectError(file)
    }

    fun redirectErrorStream(enabled: Boolean): InternalAlpineCommand = apply {
        builder.redirectErrorStream(enabled)
    }

    fun start(): InternalAlpineManagedProcess {
        pidFile.parentFile?.mkdirs()
        runCatching { pidFile.delete() }
        val process = builder.start()
        repeat(50) {
            if (pidFile.isFile && pidFile.length() > 0L) {
                return InternalAlpineManagedProcess(process, pidFile)
            }
            if (!process.isAlive) {
                return InternalAlpineManagedProcess(process, pidFile)
            }
            try {
                Thread.sleep(10)
            } catch (interrupted: InterruptedException) {
                InternalAlpineProcessControl.terminate(
                    InternalAlpineManagedProcess(process, pidFile),
                    gracefulMillis = 500L,
                )
                Thread.currentThread().interrupt()
                throw interrupted
            }
        }
        return InternalAlpineManagedProcess(process, pidFile)
    }
}

object InternalAlpineFiles {
    private const val ASSET_ROOTFS = "siftalphax/alpine/alpine-minirootfs.tgzblob"
    private const val ASSET_PROVENANCE = "siftalphax/alpine/PROVENANCE.txt"
    private const val EXPECTED_ALPINE_VERSION = "3.21.8"
    private const val EXPECTED_ROOTFS_SHA256 =
        "f25a96d2846a4bc439093107c1b48a8b0c93dcb411e2cb9cfded6f790b2bc001"
    private const val FORMAT_VERSION = "1"
    private const val PRIVATE_ROOT = "siftalphax/alpine"
    private const val READY_MARKER = ".siftalpha-rootfs-ready"

    @Synchronized
    fun prepare(context: Context): InternalAlpineLayout {
        throwIfCancelled()
        val appContext = context.applicationContext
        val base = File(appContext.filesDir, PRIVATE_ROOT)
        check(base.mkdirs() || base.isDirectory) { "Unable to create Internal Alpine base" }
        val rootfs = File(base, "rootfs")
        val marker = File(rootfs, READY_MARKER)
        val expectedMarker = markerText()

        if (!marker.isFile || marker.readText() != expectedMarker || !File(rootfs, "bin/busybox").exists()) {
            installRootfs(appContext, base, rootfs, expectedMarker)
        }
        refreshDns(appContext, rootfs)

        val nativeLibDir = File(appContext.applicationInfo.nativeLibraryDir)
        val proot = File(nativeLibDir, "libproot.so")
        val loader = File(nativeLibDir, "libproot-loader.so")
        check(proot.isFile && proot.canExecute()) {
            "INTERNAL_ALPINE_PROOT_UNAVAILABLE: " + proot.absolutePath
        }
        check(loader.isFile && loader.canExecute()) {
            "INTERNAL_ALPINE_LOADER_UNAVAILABLE: " + loader.absolutePath
        }
        val temp = File(appContext.cacheDir, "siftalpha-proot-tmp")
        check(temp.mkdirs() || temp.isDirectory) { "Unable to create Internal Alpine temp directory" }
        val sharedMemory = File(base, "shm")
        check(sharedMemory.mkdirs() || sharedMemory.isDirectory) {
            "Unable to create Internal Alpine shared memory directory"
        }
        val guestSharedMemory = File(rootfs, "dev/shm")
        check(guestSharedMemory.mkdirs() || guestSharedMemory.isDirectory) {
            "Unable to create Internal Alpine /dev/shm mount point"
        }
        File(rootfs, "workspace").mkdirs()
        File(rootfs, "siftalpha-env").mkdirs()
        return InternalAlpineLayout(
            rootfs.canonicalFile,
            proot,
            loader,
            temp.canonicalFile,
            sharedMemory.canonicalFile,
        )
    }

    fun provenance(context: Context): String =
        context.assets.open(ASSET_PROVENANCE).bufferedReader().use { it.readText() }

    fun projectEnvironmentRoot(context: Context, projectIdentity: String): File {
        require(projectIdentity.isNotBlank()) { "projectIdentity must not be blank" }
        val environments = File(context.filesDir, "$PRIVATE_ROOT/environments")
        check(environments.mkdirs() || environments.isDirectory) {
            "Unable to create Internal Alpine environments root"
        }
        val root = File(environments, sha256Hex(projectIdentity.toByteArray()))
        check(root.parentFile?.canonicalFile == environments.canonicalFile) {
            "Internal Alpine environment escaped app-private root"
        }
        return root
    }

    fun sessionRoot(context: Context, sessionId: String): File {
        require(sessionId.isNotBlank())
        val sessions = File(context.filesDir, "$PRIVATE_ROOT/sessions")
        check(sessions.mkdirs() || sessions.isDirectory)
        val root = File(sessions, sessionId)
        check(root.parentFile?.canonicalFile == sessions.canonicalFile)
        check(root.mkdirs() || root.isDirectory)
        return root.canonicalFile
    }

    fun buildCommand(
        context: Context,
        layout: InternalAlpineLayout,
        shellCommand: String,
        binds: List<Pair<File, String>> = emptyList(),
        workingDirectory: String = "/root",
    ): InternalAlpineCommand {
        require(workingDirectory.startsWith("/")) { "guest working directory must be absolute" }
        val cmd = mutableListOf(
            layout.proot.absolutePath,
            "-0",
            "--link2symlink",
            "--kill-on-exit",
            "-r",
            layout.rootfs.absolutePath,
        )
        cmd += baseBindArguments(layout)
        binds.forEach { (host, guest) ->
            require(host.exists()) { "Internal Alpine bind source missing: $host" }
            require(guest.startsWith("/")) { "Internal Alpine bind target must be absolute" }
            cmd += "-b"
            cmd += host.canonicalPath + ":" + guest
        }
        cmd += listOf(
            "-w",
            workingDirectory,
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "PYTHONDONTWRITEBYTECODE=1",
            "PIP_DISABLE_PIP_VERSION_CHECK=1",
            "/bin/sh",
            "-lc",
            shellCommand,
        )
        val pidRoot = File(context.cacheDir, "siftalpha-proot-pids")
        check(pidRoot.mkdirs() || pidRoot.isDirectory) {
            "Unable to create Internal Alpine PID root"
        }
        val pidFile = File(pidRoot, UUID.randomUUID().toString() + ".pid")
        val dollar = '$'
        val wrapper = listOf(
            "set -eu",
            "pid_file=\"" + dollar + "1\"",
            "shift",
            "umask 077",
            "printf '%s\\n' \"" + dollar + dollar + "\" >\"" + dollar + "pid_file\"",
            "exec \"" + dollar + "@\"",
        ).joinToString("\n")
        val wrapped = mutableListOf(
            "/system/bin/sh",
            "-c",
            wrapper,
            "siftalpha-proot",
            pidFile.absolutePath,
        ).apply { addAll(cmd) }
        val builder = ProcessBuilder(wrapped).apply {
            environment()["PROOT_LOADER"] = layout.loader.absolutePath
            environment()["PROOT_TMP_DIR"] = layout.tempDirectory.absolutePath
            environment()["TMPDIR"] = layout.tempDirectory.absolutePath
            environment()["LD_LIBRARY_PATH"] = File(context.applicationInfo.nativeLibraryDir).absolutePath
        }
        return InternalAlpineCommand(builder, pidFile)
    }

    internal fun baseBindArguments(layout: InternalAlpineLayout): List<String> = listOf(
        "-b",
        "/dev",
        "-b",
        layout.sharedMemoryDirectory.canonicalPath + ":/dev/shm",
        "-b",
        "/proc",
        "-b",
        "/sys",
    )

    internal fun extractTar(input: InputStream, targetDir: File) {
        val root = targetDir.toPath().toAbsolutePath().normalize()
        val header = ByteArray(512)
        while (true) {
            throwIfCancelled()
            val count = readFully(input, header)
            if (count == 0) break
            check(count == 512) { "Truncated Internal Alpine tar header" }
            if (header.all { it == 0.toByte() }) break

            val name = extractString(header, 0, 100)
            val prefix = extractString(header, 345, 155)
            val rawName = if (prefix.isBlank()) name else "$prefix/$name"
            val safeName = normalizeArchivePath(rawName)
            val size = extractString(header, 124, 12).trim().toLongOrNull(8) ?: 0L
            val mode = extractString(header, 100, 8).trim().toIntOrNull(8) ?: 0
            val type = header[156].toInt().toChar()
            val linkName = extractString(header, 157, 100)

            if (safeName.isBlank()) {
                skipEntryData(input, size)
                continue
            }
            val target = root.resolve(safeName).normalize()
            check(target.startsWith(root) && target != root) { "Rootfs archive path escaped root: $rawName" }
            ensureSafeParents(root, target.parent)

            when (type) {
                '5', 'D' -> {
                    check(!Files.isSymbolicLink(target)) { "Rootfs directory collided with symlink: $safeName" }
                    Files.createDirectories(target)
                    skipEntryData(input, size)
                }
                '2' -> {
                    check(linkName.isNotBlank()) { "Empty rootfs symlink target: $safeName" }
                    check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        "Duplicate rootfs symlink path: $safeName"
                    }
                    Files.createSymbolicLink(target, Paths.get(linkName))
                    skipEntryData(input, size)
                }
                '1' -> {
                    check(linkName.isNotBlank()) { "Empty rootfs hard-link target: $safeName" }
                    val safeLinkName = normalizeArchivePath(linkName)
                    check(safeLinkName.isNotBlank()) { "Invalid rootfs hard-link target: $linkName" }
                    val linkTarget = root.resolve(safeLinkName).normalize()
                    check(linkTarget.startsWith(root) && linkTarget != root) {
                        "Rootfs hard-link target escaped root: $linkName"
                    }
                    check(Files.isRegularFile(linkTarget, LinkOption.NOFOLLOW_LINKS)) {
                        "Rootfs hard-link target is unavailable or unsafe: $linkName"
                    }
                    check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        "Duplicate rootfs hard-link path: $safeName"
                    }
                    Files.copy(linkTarget, target)
                    if (mode and 0b001_001_001 != 0) target.toFile().setExecutable(true, false)
                    skipEntryData(input, size)
                }
                '0', '\u0000' -> {
                    check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        "Duplicate rootfs file path: $safeName"
                    }
                    Files.createFile(target)
                    Files.newOutputStream(target).use { output -> copyExact(input, output, size) }
                    skipPadding(input, size)
                    if (mode and 0b001_001_001 != 0) target.toFile().setExecutable(true, false)
                }
                'x', 'g', 'L', 'K', '3', '4', '6' -> {
                    // Alpine minirootfs is a ustar archive in normal releases. Keep extraction
                    // resilient to metadata/special entries without materializing host devices.
                    skipEntryData(input, size)
                }
                else -> error("Unsupported rootfs tar entry type '$type' for $safeName")
            }
        }
    }

    private fun installRootfs(context: Context, base: File, rootfs: File, expectedMarker: String) {
        verifyAssetSha256(context)
        val temp = File(base, "rootfs.install-" + UUID.randomUUID())
        check(!temp.exists())
        check(temp.mkdirs())
        try {
            context.assets.open(ASSET_ROOTFS).use { raw ->
                GZIPInputStream(raw).use { gzip -> extractTar(gzip, temp) }
            }
            check(File(temp, "bin/busybox").exists()) { "Internal Alpine busybox missing after extraction" }
            refreshDns(context, temp)
            throwIfCancelled()
            File(temp, READY_MARKER).writeText(expectedMarker)
            if (rootfs.exists()) check(rootfs.deleteRecursively()) { "Unable to replace Internal Alpine rootfs" }
            check(temp.renameTo(rootfs)) { "Unable to activate Internal Alpine rootfs" }
        } catch (error: Throwable) {
            temp.deleteRecursively()
            throw error
        }
    }

    private fun verifyAssetSha256(context: Context) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(ASSET_ROOTFS).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                throwIfCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actual == EXPECTED_ROOTFS_SHA256) { "INTERNAL_ALPINE_ASSET_HASH_MISMATCH" }
    }

    private fun refreshDns(context: Context, rootfs: File) {
        val addresses = runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val network = manager?.activeNetwork
            manager?.getLinkProperties(network)?.dnsServers.orEmpty()
                .mapNotNull { it.hostAddress?.takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        val selected = addresses.ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        if (Files.isSymbolicLink(resolv.toPath())) Files.delete(resolv.toPath())
        resolv.writeText(selected.distinct().joinToString("\n") { "nameserver $it" } + "\n")
    }

    private fun normalizeArchivePath(raw: String): String {
        var value = raw
        while (value.startsWith("./")) value = value.removePrefix("./")
        if (value == ".") return ""
        val path = Paths.get(value)
        require(!path.isAbsolute) { "Absolute rootfs archive path rejected: $raw" }
        val normalized = path.normalize()
        require(normalized.none { it.toString() == ".." }) { "Rootfs traversal rejected: $raw" }
        return normalized.joinToString("/")
    }

    private fun ensureSafeParents(root: Path, parent: Path?) {
        if (parent == null || parent == root) return
        check(parent.startsWith(root))
        var current = root
        root.relativize(parent).forEach { part ->
            current = current.resolve(part)
            check(!Files.isSymbolicLink(current)) { "Rootfs extraction refused symlink parent: $current" }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                check(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    "Rootfs parent is not a directory: $current"
                }
            } else {
                Files.createDirectory(current)
            }
        }
    }

    private fun extractString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = minOf(offset + length, header.size)
        while (end < limit && header[end] != 0.toByte()) end++
        return header.copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }

    private fun copyExact(input: InputStream, output: java.io.OutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "Truncated rootfs file payload" }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipEntryData(input: InputStream, size: Long) {
        var remaining = ((size + 511) / 512) * 512
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "Truncated rootfs tar entry" }
            remaining -= count
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        var remaining = (512 - (size % 512)) % 512
        val buffer = ByteArray(512)
        while (remaining > 0) {
            val count = input.read(buffer, 0, remaining.toInt())
            check(count > 0) { "Truncated rootfs tar padding" }
            remaining -= count
        }
    }

    private fun throwIfCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Internal Alpine operation cancelled")
        }
    }

    private fun markerText(): String =
        "FORMAT=$FORMAT_VERSION\nALPINE_VERSION=$EXPECTED_ALPINE_VERSION\nROOTFS_SHA256=$EXPECTED_ROOTFS_SHA256\n"

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
