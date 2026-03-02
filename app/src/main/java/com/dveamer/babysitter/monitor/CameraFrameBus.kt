package com.dveamer.babysitter.monitor

import java.util.concurrent.atomic.AtomicReference

data class CameraFrameSnapshot(
    val jpeg: ByteArray,
    val capturedAtMs: Long
)

object CameraFrameBus {
    private val latest = AtomicReference<CameraFrameSnapshot?>(null)

    fun publish(snapshot: CameraFrameSnapshot) {
        latest.set(snapshot)
    }

    fun latest(): CameraFrameSnapshot? = latest.get()

    fun clear() {
        latest.set(null)
    }
}
