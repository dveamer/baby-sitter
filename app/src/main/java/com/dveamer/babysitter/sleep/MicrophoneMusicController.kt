package com.dveamer.babysitter.sleep

enum class MicrophoneMusicAction {
    NONE,
    START
}

class MicrophoneMusicController {
    private var activeSinceMs: Long? = null
    private var lastActive = false
    private var startedInCurrentActivePeriod = false

    fun onSignal(
        active: Boolean,
        nowMs: Long,
        requiredActiveDurationMs: Long
    ): MicrophoneMusicAction {
        if (!active) {
            lastActive = false
            activeSinceMs = null
            startedInCurrentActivePeriod = false
            return MicrophoneMusicAction.NONE
        }

        if (!lastActive) {
            activeSinceMs = nowMs
            startedInCurrentActivePeriod = false
        }
        lastActive = true

        val since = activeSinceMs ?: nowMs.also { activeSinceMs = it }
        val action = if (
            !startedInCurrentActivePeriod &&
            nowMs - since >= requiredActiveDurationMs
        ) {
            startedInCurrentActivePeriod = true
            MicrophoneMusicAction.START
        } else {
            MicrophoneMusicAction.NONE
        }
        lastActive = active
        return action
    }

    fun reset() {
        activeSinceMs = null
        lastActive = false
        startedInCurrentActivePeriod = false
    }
}
