package com.dveamer.babysitter.sleep

enum class MicrophoneMusicAction {
    NONE,
    START,
    STOP
}

class MicrophoneMusicController {
    private var microphoneOwnedPlayback = false
    private var lastActive = false
    private var lastLullabyActive = false

    fun onSignal(
        active: Boolean,
        lullabyActive: Boolean
    ): MicrophoneMusicAction {
        val action = when {
            !active && !lullabyActive -> {
                microphoneOwnedPlayback = false
                MicrophoneMusicAction.NONE
            }

            active && !lullabyActive && (!lastActive || lastLullabyActive || !microphoneOwnedPlayback) -> {
                microphoneOwnedPlayback = true
                MicrophoneMusicAction.START
            }

            !active && lullabyActive && microphoneOwnedPlayback -> {
                microphoneOwnedPlayback = false
                MicrophoneMusicAction.STOP
            }

            else -> MicrophoneMusicAction.NONE
        }

        lastActive = active
        lastLullabyActive = lullabyActive
        return action
    }

    fun reset() {
        microphoneOwnedPlayback = false
        lastActive = false
        lastLullabyActive = false
    }
}
