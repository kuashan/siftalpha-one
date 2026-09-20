package com.siftalpha.studio.runtime

/**
 * Per-project polling ownership for Embedded R observation.
 *
 * The registry deliberately separates "tracked" from "in flight" so one project's snapshot
 * request cannot suppress polling for another project.
 */
class EmbeddedProjectPollRegistry<T> {
    private val projects = linkedMapOf<String, T>()
    private val inFlight = mutableSetOf<String>()

    fun track(projectKey: String, project: T) {
        projects[projectKey] = project
    }

    fun untrack(projectKey: String): T? {
        inFlight.remove(projectKey)
        return projects.remove(projectKey)
    }

    fun project(projectKey: String): T? = projects[projectKey]

    fun trackedProjects(): List<T> = projects.values.toList()

    fun trackedKeys(): Set<String> = projects.keys.toSet()

    fun begin(projectKey: String): Boolean =
        projects.containsKey(projectKey) && inFlight.add(projectKey)

    fun finish(projectKey: String) {
        inFlight.remove(projectKey)
    }

    fun isInFlight(projectKey: String): Boolean = projectKey in inFlight

    fun clear() {
        projects.clear()
        inFlight.clear()
    }
}
