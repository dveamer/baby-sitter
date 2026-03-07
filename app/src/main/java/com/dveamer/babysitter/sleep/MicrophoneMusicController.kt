package com.dveamer.babysitter.sleep

enum class MicrophoneMusicAction {
    NONE,
    START,
    STOP
}

class MicrophoneMusicController {
    private var microphoneOwnedPlayback = false

    fun onSignal(
        active: Boolean,
        lullabyActive: Boolean
    ): MicrophoneMusicAction {
        if (!active && !lullabyActive) {
            microphoneOwnedPlayback = false
            return MicrophoneMusicAction.NONE
        }

        if (active && !lullabyActive && !microphoneOwnedPlayback) {
            microphoneOwnedPlayback = true
            return MicrophoneMusicAction.START
        }

        if (!active && lullabyActive && microphoneOwnedPlayback) {
            microphoneOwnedPlayback = false
            return MicrophoneMusicAction.STOP
        }

        return MicrophoneMusicAction.NONE
    }

    fun reset() {
        microphoneOwnedPlayback = false
    }
}
