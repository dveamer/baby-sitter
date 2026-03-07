package com.dveamer.babysitter.sleep

import android.util.Log
import com.dveamer.babysitter.collect.CollectCatalog
import com.dveamer.babysitter.collect.CollectStoragePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MaintenanceScheduler(
    private val paths: CollectStoragePaths,
    private val catalog: CollectCatalog,
    private val isAwakeSessionActive: () -> Boolean,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        paths.ensureDirectories()
        job = scope.launch(workerDispatcher) {
            while (isActive) {
                runCatching { runOnce(nowProvider()) }
                    .onFailure { Log.w(TAG, "maintenance cycle failed", it) }
                delay(RUN_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun runOnce(nowMs: Long = nowProvider()) {
        if (!isAwakeSessionActive()) {
            val deleteBefore = nowMs - COLLECT_RETENTION_MS
            catalog.listCollectVideosSorted().forEach { timed ->
                if (timed.startMs < deleteBefore) {
                    runCatching { timed.file.delete() }
                }
            }
            catalog.listCollectAudiosSorted().forEach { timed ->
                if (timed.startMs < deleteBefore) {
                    runCatching { timed.file.delete() }
                }
            }
        }

        val memoryDeleteBefore = nowMs - MEMORY_RETENTION_MS
        catalog.listMemoryVideosSortedDesc().forEach { timed ->
            if (timed.startMs < memoryDeleteBefore) {
                runCatching { timed.file.delete() }
            }
        }
    }

    companion object {
        const val RUN_INTERVAL_MS = 60 * 60 * 1000L
        const val COLLECT_RETENTION_MS = 60 * 60 * 1000L
        const val MEMORY_RETENTION_MS = 3 * 24 * 60 * 60 * 1000L
        private const val TAG = "MaintenanceScheduler"
    }
}
