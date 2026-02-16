package com.dveamer.babysitter.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CameraMonitor(
    private val scope: CoroutineScope,
    override val id: String = "camera"
) : Monitor {

    private val mutableSignals = MutableSharedFlow<MonitorSignal>(extraBufferCapacity = 16)
    override val signals: Flow<MonitorSignal> = mutableSignals.asSharedFlow()

    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                // TODO: Replace with real movement detection based on CameraX stream analysis.
                mutableSignals.emit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.CAMERA,
                        active = false
                    )
                )
                delay(1_000)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
    }
}
