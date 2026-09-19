package com.siftalpha.studio.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Deletes one already-resolved project environment without following symbolic links.
 *
 * The caller supplies the known parent root, so a project environment can never turn this
 * helper into a cache/rootfs/other-project deletion. The walk checks cancellation and deadline
 * before every directory entry and before the final directory removal.
 */
object InterruptibleProjectTreeDelete {
    class Cancelled : IOException("PROJECT_ENVIRONMENT_DELETE_CANCELLED")
    class TimedOut : IOException("PROJECT_ENVIRONMENT_DELETE_TIMED_OUT")

    fun delete(
        environmentRoot: File,
        allowedParent: File,
        deadlineNanos: Long = Long.MAX_VALUE,
        shouldCancel: () -> Boolean = { Thread.currentThread().isInterrupted },
    ) {
        val parent = allowedParent.canonicalFile
        val root = environmentRoot.canonicalFile
        require(root != parent) { "environment root must not be the shared parent" }
        require(root.toPath().startsWith(parent.toPath())) {
            "environment root escaped its allowed parent"
        }
        checkNotCancelled(deadlineNanos, shouldCancel)
        if (!Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        deletePath(root.toPath(), deadlineNanos, shouldCancel)
    }

    private fun deletePath(
        path: Path,
        deadlineNanos: Long,
        shouldCancel: () -> Boolean,
    ) {
        checkNotCancelled(deadlineNanos, shouldCancel)
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(path)
        ) {
            Files.newDirectoryStream(path).use { entries ->
                for (entry in entries) deletePath(entry, deadlineNanos, shouldCancel)
            }
        }
        checkNotCancelled(deadlineNanos, shouldCancel)
        Files.deleteIfExists(path)
    }

    private fun checkNotCancelled(deadlineNanos: Long, shouldCancel: () -> Boolean) {
        if (shouldCancel() || Thread.currentThread().isInterrupted) throw Cancelled()
        if (System.nanoTime() >= deadlineNanos) throw TimedOut()
    }
}
