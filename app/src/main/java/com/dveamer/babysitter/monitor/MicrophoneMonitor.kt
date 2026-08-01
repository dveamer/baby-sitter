package com.dveamer.babysitter.monitor

import android.util.Log
import com.dveamer.babysitter.collect.CollectAudioBus
import com.dveamer.babysitter.collect.CollectAudioConfig
import com.dveamer.babysitter.collect.CollectCryBus
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
    private val yamNetCryConfidenceThreshold: Float = YAMNET_CONFIDENCE_THRESHOLD_DEFAULT,
    override val id: String = "microphone"
) : Monitor {

    private val mutableSignals = MutableSharedFlow<MonitorSignal>(extraBufferCapacity = 16)
    override val signals: Flow<MonitorSignal> = mutableSignals.asSharedFlow()

    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return

        job = scope.launch(Dispatchers.Default) {
            val activityTracker = CryActivityTracker()
            var noiseFloor = 0.0
            var pollCount = 0L

            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val amplitudeSnapshot = CollectAudioBus.latest()
                val amplitude = amplitudeSnapshot?.averageAmplitude ?: 0.0
                val amplitudeFresh = amplitudeSnapshot != null &&
                    (nowMs - amplitudeSnapshot.capturedAtMs) <= AMPLITUDE_STALE_TIMEOUT_MS
                var dynamicAmplitudeThreshold = amplitudeThreshold
                var amplitudeActive = false

                if (amplitudeFresh) {
                    noiseFloor = updateNoiseFloor(noiseFloor, amplitude)
                    dynamicAmplitudeThreshold = max(
                        amplitudeThreshold,
                        max(noiseFloor * NOISE_MULTIPLIER, noiseFloor + NOISE_OFFSET)
                    )
                    amplitudeActive = amplitude >= dynamicAmplitudeThreshold
                }

                val yamNetSnapshot = CollectCryBus.latest()
                val yamNetScore = yamNetSnapshot?.score ?: 0.0f
                val yamNetFresh = yamNetSnapshot != null &&
                    (nowMs - yamNetSnapshot.capturedAtMs) <= YAMNET_STALE_TIMEOUT_MS
                val yamNetActive = yamNetFresh &&
                    yamNetScore >= yamNetCryConfidenceThreshold
                val rawActive = passesCombinedCryGate(
                    amplitudeFresh = amplitudeFresh,
                    amplitudeActive = amplitudeActive,
                    yamNetFresh = yamNetFresh,
                    yamNetActive = yamNetActive
                )
                val transition = activityTracker.update(rawActive)

                pollCount += 1
                if (shouldLogLevel(
                        pollCount = pollCount,
                        activeChanged = transition.activeChanged
                    )
                ) {
                    Log.d(
                        TAG,
                        "mic level amplitude=${amplitude.toInt()} " +
                            "noiseFloor=${noiseFloor.toInt()} " +
                            "threshold=${dynamicAmplitudeThreshold.toInt()} " +
                            "amplitudeActive=$amplitudeActive " +
                            "yamNetScore=$yamNetScore " +
                            "yamNetThreshold=$yamNetCryConfidenceThreshold " +
                            "yamNetActive=$yamNetActive rawActive=$rawActive " +
                            "active=${transition.active} " +
                            "freshAmplitude=$amplitudeFresh freshYamNet=$yamNetFresh " +
                            "evidenceA=${transition.activeEvidenceCount}/${transition.evidenceWindowSize} " +
                            "streakI=${transition.inactiveStreak}"
                    )
                }

                mutableSignals.tryEmit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.MICROPHONE,
                        active = transition.active
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
        val alpha = when {
            amplitude <= previous -> NOISE_FALL_ALPHA
            amplitude <= previous * MODEST_RISE_MULTIPLIER -> NOISE_MODEST_RISE_ALPHA
            else -> NOISE_SPIKE_RISE_ALPHA
        }
        return (previous * (1.0 - alpha)) + (amplitude * alpha)
    }

    private fun shouldLogLevel(pollCount: Long, activeChanged: Boolean): Boolean {
        return activeChanged || pollCount % LOG_EVERY_N_POLLS == 0L
    }

    private companion object {
        const val TAG = "MicrophoneMonitor"
        const val AMPLITUDE_THRESHOLD_DEFAULT = 900.0
        const val YAMNET_CONFIDENCE_THRESHOLD_DEFAULT = 0.5f
        const val POLL_INTERVAL_MS = 1_000L
        const val AMPLITUDE_STALE_TIMEOUT_MS = CollectAudioConfig.AMPLITUDE_STALE_TIMEOUT_MS
        const val YAMNET_STALE_TIMEOUT_MS = CollectAudioConfig.CRY_RESULT_STALE_TIMEOUT_MS
        const val ACTIVE_EVIDENCE_WINDOW_POLLS = 4
        const val ACTIVE_REQUIRED_POLLS = 2
        const val INACTIVE_HOLD_POLLS = 3
        const val NOISE_MULTIPLIER = 2.0
        const val NOISE_OFFSET = 140.0
        const val LOG_EVERY_N_POLLS = 30
        const val MODEST_RISE_MULTIPLIER = 1.2
        const val NOISE_FALL_ALPHA = 0.25
        const val NOISE_MODEST_RISE_ALPHA = 0.08
        const val NOISE_SPIKE_RISE_ALPHA = 0.01
    }

    internal class CryActivityTracker {
        private val recentRawActivity = ArrayDeque<Boolean>(ACTIVE_EVIDENCE_WINDOW_POLLS)
        private var inactiveStreak = 0
        private var currentActive = false

        fun update(rawActive: Boolean): CryActivityTransition {
            recentRawActivity.addLast(rawActive)
            if (recentRawActivity.size > ACTIVE_EVIDENCE_WINDOW_POLLS) {
                recentRawActivity.removeFirst()
            }

            if (rawActive) {
                inactiveStreak = 0
            } else {
                inactiveStreak += 1
            }

            val activeEvidenceCount = recentRawActivity.count { it }
            val previousActive = currentActive
            currentActive = when {
                currentActive && inactiveStreak >= INACTIVE_HOLD_POLLS -> false
                !currentActive && activeEvidenceCount >= ACTIVE_REQUIRED_POLLS -> true
                else -> currentActive
            }

            return CryActivityTransition(
                active = currentActive,
                activeChanged = previousActive != currentActive,
                activeEvidenceCount = activeEvidenceCount,
                evidenceWindowSize = recentRawActivity.size,
                inactiveStreak = inactiveStreak
            )
        }
    }

    internal data class CryActivityTransition(
        val active: Boolean,
        val activeChanged: Boolean,
        val activeEvidenceCount: Int,
        val evidenceWindowSize: Int,
        val inactiveStreak: Int
    )
}

internal fun passesCombinedCryGate(
    amplitudeFresh: Boolean,
    amplitudeActive: Boolean,
    yamNetFresh: Boolean,
    yamNetActive: Boolean
): Boolean {
    return amplitudeFresh && amplitudeActive && yamNetFresh && yamNetActive
}
