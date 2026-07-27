package com.dveamer.babysitter.collect

import java.util.concurrent.atomic.AtomicReference

data class CollectCrySnapshot(
    val score: Float,
    val capturedAtMs: Long
)

object CollectCryBus {
    private val latest = AtomicReference<CollectCrySnapshot?>(null)

    fun publish(snapshot: CollectCrySnapshot) {
        latest.set(snapshot)
    }

    fun latest(): CollectCrySnapshot? = latest.get()

    fun clear() {
        latest.set(null)
    }
}
