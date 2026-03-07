package com.dveamer.babysitter.monitor

import com.dveamer.babysitter.collect.CollectAudioBus
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
import kotlin.math.max

class MicrophoneMonitor(
    private val scope: CoroutineScope,
    private val amplitudeThreshold: Double = AMPLITUDE_THRESHOLD_DEFAULT,
    override val id: String = "microphone"
) : Monitor {

    private val mutableSignals = MutableSharedFlow<MonitorSignal>(extraBufferCapacity = 16)
    override val signals: Flow<MonitorSignal> = mutableSignals.asSharedFlow()

    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return

        job = scope.launch(Dispatchers.Default) {
            var noiseFloor = 0.0
            var activeStreak = 0
            var inactiveStreak = 0
            var currentActive = false
            var pollCount = 0L

            while (isActive) {
                val snapshot = CollectAudioBus.latest()
                val amplitude = snapshot?.averageAmplitude ?: 0.0
                val fresh = snapshot != null && (System.currentTimeMillis() - snapshot.capturedAtMs) <= STALE_TIMEOUT_MS
                var dynamicThreshold = amplitudeThreshold
                var rawActive = false

                if (!fresh) {
                    activeStreak = 0
                    inactiveStreak += 1
                } else {
                    noiseFloor = updateNoiseFloor(noiseFloor, amplitude)
                    dynamicThreshold = max(
                        amplitudeThreshold,
                        max(noiseFloor * NOISE_MULTIPLIER, noiseFloor + NOISE_OFFSET)
                    )
                    rawActive = amplitude >= dynamicThreshold

                    if (rawActive) {
                        activeStreak += 1
                        inactiveStreak = 0
                    } else {
                        inactiveStreak += 1
                        activeStreak = 0
                    }
                }

                currentActive = when {
                    currentActive && inactiveStreak >= INACTIVE_HOLD_POLLS -> false
                    !currentActive && activeStreak >= ACTIVE_HOLD_POLLS -> true
                    else -> currentActive
                }

                pollCount += 1
                if (pollCount % LOG_EVERY_N_POLLS == 0L) {
                    Log.d(
                        TAG,
                        "mic level amplitude=${amplitude.toInt()} noiseFloor=${noiseFloor.toInt()} threshold=${dynamicThreshold.toInt()} rawActive=$rawActive active=$currentActive fresh=$fresh streakA=$activeStreak streakI=$inactiveStreak"
                    )
                }

                mutableSignals.tryEmit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.MICROPHONE,
                        active = currentActive
                    )
                )

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
    }

    private fun updateNoiseFloor(previous: Double, amplitude: Double): Double {
        if (previous <= 0.0) return amplitude
        val alpha = if (amplitude <= previous * 1.2) 0.25 else 0.04
        return (previous * (1.0 - alpha)) + (amplitude * alpha)
    }

    private companion object {
        const val TAG = "MicrophoneMonitor"
        const val AMPLITUDE_THRESHOLD_DEFAULT = 900.0
        const val POLL_INTERVAL_MS = 1_000L
        const val STALE_TIMEOUT_MS = 2_000L
        const val ACTIVE_HOLD_POLLS = 2
        const val INACTIVE_HOLD_POLLS = 3
        const val NOISE_MULTIPLIER = 2.0
        const val NOISE_OFFSET = 140.0
        const val LOG_EVERY_N_POLLS = 1
    }
}
