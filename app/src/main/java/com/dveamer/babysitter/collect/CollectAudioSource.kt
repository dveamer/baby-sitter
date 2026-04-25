package com.dveamer.babysitter.collect

import android.media.MediaRecorder
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CollectAudioSource(
    private val paths: CollectStoragePaths,
    private val scope: CoroutineScope
) {
    private val lock = Any()

    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var currentOutputStartMs: Long? = null
    private var levelJob: Job? = null
    private var rotateJob: Job? = null

    fun start() {
        synchronized(lock) {
            if (recorder != null) return
            paths.ensureDirectories()
            runCatching { startRecorderForCurrentMinute() }
                .onFailure { Log.w(TAG, "audio source start failed", it) }
            if (recorder == null) return

            if (levelJob == null) {
                levelJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        val amplitude = synchronized(lock) {
                            runCatching { recorder?.maxAmplitude?.toDouble() ?: 0.0 }
                                .getOrDefault(0.0)
                        }
                        CollectAudioBus.publish(
                            CollectAudioSnapshot(
                                averageAmplitude = amplitude,
                                capturedAtMs = System.currentTimeMillis()
                            )
                        )
                        delay(CollectAudioConfig.AMPLITUDE_PUBLISH_INTERVAL_MS)
                    }
                }
            }

            if (rotateJob == null) {
                rotateJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(msUntilNextMinute())
                        synchronized(lock) {
                            if (recorder != null) {
                                runCatching {
                                    stopRecorder()
                                    startRecorderForCurrentMinute()
                                }.onFailure { Log.w(TAG, "audio collect rotation failed", it) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            levelJob?.cancel()
            rotateJob?.cancel()
            levelJob = null
            rotateJob = null
            stopRecorder()
        }
        CollectAudioBus.clear()
    }

    private fun startRecorderForCurrentMinute() {
        val startMs = CollectFileNaming.minuteFloor(System.currentTimeMillis())
        val file = File(paths.collectDir, CollectFileNaming.collectAudioFileName(startMs))
        currentOutputFile = file
        currentOutputStartMs = startMs
        @Suppress("DEPRECATION")
        val next = MediaRecorder()
        runCatching {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(CollectAudioConfig.ENCODING_BIT_RATE)
            next.setAudioSamplingRate(CollectAudioConfig.SAMPLING_RATE)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
        }.onFailure { e ->
            runCatching { next.reset() }
            runCatching { next.release() }
            throw e
        }
        recorder = next
    }

    private fun stopRecorder() {
        recorder?.let { current ->
            runCatching { current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        currentOutputFile?.let { output ->
            val startMs = currentOutputStartMs ?: CollectFileNaming.minuteFloor(System.currentTimeMillis())
            if (output.exists() && output.length() > 0L) {
                Log.d(TAG, "closed collect audio file=${output.name} size=${output.length()} startMs=$startMs")
                CollectClosedFileBus.publish(
                    CollectClosedFileMeta(
                        type = CollectFileType.AUDIO,
                        file = output,
                        startMs = startMs,
                        closedAtMs = System.currentTimeMillis()
                    )
                )
            } else {
                Log.w(TAG, "collect audio file not published file=${output.name} exists=${output.exists()} size=${output.length()}")
            }
        }
        recorder = null
        currentOutputFile = null
        currentOutputStartMs = null
    }

    private fun msUntilNextMinute(nowMs: Long = System.currentTimeMillis()): Long {
        val next = CollectFileNaming.minuteFloor(nowMs) + 60_000L
        return (next - nowMs).coerceIn(500L, 60_000L)
    }

    private companion object {
        const val TAG = "CollectAudioSource"
    }
}
