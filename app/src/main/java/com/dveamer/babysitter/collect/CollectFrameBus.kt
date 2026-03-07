package com.dveamer.babysitter.collect

import java.util.concurrent.atomic.AtomicReference

data class CollectFrameSnapshot(
    val gray: IntArray,
    val width: Int,
    val height: Int,
    val capturedAtMs: Long
)

object CollectFrameBus {
    private val latest = AtomicReference<CollectFrameSnapshot?>(null)

    fun publish(snapshot: CollectFrameSnapshot) {
        latest.set(snapshot)
    }

    fun latest(): CollectFrameSnapshot? = latest.get()

    fun clear() {
        latest.set(null)
    }
}
