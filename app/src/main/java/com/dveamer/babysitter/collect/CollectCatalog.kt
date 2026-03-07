package com.dveamer.babysitter.collect

import java.io.File

data class TimedFile(
    val file: File,
    val startMs: Long
)

class CollectCatalog(
    private val paths: CollectStoragePaths
) {
    fun listCollectVideosSorted(): List<TimedFile> {
        return listTimedFiles(paths.collectDir) { CollectFileNaming.parseCollectVideoStartMs(it) }
    }

    fun listCollectAudiosSorted(): List<TimedFile> {
        return listTimedFiles(paths.collectDir) { CollectFileNaming.parseCollectAudioStartMs(it) }
    }

    fun listMemoryVideosSortedDesc(): List<TimedFile> {
        return listTimedFiles(paths.memoryDir) { CollectFileNaming.parseMemoryStartMs(it) }
            .sortedByDescending { it.startMs }
    }

    private fun listTimedFiles(dir: File, parser: (File) -> Long?): List<TimedFile> {
        val files = dir.listFiles().orEmpty()
        return files.mapNotNull { file ->
            val start = parser(file) ?: return@mapNotNull null
            TimedFile(file = file, startMs = start)
        }.sortedBy { it.startMs }
    }
}
