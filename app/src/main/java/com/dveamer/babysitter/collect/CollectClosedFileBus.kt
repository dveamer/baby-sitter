package com.dveamer.babysitter.collect

import java.io.File
import java.util.concurrent.atomic.AtomicReference

enum class CollectFileType {
    VIDEO,
    AUDIO
}

data class CollectClosedFileMeta(
    val type: CollectFileType,
    val file: File,
    val startMs: Long,
    val closedAtMs: Long
)

object CollectClosedFileBus {
    private val latestAny = AtomicReference<CollectClosedFileMeta?>(null)
    private val latestVideo = AtomicReference<CollectClosedFileMeta?>(null)
    private val latestAudio = AtomicReference<CollectClosedFileMeta?>(null)

    fun publish(meta: CollectClosedFileMeta) {
        latestAny.set(meta)
        when (meta.type) {
            CollectFileType.VIDEO -> latestVideo.set(meta)
            CollectFileType.AUDIO -> latestAudio.set(meta)
        }
    }

    fun latest(): CollectClosedFileMeta? = latestAny.get()

    fun latest(type: CollectFileType): CollectClosedFileMeta? {
        return when (type) {
            CollectFileType.VIDEO -> latestVideo.get()
            CollectFileType.AUDIO -> latestAudio.get()
        }
    }

    fun clear() {
        latestAny.set(null)
        latestVideo.set(null)
        latestAudio.set(null)
    }
}
