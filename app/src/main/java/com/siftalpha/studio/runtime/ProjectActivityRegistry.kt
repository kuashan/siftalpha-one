package com.siftalpha.studio.runtime

import java.util.concurrent.atomic.AtomicLong

class ProjectActivityRegistry {
    enum class Kind {
        PREPARE,
        START,
        STATUS,
        LOGS,
        OBSERVATION,
    }

    data class Token internal constructor(
        val projectId: String,
        val operationId: Long,
        val kind: Kind,
    )

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
        val token = Token(projectId, nextId.incrementAndGet(), kind)
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
        synchronized(lock) { entries.values.any { it.token.projectId == projectId } }

    fun activeKinds(projectId: String): Set<Kind> =
        synchronized(lock) {
            entries.values
                .asSequence()
                .filter { it.token.projectId == projectId }
                .map { it.token.kind }
                .toSet()
        }

    fun cancelProject(projectId: String): Int {
        val cancelled = synchronized(lock) {
            val matching = entries.values.filter { it.token.projectId == projectId }
            matching.forEach { entries.remove(it.token.operationId) }
            matching
        }
        cancelled.forEach { entry -> runCatching(entry.cancel) }
        return cancelled.size
    }
}
