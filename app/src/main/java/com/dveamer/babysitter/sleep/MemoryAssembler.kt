package com.dveamer.babysitter.sleep

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.dveamer.babysitter.collect.CollectCatalog
import com.dveamer.babysitter.collect.CollectFileNaming
import com.dveamer.babysitter.collect.CollectStoragePaths
import com.dveamer.babysitter.collect.TimedFile
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

data class MemoryBuildRequest(
    val rangeStartMs: Long,
    val rangeEndMs: Long
)

data class MemoryBuildResult(
    val outputFile: File?,
    val usedVideoFiles: Int,
    val usedAudioFiles: Int,
    val skippedReason: String? = null
)

class MemoryAssembler(
    private val paths: CollectStoragePaths,
    private val catalog: CollectCatalog
) {
    fun build(request: MemoryBuildRequest): MemoryBuildResult {
        paths.ensureDirectories()

        val allVideos = catalog.listCollectVideosSorted()
        if (allVideos.isEmpty()) {
            return MemoryBuildResult(outputFile = null, usedVideoFiles = 0, usedAudioFiles = 0, skippedReason = "no_video_collect")
        }

        val selectedStartMs = resolveStartMinute(allVideos, request.rangeStartMs)
            ?: return MemoryBuildResult(outputFile = null, usedVideoFiles = 0, usedAudioFiles = 0, skippedReason = "start_not_found")

        val endFloor = CollectFileNaming.minuteFloor(request.rangeEndMs)
        val selectedVideos = allVideos.filter { it.startMs in selectedStartMs..endFloor }
            .filter { it.file.exists() && it.file.length() > MIN_COLLECT_FILE_BYTES }
        if (selectedVideos.isEmpty()) {
            return MemoryBuildResult(outputFile = null, usedVideoFiles = 0, usedAudioFiles = 0, skippedReason = "no_video_in_range")
        }

        val selectedAudios = catalog.listCollectAudiosSorted()
            .filter { it.startMs in selectedStartMs..endFloor }
            .filter { it.file.exists() && it.file.length() > MIN_COLLECT_FILE_BYTES }

        val outputName = CollectFileNaming.memoryVideoFileName(selectedStartMs)
        val tempOutput = File(paths.workDir, "tmp_$outputName")
        val finalOutput = File(paths.memoryDir, outputName)
        runCatching { tempOutput.delete() }

        val built = runCatching {
            muxTimeline(tempOutput, selectedVideos, selectedAudios)
        }.onFailure { e ->
            Log.w(TAG, "memory mux failed", e)
        }.getOrDefault(false)

        if (!built || !tempOutput.exists()) {
            return MemoryBuildResult(outputFile = null, usedVideoFiles = selectedVideos.size, usedAudioFiles = selectedAudios.size, skippedReason = "mux_failed")
        }

        commitTempToFinal(tempOutput, finalOutput)
        return MemoryBuildResult(
            outputFile = finalOutput,
            usedVideoFiles = selectedVideos.size,
            usedAudioFiles = selectedAudios.size
        )
    }

    private fun resolveStartMinute(videos: List<TimedFile>, requestedStartMs: Long): Long? {
        val base = CollectFileNaming.minuteFloor(requestedStartMs)
        val candidates = listOf(base, base + 60_000L, base + 120_000L, base + 180_000L)
        return candidates.firstOrNull { candidate -> videos.any { it.startMs == candidate } }
    }

    private fun muxTimeline(outputFile: File, videos: List<TimedFile>, audios: List<TimedFile>): Boolean {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var muxAudioTrack = -1

        val firstVideoTrack = findTrack(videos.first().file, "video/")
        if (firstVideoTrack == null) {
            runCatching { muxer.release() }
            return false
        }
        val muxVideoTrack = muxer.addTrack(firstVideoTrack.format)

        if (audios.isNotEmpty()) {
            val firstAudioTrack = findTrack(audios.first().file, "audio/")
            if (firstAudioTrack != null) {
                muxAudioTrack = muxer.addTrack(firstAudioTrack.format)
            }
        }

        return try {
            muxer.start()
            started = true

            val videoWritten = appendTrackFiles(
                files = videos.map { it.file },
                mimePrefix = "video/",
                muxer = muxer,
                outputTrack = muxVideoTrack,
                defaultFrameDurationUs = 33_333L
            )
            val audioWritten = if (muxAudioTrack >= 0) {
                appendTrackFiles(
                    files = audios.map { it.file },
                    mimePrefix = "audio/",
                    muxer = muxer,
                    outputTrack = muxAudioTrack,
                    defaultFrameDurationUs = 21_333L
                )
            } else {
                false
            }
            videoWritten || audioWritten
        } catch (t: Throwable) {
            Log.w(TAG, "muxTimeline failed", t)
            false
        } finally {
            if (started) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private fun appendTrackFiles(
        files: List<File>,
        mimePrefix: String,
        muxer: MediaMuxer,
        outputTrack: Int,
        defaultFrameDurationUs: Long
    ): Boolean {
        var writtenAny = false
        var ptsOffsetUs = 0L
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()

        files.forEach { file ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val track = findTrack(extractor, mimePrefix) ?: return@forEach
                extractor.selectTrack(track.index)

                var lastSamplePts = 0L
                var lastDeltaUs = defaultFrameDurationUs
                var prevSamplePts: Long? = null

                while (true) {
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break

                    val sampleTimeUs = max(0L, extractor.sampleTime)
                    val presentationTimeUs = ptsOffsetUs + sampleTimeUs
                    info.presentationTimeUs = presentationTimeUs
                    info.flags = extractor.sampleFlags
                    info.offset = 0
                    muxer.writeSampleData(outputTrack, buffer, info)
                    writtenAny = true

                    val prev = prevSamplePts
                    if (prev != null && sampleTimeUs > prev) {
                        lastDeltaUs = sampleTimeUs - prev
                    }
                    prevSamplePts = sampleTimeUs
                    lastSamplePts = sampleTimeUs
                    extractor.advance()
                }

                ptsOffsetUs += lastSamplePts + lastDeltaUs
            } catch (t: Throwable) {
                Log.w(TAG, "appendTrackFiles failed: ${file.name}", t)
            } finally {
                runCatching { extractor.release() }
            }
        }

        return writtenAny
    }

    private data class ExtractorTrack(
        val index: Int,
        val format: MediaFormat
    )

    private fun findTrack(file: File, mimePrefix: String): ExtractorTrack? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            findTrack(extractor, mimePrefix)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): ExtractorTrack? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) {
                return ExtractorTrack(index = i, format = format)
            }
        }
        return null
    }

    private fun commitTempToFinal(tempFile: File, finalFile: File) {
        finalFile.parentFile?.mkdirs()
        if (finalFile.exists() && !finalFile.delete()) {
            error("failed to replace existing memory file: ${finalFile.absolutePath}")
        }
        if (tempFile.renameTo(finalFile)) {
            return
        }

        tempFile.copyTo(finalFile, overwrite = true)
        if (!tempFile.delete()) {
            Log.w(TAG, "failed to delete temp memory file after copy: ${tempFile.absolutePath}")
        }
    }

    private companion object {
        const val TAG = "MemoryAssembler"
        const val MIN_COLLECT_FILE_BYTES = 1024L
    }
}
