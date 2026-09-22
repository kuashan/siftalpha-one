package com.siftalpha.studio.runtime

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOperationCoordinatorTest {
    @Test
    fun normalAndDeveloperSurfacesShareOneOperationOwner() {
        val coordinator = coordinator(listenForExternalResults = false)

        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
        )!!
        assertNotNull(
            coordinator.bindExternalExecution(
                projectId = "project-a",
                generation = prepare.generation,
                executionId = 7101,
            ),
        )

        // The second surface sees the same current operation and cannot create another generation.
        assertNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.START,
            ),
        )

        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
        )
        assertNotNull(stop)
        assertTrue(stop!!.generation > prepare.generation)
        assertEquals(RuntimeOperationAction.STOP, coordinator.current("project-a")?.action)
        assertEquals(
            ExternalResultDisposition.FENCED,
            coordinator.consumeExternalResultDisposition(7101),
        )
        assertNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STOP,
            ),
        )
    }

    @Test
    fun externalPrepareAndStopReceiveTheirActionDeadlines() {
        var now = 10_000L
        val coordinator = coordinator(
            nowEpochMs = { now },
            listenForExternalResults = false,
        )

        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
        )!!
        assertEquals(now + RuntimeOperationContract.PREPARE_TIMEOUT_MS, prepare.deadlineAtEpochMs)

        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
        )!!
        assertEquals(
            now + RuntimeOperationContract.EXTERNAL_STOP_TIMEOUT_MS,
            stop.deadlineAtEpochMs,
        )
    }

    @Test
    fun externalWatchdogExpiresOperationAndReleasesTheProjectLock() {
        var now = 20_000L
        val watchdog = ManualWatchdog()
        val timeouts = mutableListOf<ProjectOperationCoordinator.ExternalTimeout>()
        val coordinator = coordinator(
            nowEpochMs = { now },
            watchdog = watchdog,
            listenForExternalResults = false,
        )
        coordinator.addTimeoutListener(timeouts::add)

        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7201,
        )!!
        now += RuntimeOperationContract.STOP_TIMEOUT_MS + 1L
        watchdog.fire("project-a", stop.generation)

        assertNull(coordinator.current("project-a"))
        assertEquals(1, timeouts.size)
        assertEquals(RuntimeOperationAction.STOP, timeouts.single().action)
        assertEquals("RUNTIME_OPERATION_TIMED_OUT:STOP", timeouts.single().failureReason)

        // A timed-out STOP cannot leave the project permanently locked.
        assertNotNull(
            coordinator.begin(
                projectId = "project-a",
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.STATUS,
            ),
        )
    }

    @Test
    fun stopTimeoutFencesLateStopResultAndDoesNotCompleteIt() {
        var now = 30_000L
        val watchdog = ManualWatchdog()
        val completions = mutableListOf<ProjectOperationCoordinator.ExternalCompletion>()
        val coordinator = coordinator(
            nowEpochMs = { now },
            watchdog = watchdog,
            listenForExternalResults = true,
        )
        coordinator.addCompletionListener(completions::add)
        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7301,
        )!!

        now += RuntimeOperationContract.STOP_TIMEOUT_MS + 1L
        watchdog.fire("project-a", stop.generation)
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7301,
                stdout = "SIFTALPHA_STATUS=STOPPED_BY_USER",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertTrue(completions.isEmpty())
        assertEquals(
            ExternalResultDisposition.FENCED,
            coordinator.consumeExternalResultDisposition(7301),
        )
    }

    @Test
    fun latePrepareResultAfterStopCannotMutateTheNewGeneration() {
        val coordinator = coordinator(listenForExternalResults = true)
        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7401,
        )!!
        val stop = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7402,
        )!!

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7401,
                stdout = "SIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertEquals(stop.generation, coordinator.current("project-a")?.generation)
        assertEquals(RuntimeOperationAction.STOP, coordinator.current("project-a")?.action)
        assertEquals(
            ExternalResultDisposition.FENCED,
            coordinator.consumeExternalResultDisposition(7401),
        )
        assertEquals(ExternalResultDisposition.ACCEPTED, coordinator.consumeExternalResultDisposition(7402))
        assertEquals(prepare.generation + 1L, stop.generation)
    }

    @Test
    fun successfulExternalCallbackBeforeDeadlineUsesTheOriginalCompletionPath() {
        val coordinator = coordinator(listenForExternalResults = true)
        val completions = mutableListOf<ProjectOperationCoordinator.ExternalCompletion>()
        coordinator.addCompletionListener(completions::add)
        val prepare = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7501,
        )!!

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7501,
                stdout = "SIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        assertNull(coordinator.current("project-a"))
        assertEquals(1, completions.size)
        assertEquals(prepare.generation, completions.single().generation)
        assertEquals(ExternalResultDisposition.ACCEPTED, coordinator.consumeExternalResultDisposition(7501))
    }

    @Test
    fun successfulExternalPrepareLeavesPreparingReadyAndNoActiveOperation() {
        val lifecycle = RuntimeLifecycleStore(MemorySharedPreferences())
        val coordinator = coordinator(
            listenForExternalResults = true,
            lifecycleStore = lifecycle,
        )
        coordinator.begin(
            projectId = "project-prepare-success",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7701,
        )!!

        assertEquals(
            RuntimeState.PREPARING,
            lifecycle.read("project-prepare-success").runtimeState,
        )

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7701,
                stdout = "SIFTALPHA_PREPARE_COMMITTED=1\nSIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        val snapshot = lifecycle.read("project-prepare-success")
        assertEquals(true, snapshot.environmentReadyFor(ProjectRuntimeSelection.TERMUX))
        assertEquals(RuntimeState.UNKNOWN, snapshot.runtimeState)
        assertNull(snapshot.failureReason)
        assertNull(coordinator.current("project-prepare-success"))
        assertNull(coordinator.persisted("project-prepare-success"))
    }

    @Test
    fun successfulExternalPrepareRefreshesRuntimeCapabilityProof() {
        val readinessStore = ExternalProviderReadinessStore(MemorySharedPreferences())
        readinessStore.recordHostFacts(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )
        val coordinator = ProjectOperationCoordinator(
            operationStore = MemoryOperationStore(),
            lifecycleStore = RuntimeLifecycleStore(MemorySharedPreferences()),
            nowEpochMs = { 42_000L },
            listenForExternalResults = true,
            watchdog = ManualWatchdog(),
            externalReadinessStore = readinessStore,
        )
        coordinator.begin(
            projectId = "project-prepare-proof",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7791,
        )!!

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7791,
                stdout = "SIFTALPHA_PREPARE_COMMITTED=1\nSIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        val facts = readinessStore.read()
        assertEquals(ExternalProviderBridgeState.PASS, facts.bridgeState)
        assertEquals(ExternalProviderProbeStage.RUNTIME_CAPABILITY, facts.probeStage)
        assertEquals(ExternalProviderProbeResult.PASS, facts.lastProbeResult)
        assertEquals(42_000L, facts.lastProbeAtEpochMs)
    }

    @Test
    fun failedExternalPrepareLeavesPreparingFailedAndNoActiveOperation() {
        val lifecycle = RuntimeLifecycleStore(MemorySharedPreferences())
        val coordinator = coordinator(
            listenForExternalResults = true,
            lifecycleStore = lifecycle,
        )
        coordinator.begin(
            projectId = "project-prepare-failure",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7702,
        )!!

        assertEquals(
            RuntimeState.PREPARING,
            lifecycle.read("project-prepare-failure").runtimeState,
        )

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7702,
                stdout = "",
                stderr = "dependency verification failed",
                exitCode = 1,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        val snapshot = lifecycle.read("project-prepare-failure")
        assertNull(snapshot.environmentReadyFor(ProjectRuntimeSelection.TERMUX))
        assertEquals(RuntimeState.ENVIRONMENT_ERROR, snapshot.runtimeState)
        assertEquals("dependency verification failed", snapshot.failureReason)
        assertNull(coordinator.current("project-prepare-failure"))
        assertNull(coordinator.persisted("project-prepare-failure"))
    }

    @Test
    fun stopDuringExternalPrepareEndsStoppedAndLeavesNoPrepareOperation() {
        val lifecycle = RuntimeLifecycleStore(MemorySharedPreferences())
        val coordinator = coordinator(
            listenForExternalResults = true,
            lifecycleStore = lifecycle,
        )
        coordinator.begin(
            projectId = "project-prepare-stop",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.PREPARE,
            executionId = 7703,
        )!!

        assertEquals(
            RuntimeState.PREPARING,
            lifecycle.read("project-prepare-stop").runtimeState,
        )

        val stop = coordinator.begin(
            projectId = "project-prepare-stop",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7704,
        )!!
        assertEquals(RuntimeOperationAction.STOP, stop.action)

        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7704,
                stdout = "SIFTALPHA_STATUS=STOPPED_BY_USER",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )

        var snapshot = lifecycle.read("project-prepare-stop")
        assertEquals(RuntimeState.STOPPED_BY_USER, snapshot.runtimeState)
        assertNull(coordinator.current("project-prepare-stop"))
        assertNull(coordinator.persisted("project-prepare-stop"))

        // A late result from the superseded PREPARE generation stays fenced.
        TermuxResultBus.publish(
            RuntimeResult(
                executionId = 7703,
                stdout = "SIFTALPHA_ENV=READY",
                stderr = "",
                exitCode = 0,
                internalErrorCode = 0,
                internalErrorMessage = "",
            ),
        )
        snapshot = lifecycle.read("project-prepare-stop")
        assertEquals(RuntimeState.STOPPED_BY_USER, snapshot.runtimeState)
        assertNull(coordinator.current("project-prepare-stop"))
    }

    @Test
    fun timeoutOfProjectADoesNotAffectProjectB() {
        var now = 40_000L
        val watchdog = ManualWatchdog()
        val coordinator = coordinator(
            nowEpochMs = { now },
            watchdog = watchdog,
            listenForExternalResults = false,
        )
        val a = coordinator.begin(
            projectId = "project-a",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.STOP,
            executionId = 7601,
        )!!
        val b = coordinator.begin(
            projectId = "project-b",
            provider = RuntimeOperationProvider.EXTERNAL,
            action = RuntimeOperationAction.START,
            executionId = 7602,
        )!!

        now += RuntimeOperationContract.EXTERNAL_STOP_TIMEOUT_MS + 1L
        watchdog.fire("project-a", a.generation)

        assertNull(coordinator.current("project-a"))
        assertEquals(b.generation, coordinator.current("project-b")?.generation)
        assertEquals(RuntimeOperationAction.START, coordinator.current("project-b")?.action)
    }

    private fun coordinator(
        nowEpochMs: () -> Long = { 1_000L },
        listenForExternalResults: Boolean,
        watchdog: RuntimeOperationWatchdog = ManualWatchdog(),
        lifecycleStore: RuntimeLifecycleStore? = null,
    ) = ProjectOperationCoordinator(
        operationStore = MemoryOperationStore(),
        lifecycleStore = lifecycleStore,
        nowEpochMs = nowEpochMs,
        listenForExternalResults = listenForExternalResults,
        watchdog = watchdog,
    )

    private class ManualWatchdog : RuntimeOperationWatchdog {
        private data class Key(val projectId: String, val generation: Long)

        private val callbacks = linkedMapOf<Key, () -> Unit>()

        override fun schedule(
            record: RuntimeOperationRecord,
            delayMs: Long,
            onTimeout: () -> Unit,
        ) {
            callbacks[Key(record.projectId, record.generation)] = onTimeout
        }

        override fun cancel(projectId: String, generation: Long) {
            callbacks.remove(Key(projectId, generation))
        }

        fun fire(projectId: String, generation: Long) {
            callbacks.remove(Key(projectId, generation))?.invoke()
        }
    }

    private class MemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            (values[key] as? Set<String>)?.toSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = linkedMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                put(key, value)
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
                put(key, values?.toSet())
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)
            override fun remove(key: String): SharedPreferences.Editor {
                changes[key] = REMOVED
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }
            override fun commit(): Boolean {
                applyChanges()
                return true
            }
            override fun apply() {
                applyChanges()
            }
            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                changes[key] = value ?: REMOVED
                return this
            }
            private fun applyChanges() {
                if (clearRequested) values.clear()
                changes.forEach { (key, value) ->
                    if (value === REMOVED) values.remove(key) else values[key] = value
                }
            }
        }

        private companion object {
            val REMOVED = Any()
        }
    }

    private class MemoryOperationStore : RuntimeOperationRecordStore {
        private val records = linkedMapOf<String, RuntimeOperationRecord>()
        private val generations = linkedMapOf<String, Long>()

        override fun read(projectId: String): RuntimeOperationRecord? = records[projectId]

        override fun lastGeneration(projectId: String): Long = generations[projectId] ?: 0L

        override fun write(record: RuntimeOperationRecord) {
            records[record.projectId] = record
            generations[record.projectId] = maxOf(generations[record.projectId] ?: 0L, record.generation)
        }

        override fun clear(projectId: String) {
            records.remove(projectId)
        }
    }
}
