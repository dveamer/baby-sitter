package com.dveamer.babysitter.sleep

enum class MicrophoneMusicAction {
    NONE,
    START
}

class MicrophoneMusicController {
    private var lastActive = false

    fun onSignal(active: Boolean): MicrophoneMusicAction {
        val action = if (active && !lastActive) {
            MicrophoneMusicAction.START
        } else {
            MicrophoneMusicAction.NONE
        }
        lastActive = active
        return action
    }

    fun reset() {
        lastActive = false
    }
}
