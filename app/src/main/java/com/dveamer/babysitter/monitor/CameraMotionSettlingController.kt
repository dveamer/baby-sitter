package com.dveamer.babysitter.monitor

internal data class CameraMotionObservation(
    val active: Boolean,
    val changedRatio: Double,
    val activeTileCount: Int
)

internal data class CameraMotionDecision(
    val active: Boolean,
    val resetAwakeAccumulation: Boolean = false,
    val settling: Boolean = false
)

/**
 * Treats broad scene changes as a caregiver/camera transition and waits for the
 * image to settle before camera motion can contribute to awake detection again.
 */
internal class CameraMotionSettlingController(
    private val quietPeriodMs: Long = DEFAULT_QUIET_PERIOD_MS,
    private val maxSettlingMs: Long = DEFAULT_MAX_SETTLING_MS,
    private val largeMotionRearmCooldownMs: Long = DEFAULT_LARGE_MOTION_REARM_COOLDOWN_MS
) {
    private val recentBroadLargeMotion = ArrayDeque<Boolean>()

    private var settlingStartedAtMs: Long? = null
    private var quietStartedAtMs: Long? = null
    private var largeMotionSuppressedUntilMs: Long = 0L

    fun onObservation(
        observation: CameraMotionObservation,
        nowMs: Long
    ): CameraMotionDecision {
        val settlingStartedAt = settlingStartedAtMs
        if (settlingStartedAt != null) {
            return onSettlingObservation(observation, nowMs, settlingStartedAt)
        }

        if (nowMs >= largeMotionSuppressedUntilMs && isLargeSceneMotion(observation)) {
            settlingStartedAtMs = nowMs
            quietStartedAtMs = null
            recentBroadLargeMotion.clear()
            return CameraMotionDecision(
                active = false,
                resetAwakeAccumulation = true,
                settling = true
            )
        }

        return CameraMotionDecision(active = observation.active)
    }

    private fun onSettlingObservation(
        observation: CameraMotionObservation,
        nowMs: Long,
        settlingStartedAtMs: Long
    ): CameraMotionDecision {
        if (observation.active) {
            quietStartedAtMs = null
        } else if (quietStartedAtMs == null) {
            quietStartedAtMs = nowMs
        }

        val quietStartedAt = quietStartedAtMs
        if (quietStartedAt != null && nowMs - quietStartedAt >= quietPeriodMs) {
            leaveSettling()
            return CameraMotionDecision(active = false)
        }

        if (nowMs - settlingStartedAtMs >= maxSettlingMs) {
            leaveSettling()
            largeMotionSuppressedUntilMs = nowMs + largeMotionRearmCooldownMs
            return CameraMotionDecision(active = observation.active)
        }

        return CameraMotionDecision(active = false, settling = true)
    }

    private fun isLargeSceneMotion(observation: CameraMotionObservation): Boolean {
        val veryLarge = observation.changedRatio >= VERY_LARGE_CHANGED_RATIO
        val broadLarge = observation.changedRatio >= BROAD_LARGE_CHANGED_RATIO &&
            observation.activeTileCount >= BROAD_LARGE_MIN_ACTIVE_TILES

        recentBroadLargeMotion.addLast(broadLarge)
        while (recentBroadLargeMotion.size > BROAD_LARGE_HISTORY_SIZE) {
            recentBroadLargeMotion.removeFirst()
        }

        return veryLarge || recentBroadLargeMotion.count { it } >= BROAD_LARGE_REQUIRED_SAMPLES
    }

    private fun leaveSettling() {
        settlingStartedAtMs = null
        quietStartedAtMs = null
        recentBroadLargeMotion.clear()
    }

    private companion object {
        const val VERY_LARGE_CHANGED_RATIO = 0.30
        const val BROAD_LARGE_CHANGED_RATIO = 0.15
        const val BROAD_LARGE_MIN_ACTIVE_TILES = 6
        const val BROAD_LARGE_HISTORY_SIZE = 3
        const val BROAD_LARGE_REQUIRED_SAMPLES = 2

        const val DEFAULT_QUIET_PERIOD_MS = 10_000L
        const val DEFAULT_MAX_SETTLING_MS = 30_000L
        const val DEFAULT_LARGE_MOTION_REARM_COOLDOWN_MS = 45_000L
    }
}
