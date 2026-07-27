package com.dveamer.babysitter.collect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class CollectAudioSource(
    context: Context,
    private val paths: CollectStoragePaths
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var running = false

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    fun start() {
        synchronized(lock) {
            if (captureThread != null) return

            paths.ensureDirectories()
            CollectAudioBus.clear()
            CollectCryBus.clear()
            running = true

            captureThread = Thread(
                { captureLoop() },
                CAPTURE_THREAD_NAME
            ).also { it.start() }
        }
    }

    fun stop() {
        val thread: Thread?
        val record: AudioRecord?
        synchronized(lock) {
            running = false
            thread = captureThread
            record = audioRecord
        }

        runCatching {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(STOP_JOIN_TIMEOUT_MS) }
            if (thread.isAlive) {
                Log.w(TAG, "audio capture thread did not stop within timeout")
                thread.interrupt()
            }
        }

        CollectAudioBus.clear()
        CollectCryBus.clear()
    }

    private fun captureLoop() {
        var record: AudioRecord? = null
        var encoder: MediaCodec? = null
        var classifier: YamNetCryClassifier? = null
        var muxer: MinuteAudioMuxer? = null

        try {
            classifier = runCatching { YamNetCryClassifier(appContext) }
                .onFailure { Log.e(TAG, "YAMNet initialization failed", it) }
                .getOrNull()
            record = createAudioRecord()
            encoder = createAacEncoder()
            muxer = MinuteAudioMuxer(paths)

            synchronized(lock) {
                if (!running) return
                audioRecord = record
            }

            val startedAtMs = System.currentTimeMillis()
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord did not enter recording state"
            }

            val pcm = ShortArray(READ_SAMPLE_COUNT)
            var queuedSampleCount = 0L
            var peakAmplitudeSincePublish = 0
            var lastAmplitudePublishedAtMs = startedAtMs

            while (running && !Thread.currentThread().isInterrupted) {
                val readCount = record.read(
                    pcm,
                    0,
                    pcm.size,
                    AudioRecord.READ_BLOCKING
                )
                if (readCount <= 0) {
                    if (readCount < 0 && running) {
                        Log.w(TAG, "AudioRecord read failed code=$readCount")
                    }
                    continue
                }

                val capturedAtMs = System.currentTimeMillis()
                peakAmplitudeSincePublish = max(
                    peakAmplitudeSincePublish,
                    peakAmplitude(pcm, readCount)
                )
                if (
                    capturedAtMs - lastAmplitudePublishedAtMs >=
                    CollectAudioConfig.AMPLITUDE_PUBLISH_INTERVAL_MS
                ) {
                    CollectAudioBus.publish(
                        CollectAudioSnapshot(
                            averageAmplitude = peakAmplitudeSincePublish.toDouble(),
                            capturedAtMs = capturedAtMs
                        )
                    )
                    peakAmplitudeSincePublish = 0
                    lastAmplitudePublishedAtMs = capturedAtMs
                }

                runCatching {
                    classifier?.accept(
                        samples = pcm,
                        offset = 0,
                        size = readCount,
                        capturedAtMs = capturedAtMs
                    )
                }.onFailure {
                    Log.e(TAG, "YAMNet classification failed; disabling classifier", it)
                    runCatching { classifier?.close() }
                    classifier = null
                    CollectCryBus.clear()
                }.getOrNull()?.let(CollectCryBus::publish)

                queuedSampleCount = queuePcm(
                    encoder = encoder,
                    pcm = pcm,
                    sampleCount = readCount,
                    queuedSampleCount = queuedSampleCount,
                    muxer = muxer,
                    startedAtMs = startedAtMs
                )
            }

            queueEndOfStream(
                encoder = encoder,
                queuedSampleCount = queuedSampleCount,
                muxer = muxer,
                startedAtMs = startedAtMs
            )
        } catch (e: Throwable) {
            if (running) {
                Log.e(TAG, "audio capture failed", e)
            } else {
                Log.d(TAG, "audio capture stopped while initializing")
            }
        } finally {
            running = false
            runCatching {
                if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
            runCatching { record?.release() }
            runCatching { classifier?.close() }
            runCatching { muxer?.close() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }

            synchronized(lock) {
                if (captureThread === Thread.currentThread()) {
                    captureThread = null
                }
                if (audioRecord === record) {
                    audioRecord = null
                }
            }
            CollectAudioBus.clear()
            CollectCryBus.clear()
        }
    }

    private fun createAudioRecord(): AudioRecord {
        check(
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "RECORD_AUDIO permission is required"
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            CollectAudioConfig.SAMPLING_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBufferSize > 0) { "Unsupported AudioRecord configuration: $minBufferSize" }

        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(CollectAudioConfig.SAMPLING_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBufferSize, AUDIO_RECORD_BUFFER_BYTES))
            .build()

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            "AudioRecord initialization failed"
        }
        return record
    }

    private fun createAacEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            CollectAudioConfig.SAMPLING_RATE,
            CHANNEL_COUNT
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, CollectAudioConfig.ENCODING_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, ENCODER_MAX_INPUT_BYTES)
        }

        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }
    }

    private fun queuePcm(
        encoder: MediaCodec,
        pcm: ShortArray,
        sampleCount: Int,
        queuedSampleCount: Long,
        muxer: MinuteAudioMuxer,
        startedAtMs: Long
    ): Long {
        var sourceOffset = 0
        var totalQueued = queuedSampleCount

        while (sourceOffset < sampleCount) {
            val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                drainEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    startedAtMs = startedAtMs,
                    waitForEndOfStream = false
                )
                continue
            }

            val inputBuffer = checkNotNull(encoder.getInputBuffer(inputIndex))
            inputBuffer.clear()
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val samplesToQueue = minOf(
                sampleCount - sourceOffset,
                inputBuffer.capacity() / PCM_BYTES_PER_SAMPLE
            )
            check(samplesToQueue > 0) { "AAC encoder returned an empty input buffer" }
            inputBuffer.asShortBuffer().put(pcm, sourceOffset, samplesToQueue)
            val presentationTimeUs = samplesToPresentationTimeUs(totalQueued)
            encoder.queueInputBuffer(
                inputIndex,
                0,
                samplesToQueue * PCM_BYTES_PER_SAMPLE,
                presentationTimeUs,
                0
            )
            sourceOffset += samplesToQueue
            totalQueued += samplesToQueue

            drainEncoder(
                encoder = encoder,
                muxer = muxer,
                startedAtMs = startedAtMs,
                waitForEndOfStream = false
            )
        }

        return totalQueued
    }

    private fun queueEndOfStream(
        encoder: MediaCodec,
        queuedSampleCount: Long,
        muxer: MinuteAudioMuxer,
        startedAtMs: Long
    ) {
        var inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        while (inputIndex < 0) {
            drainEncoder(
                encoder = encoder,
                muxer = muxer,
                startedAtMs = startedAtMs,
                waitForEndOfStream = false
            )
            inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        }
        encoder.queueInputBuffer(
            inputIndex,
            0,
            0,
            samplesToPresentationTimeUs(queuedSampleCount),
            MediaCodec.BUFFER_FLAG_END_OF_STREAM
        )
        drainEncoder(
            encoder = encoder,
            muxer = muxer,
            startedAtMs = startedAtMs,
            waitForEndOfStream = true
        )
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MinuteAudioMuxer,
        startedAtMs: Long,
        waitForEndOfStream: Boolean
    ) {
        val info = MediaCodec.BufferInfo()
        var emptyPollCount = 0

        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(
                info,
                if (waitForEndOfStream) CODEC_TIMEOUT_US else 0L
            )
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEndOfStream || emptyPollCount++ >= MAX_EOS_EMPTY_POLLS) {
                        return
                    }
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxer.setOutputFormat(encoder.outputFormat)
                }

                outputIndex >= 0 -> {
                    val outputBuffer = checkNotNull(encoder.getOutputBuffer(outputIndex))
                    val codecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!codecConfig && info.size > 0) {
                        muxer.writeSample(
                            encodedData = outputBuffer,
                            bufferInfo = info,
                            sampleWallClockMs = startedAtMs + (info.presentationTimeUs / 1_000L)
                        )
                    }
                    val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (endOfStream) return
                    emptyPollCount = 0
                }
            }
        }
    }

    private fun samplesToPresentationTimeUs(sampleCount: Long): Long {
        return sampleCount * MICROS_PER_SECOND / CollectAudioConfig.SAMPLING_RATE
    }

    private fun peakAmplitude(samples: ShortArray, sampleCount: Int): Int {
        var peak = 0
        for (index in 0 until sampleCount) {
            peak = max(peak, abs(samples[index].toInt()))
        }
        return peak
    }

    private class MinuteAudioMuxer(
        private val paths: CollectStoragePaths
    ) : AutoCloseable {
        private var outputFormat: MediaFormat? = null
        private var muxer: MediaMuxer? = null
        private var trackIndex: Int = -1
        private var currentOutputFile: File? = null
        private var currentOutputStartMs: Long? = null
        private var firstPresentationTimeUs: Long = 0L

        fun setOutputFormat(format: MediaFormat) {
            check(outputFormat == null) { "AAC encoder output format changed twice" }
            outputFormat = format
        }

        fun writeSample(
            encodedData: java.nio.ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
            sampleWallClockMs: Long
        ) {
            val minuteStartMs = CollectFileNaming.minuteFloor(sampleWallClockMs)
            if (minuteStartMs != currentOutputStartMs) {
                closeCurrent()
                open(minuteStartMs, bufferInfo.presentationTimeUs)
            }

            val adjustedInfo = MediaCodec.BufferInfo().apply {
                set(
                    bufferInfo.offset,
                    bufferInfo.size,
                    (bufferInfo.presentationTimeUs - firstPresentationTimeUs).coerceAtLeast(0L),
                    bufferInfo.flags
                )
            }
            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)
            checkNotNull(muxer).writeSampleData(trackIndex, encodedData, adjustedInfo)
        }

        override fun close() {
            closeCurrent()
        }

        private fun open(startMs: Long, firstSamplePresentationTimeUs: Long) {
            val format = checkNotNull(outputFormat) {
                "AAC output format is unavailable before first sample"
            }
            val file = File(
                paths.collectDir,
                CollectFileNaming.collectAudioFileName(startMs)
            )
            val nextMuxer = MediaMuxer(
                file.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val nextTrackIndex = nextMuxer.addTrack(format)
            nextMuxer.start()

            muxer = nextMuxer
            trackIndex = nextTrackIndex
            currentOutputFile = file
            currentOutputStartMs = startMs
            firstPresentationTimeUs = firstSamplePresentationTimeUs
        }

        private fun closeCurrent() {
            val currentMuxer = muxer
            val output = currentOutputFile
            val startMs = currentOutputStartMs

            if (currentMuxer != null) {
                runCatching { currentMuxer.stop() }
                    .onFailure { Log.w(TAG, "audio muxer stop failed", it) }
                runCatching { currentMuxer.release() }
            }

            if (output != null && startMs != null) {
                if (output.exists() && output.length() > 0L) {
                    Log.d(
                        TAG,
                        "closed collect audio file=${output.name} size=${output.length()} startMs=$startMs"
                    )
                    CollectClosedFileBus.publish(
                        CollectClosedFileMeta(
                            type = CollectFileType.AUDIO,
                            file = output,
                            startMs = startMs,
                            closedAtMs = System.currentTimeMillis()
                        )
                    )
                } else {
                    Log.w(
                        TAG,
                        "collect audio file not published file=${output.name} " +
                            "exists=${output.exists()} size=${output.length()}"
                    )
                }
            }

            muxer = null
            trackIndex = -1
            currentOutputFile = null
            currentOutputStartMs = null
            firstPresentationTimeUs = 0L
        }
    }

    private companion object {
        const val TAG = "CollectAudioSource"
        const val CAPTURE_THREAD_NAME = "collect-audio-capture"
        const val CHANNEL_COUNT = 1
        const val PCM_BYTES_PER_SAMPLE = 2
        const val READ_SAMPLE_COUNT = 2_048
        const val AUDIO_RECORD_BUFFER_BYTES = 16_384
        const val ENCODER_MAX_INPUT_BYTES = 16_384
        const val CODEC_TIMEOUT_US = 10_000L
        const val MICROS_PER_SECOND = 1_000_000L
        const val MAX_EOS_EMPTY_POLLS = 100
        const val STOP_JOIN_TIMEOUT_MS = 5_000L
    }
}
