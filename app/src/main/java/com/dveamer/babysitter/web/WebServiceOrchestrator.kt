package com.dveamer.babysitter.web

import com.dveamer.babysitter.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WebServiceOrchestrator(
    private val repository: SettingsRepository,
    private val server: LocalSettingsHttpServer
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            repository.state.collectLatest { state ->
                if (state.webServiceEnabled) {
                    server.start()
                } else {
                    server.stop()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
