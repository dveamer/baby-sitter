package com.dveamer.babysitter.sleep

enum class PlaybackInactivityAction {
    NONE,
    REQUEST_STOP_AFTER_CURRENT_TRACK,
    CLEAR_STOP_REQUEST
}

class PlaybackInactivityController(
    private val inactivityGraceMs: Long
) {
    private var inactiveSinceMs: Long? = null
    private var stopRequested = false

    fun onConditionChanged(
        shouldContinue: Boolean,
        nowMs: Long
    ): PlaybackInactivityAction {
        if (shouldContinue) {
            inactiveSinceMs = null
            return if (stopRequested) {
                stopRequested = false
                PlaybackInactivityAction.CLEAR_STOP_REQUEST
            } else {
                PlaybackInactivityAction.NONE
            }
        }

        val inactiveSince = inactiveSinceMs ?: nowMs.also { inactiveSinceMs = it }
        return if (!stopRequested && nowMs - inactiveSince >= inactivityGraceMs) {
            stopRequested = true
            PlaybackInactivityAction.REQUEST_STOP_AFTER_CURRENT_TRACK
        } else {
            PlaybackInactivityAction.NONE
        }
    }

    fun reset() {
        inactiveSinceMs = null
        stopRequested = false
    }
}
