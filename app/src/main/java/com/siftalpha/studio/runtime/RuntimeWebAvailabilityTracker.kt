package com.siftalpha.studio.runtime

import android.os.Handler
import android.os.Looper

/**
 * Activity-lifetime cache and scheduler for loopback HTTP readiness checks.
 *
 * New candidates get a short bounded retry burst. A previously verified endpoint remains
 * provisionally AVAILABLE across Activity lifecycle changes while a fresh foreground probe runs;
 * only repeated fresh failures downgrade it.
 */
class RuntimeWebAvailabilityTracker(
    private val probe: (String) -> Boolean = { RuntimeWebHttpReadinessProbe.isReady(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onChanged: () -> Unit,
) {

    private data class ProbeResult(
        val reachable: Boolean,
        val checkedAtEpochMs: Long,
        val generation: Int,
        val lifecycleGeneration: Int,
        val consecutiveFailures: Int,
        val everReachable: Boolean,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val results = mutableMapOf<String, ProbeResult>()
    private val inFlight = mutableSetOf<String>()
    private val scheduledVersions = mutableMapOf<String, Long>()
    private val generations = mutableMapOf<String, Int>()
    private var lifecycleGeneration = 0
    private var active = false

    fun resume() {
        active = true
    }

    fun pause() {
        active = false
        lifecycleGeneration += 1
        handler.removeCallbacksAndMessages(null)
        scheduledVersions.clear()
        // Keep verified identity. The next foreground lifecycle still schedules a fresh probe.
        inFlight.clear()
    }

    /**
     * Restores app-private verified identity after Activity recreation without treating it as a
     * fresh reachability result. The stale lifecycle marker forces an immediate background probe.
     */
    fun restoreVerifiedIdentity(
        projectKey: String,
        url: String,
        checkedAtEpochMs: Long,
    ) {
        if (url.isBlank()) return
        val key = key(projectKey, url)
        val generation = generation(projectKey)
        if (results[key]?.generation == generation) return
        results[key] = ProbeResult(
            reachable = true,
            checkedAtEpochMs = checkedAtEpochMs,
            generation = generation,
            lifecycleGeneration = lifecycleGeneration - 1,
            consecutiveFailures = 0,
            everReachable = true,
        )
    }

    fun invalidate(projectKey: String) {
        generations[projectKey] = generation(projectKey) + 1
        val prefix = keyPrefix(projectKey)
        results.keys.removeAll { it.startsWith(prefix) }
        inFlight.removeAll { it.startsWith(prefix) }
        scheduledVersions.keys.removeAll { it.startsWith(prefix) }
    }

    fun reachableUrl(
        projectKey: String,
        runtimeState: RuntimeState,
        candidateUrls: List<String>,
    ): String? {
        if (endpointReachable(projectKey, runtimeState, candidateUrls) != true) return null
        val urls = candidateUrls.distinct()
        return urls.firstOrNull { url ->
            val result = results[key(projectKey, url)] ?: return@firstOrNull false
            result.generation == generation(projectKey) &&
                result.lifecycleGeneration == lifecycleGeneration &&
                result.reachable
        }
    }

    fun lastKnownReachableUrl(
        projectKey: String,
        candidateUrls: List<String>,
    ): String? {
        val projectGeneration = generation(projectKey)
        return candidateUrls.distinct().firstOrNull { url ->
            results[key(projectKey, url)]?.let { result ->
                result.generation == projectGeneration &&
                    (result.reachable || RuntimeWebDetectionCadence.verifiedFailurePending(
                        everReachable = result.everReachable,
                        consecutiveFailures = result.consecutiveFailures,
                    ))
            } == true
        }
    }

    /**
     * Returns the latest tri-state endpoint fact while scheduling any missing probe.
     *
     * A stale verified fact remains provisionally true across Activity lifecycle changes. One fresh
     * miss also keeps it true while a fast confirmation probe is scheduled; the configured repeated
     * failure threshold is required before false is returned.
     */
    fun endpointReachable(
        projectKey: String,
        runtimeState: RuntimeState,
        candidateUrls: List<String>,
    ): Boolean? {
        if (runtimeState != RuntimeState.RUNNING) return null
        if (candidateUrls.isEmpty()) return false
        val now = clock()
        val urls = candidateUrls.distinct()
        val projectGeneration = generation(projectKey)
        urls.forEach { url -> ensureProbe(projectKey, url, now) }

        val current = urls.mapNotNull { url ->
            results[key(projectKey, url)]?.takeIf {
                it.generation == projectGeneration &&
                    it.lifecycleGeneration == lifecycleGeneration
            }
        }
        if (current.any { it.reachable }) return true

        val staleVerifiedExists = urls.any { url ->
            results[key(projectKey, url)]?.let { result ->
                result.generation == projectGeneration &&
                    result.lifecycleGeneration != lifecycleGeneration &&
                    result.everReachable
            } == true
        }
        if (current.size != urls.size) {
            return if (staleVerifiedExists) true else null
        }
        if (current.any {
                RuntimeWebDetectionCadence.verifiedFailurePending(
                    everReachable = it.everReachable,
                    consecutiveFailures = it.consecutiveFailures,
                )
            }
        ) {
            return true
        }
        if (current.any {
                RuntimeWebDetectionCadence.initialVerificationPending(
                    everReachable = it.everReachable,
                    consecutiveFailures = it.consecutiveFailures,
                )
            }
        ) {
            return null
        }
        return false
    }

    fun verifyNow(projectKey: String, url: String, callback: (Boolean) -> Unit) {
        val generation = generation(projectKey)
        val lifecycle = lifecycleGeneration
        Thread {
            val reachable = probe(url)
            val checkedAt = clock()
            handler.post {
                if (!active || lifecycle != lifecycleGeneration) return@post
                if (generation != generation(projectKey)) return@post
                val key = key(projectKey, url)
                val previous = results[key]
                val current = nextResult(
                    previous = previous,
                    reachable = reachable,
                    checkedAt = checkedAt,
                    generation = generation,
                    lifecycle = lifecycleGeneration,
                )
                results[key] = current
                scheduleRecheck(
                    projectKey = projectKey,
                    url = url,
                    checkedAt = checkedAt,
                    delayMs = RuntimeWebDetectionCadence.endpointRecheckDelay(
                        everReachable = current.everReachable,
                        consecutiveFailures = current.consecutiveFailures,
                    ),
                )
                notifyIfPresentationChanged(previous, current)
                callback(reachable)
            }
        }.start()
    }

    private fun ensureProbe(projectKey: String, url: String, now: Long) {
        if (!active) return
        val key = key(projectKey, url)
        val generation = generation(projectKey)
        val lifecycle = lifecycleGeneration
        val cached = results[key]
        if (
            cached != null &&
            cached.generation == generation &&
            cached.lifecycleGeneration == lifecycleGeneration
        ) {
            val delay = RuntimeWebDetectionCadence.endpointRecheckDelay(
                everReachable = cached.everReachable,
                consecutiveFailures = cached.consecutiveFailures,
            )
            val age = (now - cached.checkedAtEpochMs).coerceAtLeast(0L)
            if (age < delay) {
                scheduleRecheck(projectKey, url, cached.checkedAtEpochMs, delay - age)
                return
            }
        }
        if (!inFlight.add(key)) return

        Thread {
            val reachable = probe(url)
            val checkedAt = clock()
            handler.post {
                if (!active || lifecycle != lifecycleGeneration) return@post
                if (generation != generation(projectKey)) return@post
                inFlight.remove(key)
                val previous = results[key]
                val current = nextResult(
                    previous = previous,
                    reachable = reachable,
                    checkedAt = checkedAt,
                    generation = generation,
                    lifecycle = lifecycleGeneration,
                )
                results[key] = current
                scheduleRecheck(
                    projectKey = projectKey,
                    url = url,
                    checkedAt = checkedAt,
                    delayMs = RuntimeWebDetectionCadence.endpointRecheckDelay(
                        everReachable = current.everReachable,
                        consecutiveFailures = current.consecutiveFailures,
                    ),
                )
                notifyIfPresentationChanged(previous, current)
            }
        }.start()
    }

    private fun nextResult(
        previous: ProbeResult?,
        reachable: Boolean,
        checkedAt: Long,
        generation: Int,
        lifecycle: Int,
    ): ProbeResult {
        val sameExecution = previous?.generation == generation
        val previousFailures = if (
            sameExecution && previous?.lifecycleGeneration == lifecycle
        ) {
            previous.consecutiveFailures
        } else {
            0
        }
        val everReachable = reachable || (sameExecution && previous?.everReachable == true)
        return ProbeResult(
            reachable = reachable,
            checkedAtEpochMs = checkedAt,
            generation = generation,
            lifecycleGeneration = lifecycle,
            consecutiveFailures = if (reachable) 0 else previousFailures + 1,
            everReachable = everReachable,
        )
    }

    private fun notifyIfPresentationChanged(previous: ProbeResult?, current: ProbeResult) {
        val previousInitialPending = previous?.let {
            RuntimeWebDetectionCadence.initialVerificationPending(
                everReachable = it.everReachable,
                consecutiveFailures = it.consecutiveFailures,
            )
        } ?: true
        val currentInitialPending = RuntimeWebDetectionCadence.initialVerificationPending(
            everReachable = current.everReachable,
            consecutiveFailures = current.consecutiveFailures,
        )
        val previousVerifiedPending = previous?.let {
            RuntimeWebDetectionCadence.verifiedFailurePending(
                everReachable = it.everReachable,
                consecutiveFailures = it.consecutiveFailures,
            )
        } ?: false
        val currentVerifiedPending = RuntimeWebDetectionCadence.verifiedFailurePending(
            everReachable = current.everReachable,
            consecutiveFailures = current.consecutiveFailures,
        )
        if (
            previous?.reachable != current.reachable ||
            previousInitialPending != currentInitialPending ||
            previousVerifiedPending != currentVerifiedPending
        ) {
            onChanged()
        }
    }

    private fun scheduleRecheck(
        projectKey: String,
        url: String,
        checkedAt: Long,
        delayMs: Long,
    ) {
        if (!active) return
        val key = key(projectKey, url)
        if (scheduledVersions[key] == checkedAt) return
        scheduledVersions[key] = checkedAt
        handler.postDelayed(
            {
                if (scheduledVersions[key] != checkedAt) return@postDelayed
                scheduledVersions.remove(key)
                val current = results[key]
                if (
                    active &&
                    current?.checkedAtEpochMs == checkedAt &&
                    current.generation == generation(projectKey)
                ) {
                    ensureProbe(projectKey, url, clock())
                }
            },
            delayMs.coerceAtLeast(1L),
        )
    }

    private fun generation(projectKey: String): Int = generations[projectKey] ?: 0

    private fun keyPrefix(projectKey: String): String = "${projectKey.length}:$projectKey:"

    private fun key(projectKey: String, url: String): String = "${keyPrefix(projectKey)}$url"
}
