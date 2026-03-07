package com.dveamer.babysitter.sleep

data class WakeMemoryState(
    val awakeStartedAt: Long? = null,
    val lastAwakeSignalAt: Long? = null,
    val sleepStableStartedAt: Long? = null,
    val memoryBuildInProgress: Boolean = false
)

data class WakeMemoryTrigger(
    val awakeStartedAt: Long,
    val sleepStableEndedAt: Long
)

class WakeMemoryManager(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var state = WakeMemoryState()

    fun snapshot(): WakeMemoryState = state

    fun onAwakeSignal(nowMs: Long = clock()): WakeMemoryTrigger? {
        val startedAt = state.awakeStartedAt ?: nowMs
        state = state.copy(
            awakeStartedAt = startedAt,
            lastAwakeSignalAt = nowMs,
            sleepStableStartedAt = null
        )
        return null
    }

    fun onPassiveSignal(
        lullabyActive: Boolean,
        nowMs: Long = clock()
    ): WakeMemoryTrigger? {
        if (state.memoryBuildInProgress) return null
        if (state.awakeStartedAt == null || state.lastAwakeSignalAt == null) return null
        if (lullabyActive) {
            state = state.copy(sleepStableStartedAt = null)
            return null
        }

        val stableStart = state.sleepStableStartedAt ?: nowMs
        state = state.copy(sleepStableStartedAt = stableStart)

        if (nowMs - stableStart < SLEEP_STABLE_REQUIRED_MS) {
            return null
        }

        val trigger = WakeMemoryTrigger(
            awakeStartedAt = state.awakeStartedAt!!,
            sleepStableEndedAt = nowMs
        )

        state = WakeMemoryState(memoryBuildInProgress = true)
        return trigger
    }

    fun markMemoryBuildFinished() {
        state = state.copy(memoryBuildInProgress = false)
    }

    fun isAwakeSessionActive(): Boolean {
        return state.awakeStartedAt != null || state.memoryBuildInProgress
    }

    companion object {
        const val SLEEP_STABLE_REQUIRED_MS = 3 * 60 * 1000L
        const val PRE_ROLL_MS = 3 * 60 * 1000L
    }
}
