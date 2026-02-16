package com.dveamer.babysitter.monitor

import kotlinx.coroutines.flow.Flow

interface Monitor {
    val id: String
    val signals: Flow<MonitorSignal>

    suspend fun start()
    suspend fun stop()
}
