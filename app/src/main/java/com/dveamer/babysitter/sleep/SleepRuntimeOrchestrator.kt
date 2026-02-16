package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SleepRuntimeOrchestrator(
    private val repository: SettingsRepository,
    private val runtime: SleepRuntime
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            repository.state.collectLatest { state ->
                if (state.sleepEnabled) {
                    runtime.start()
                } else {
                    runtime.stop()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

interface SleepRuntime {
    suspend fun start()
    suspend fun stop()
}
