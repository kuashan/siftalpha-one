package com.siftalpha.macos

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

enum class MacProjectEnvProvisionStatus {
    EXISTING,
    CREATED_FROM_TEMPLATE,
    NO_TEMPLATE,
}

data class MacProjectEnvProvisionResult(
    val success: Boolean,
    val status: MacProjectEnvProvisionStatus? = null,
    val detail: String? = null,
)

internal object MacProjectEnvironmentTemplatePolicy {
    private const val MAX_TEMPLATE_BYTES = 2L * 1024L * 1024L

    fun ensure(projectRootPath: String): MacProjectEnvProvisionResult {
        val root = runCatching { File(projectRootPath).canonicalFile }.getOrNull()
            ?: return failure("project root is invalid")
        if (!root.isDirectory) return failure("project root is unavailable")

        val target = File(root, ".env")
        if (target.exists()) {
            val path = target.toPath()
            if (
                Files.isSymbolicLink(path) ||
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            ) {
                return failure(".env exists but is not a regular project file")
            }
            return MacProjectEnvProvisionResult(
                success = true,
                status = MacProjectEnvProvisionStatus.EXISTING,
            )
        }

        val template = File(root, ".env.example")
        if (!template.exists()) {
            return MacProjectEnvProvisionResult(
                success = true,
                status = MacProjectEnvProvisionStatus.NO_TEMPLATE,
            )
        }

        val templatePath = template.toPath()
        if (
            Files.isSymbolicLink(templatePath) ||
            !Files.isRegularFile(templatePath, LinkOption.NOFOLLOW_LINKS)
        ) {
            return failure(".env.example is not a regular project file")
        }
        val canonicalTemplate = runCatching { template.canonicalFile }.getOrNull()
            ?: return failure(".env.example cannot be resolved")
        if (!canonicalTemplate.toPath().startsWith(root.toPath())) {
            return failure(".env.example escapes the project root")
        }
        if (template.length() > MAX_TEMPLATE_BYTES) {
            return failure(".env.example is too large to provision safely")
        }

        return runCatching {
            Files.copy(
                templatePath,
                target.toPath(),
                StandardCopyOption.COPY_ATTRIBUTES,
            )
            runCatching {
                Files.setPosixFilePermissions(
                    target.toPath(),
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                    ),
                )
            }
            MacProjectEnvProvisionResult(
                success = true,
                status = MacProjectEnvProvisionStatus.CREATED_FROM_TEMPLATE,
            )
        }.getOrElse { error ->
            runCatching { target.delete() }
            failure(
                "failed to create .env from .env.example: " +
                    (error.message ?: error.javaClass.simpleName),
            )
        }
    }

    private fun failure(detail: String): MacProjectEnvProvisionResult =
        MacProjectEnvProvisionResult(
            success = false,
            status = null,
            detail = detail,
        )
}
