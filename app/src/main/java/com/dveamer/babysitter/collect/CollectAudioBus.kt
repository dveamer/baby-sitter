package com.dveamer.babysitter.collect

import java.util.concurrent.atomic.AtomicReference

data class CollectAudioSnapshot(
    val averageAmplitude: Double,
    val capturedAtMs: Long
)

object CollectAudioBus {
    private val latest = AtomicReference<CollectAudioSnapshot?>(null)

    fun publish(snapshot: CollectAudioSnapshot) {
        latest.set(snapshot)
    }

    fun latest(): CollectAudioSnapshot? = latest.get()

    fun clear() {
        latest.set(null)
    }
}
