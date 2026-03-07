package com.dveamer.babysitter.collect

import android.media.MediaMetadataRetriever
import java.io.File

data class MemoryFileMeta(
    val fileName: String,
    val startEpochMs: Long,
    val sizeBytes: Long,
    val durationMs: Long
)

class MemoryRepository(
    private val catalog: CollectCatalog
) {
    fun listLatest(): List<MemoryFileMeta> {
        return catalog.listMemoryVideosSortedDesc().map { timed ->
            MemoryFileMeta(
                fileName = timed.file.name,
                startEpochMs = timed.startMs,
                sizeBytes = timed.file.length(),
                durationMs = extractDurationMs(timed.file)
            )
        }
    }

    fun findByName(fileName: String): File? {
        if (!fileName.matches(Regex("^memory_\\d{8}_\\d{4}\\.mp4$"))) return null
        return catalog.listMemoryVideosSortedDesc()
            .firstOrNull { it.file.name == fileName }
            ?.file
    }

    private fun extractDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        }.getOrDefault(0L)
            .also { runCatching { retriever.release() } }
    }
}
