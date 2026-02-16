package com.dveamer.babysitter.monitor

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

    class MicrophoneMonitor(
    private val scope: CoroutineScope,
    override val id: String = "microphone"
) : Monitor {

    private val mutableSignals = MutableSharedFlow<MonitorSignal>(extraBufferCapacity = 16)
    override val signals: Flow<MonitorSignal> = mutableSignals.asSharedFlow()

    private var job: Job? = null
    private var recorder: AudioRecord? = null

    override suspend fun start() {
        if (job != null) return

        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else 4096

        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).also { it.startRecording() }
        } catch (se: SecurityException) {
            Log.w(TAG, "microphone permission denied", se)
            return
        } catch (t: Throwable) {
            Log.w(TAG, "microphone start failed", t)
            return
        }
        recorder = audio

        job = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (isActive) {
                val read = runCatching {
                    audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                }.getOrElse {
                    Log.w(TAG, "microphone read failed", it)
                    -1
                }

                if (read <= 0) {
                    delay(500)
                    continue
                }

                val avgAmplitude = buffer
                    .take(read)
                    .map { abs(it.toInt()) }
                    .average()

                val active = avgAmplitude > AMPLITUDE_THRESHOLD
                mutableSignals.tryEmit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.MICROPHONE,
                        active = active
                    )
                )

                delay(1_000)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        recorder?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        recorder = null
    }

    private companion object {
        const val TAG = "MicrophoneMonitor"
        const val AMPLITUDE_THRESHOLD = 900.0
    }
}
