package com.dveamer.babysitter.monitor

enum class MonitorKind {
    MICROPHONE,
    CAMERA,
    OTHER
}

data class MonitorSignal(
    val monitorId: String,
    val kind: MonitorKind,
    val active: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
