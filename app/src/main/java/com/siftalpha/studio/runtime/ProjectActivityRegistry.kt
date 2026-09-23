package com.siftalpha.studio.runtime

import com.siftalpha.core.process.ProjectProcessControlPolicy
import com.siftalpha.core.process.ProjectProcessScope
import java.util.concurrent.atomic.AtomicLong

class ProjectActivityRegistry {
    enum class Kind {
        PREPARE,
        START,
        STATUS,
        LOGS,
        CLEAN,
        OBSERVATION,
    }

    data class Token internal constructor(
        val scope: ProjectProcessScope,
        val operationId: Long,
        val kind: Kind,
    ) {
        val projectId: String
            get() = scope.projectId
    }

    private data class Entry(
        val token: Token,
        var cancel: () -> Unit,
    )

    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val entries = linkedMapOf<Long, Entry>()

    fun begin(
        projectId: String,
        kind: Kind,
        cancel: () -> Unit = {},
    ): Token {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        val token = Token(ProjectProcessScope(projectId), nextId.incrementAndGet(), kind)
        synchronized(lock) {
            entries[token.operationId] = Entry(token, cancel)
        }
        return token
    }

    fun attachCancel(token: Token, cancel: () -> Unit): Boolean =
        synchronized(lock) {
            val entry = entries[token.operationId]
            if (entry?.token != token) {
                false
            } else {
                entry.cancel = cancel
                true
            }
        }

    fun finish(token: Token): Boolean =
        synchronized(lock) {
            val entry = entries[token.operationId]
            if (entry?.token != token) {
                false
            } else {
                entries.remove(token.operationId)
                true
            }
        }

    fun hasActive(projectId: String): Boolean =
        synchronized(lock) {
            val scope = ProjectProcessScope(projectId)
            entries.values.any { ProjectProcessControlPolicy.sameProject(it.token.scope, scope) }
        }

    fun activeKinds(projectId: String): Set<Kind> =
        synchronized(lock) {
            val scope = ProjectProcessScope(projectId)
            entries.values
                .asSequence()
                .filter { ProjectProcessControlPolicy.sameProject(it.token.scope, scope) }
                .map { it.token.kind }
                .toSet()
        }

    fun cancelProject(projectId: String): Int {
        val scope = ProjectProcessScope(projectId)
        val cancelled = synchronized(lock) {
            val matching = entries.values.filter {
                ProjectProcessControlPolicy.sameProject(it.token.scope, scope)
            }
            matching.forEach { entries.remove(it.token.operationId) }
            matching
        }
        cancelled.forEach { entry -> runCatching(entry.cancel) }
        return cancelled.size
    }
}
