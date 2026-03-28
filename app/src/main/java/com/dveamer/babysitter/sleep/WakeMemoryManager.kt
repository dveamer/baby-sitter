package com.dveamer.babysitter.sleep

data class WakeMemoryState(
    val awakeStartedAt: Long? = null,
    val lastAwakeSignalAt: Long? = null,
    val sleepStableStartedAt: Long? = null,
    val finalizationTargetEndMs: Long? = null,
    val lastBuiltRangeEndMs: Long? = null,
    val lastBuildRequestedAtMs: Long? = null,
    val memoryBuildInProgress: Boolean = false
)

data class WakeMemoryTrigger(
    val awakeStartedAt: Long,
    val requestedRangeEndMs: Long
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
            sleepStableStartedAt = null,
            finalizationTargetEndMs = null
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

        state = state.copy(finalizationTargetEndMs = nowMs)
        return issueBuildTrigger(requestedRangeEndMs = nowMs)
    }

    fun onPeriodicCheck(
        latestClosedVideoEndMs: Long?,
        nowMs: Long = clock()
    ): WakeMemoryTrigger? {
        val awakeStartedAt = state.awakeStartedAt ?: return null
        if (state.lastAwakeSignalAt == null || state.memoryBuildInProgress) return null

        val latestClosedEndMs = latestClosedVideoEndMs ?: return null
        if (latestClosedEndMs < awakeStartedAt - PRE_ROLL_MS) return null

        val lastBuiltRangeEndMs = state.lastBuiltRangeEndMs
        if (lastBuiltRangeEndMs != null && latestClosedEndMs <= lastBuiltRangeEndMs) {
            return null
        }

        val lastRequestedAtMs = state.lastBuildRequestedAtMs
        if (lastRequestedAtMs != null && nowMs - lastRequestedAtMs < PERIODIC_BUILD_INTERVAL_MS) {
            return null
        }

        return issueBuildTrigger(requestedRangeEndMs = nowMs)
    }

    fun onForceBuildCheck(
        latestClosedVideoEndMs: Long?,
        nowMs: Long = clock()
    ): WakeMemoryTrigger? {
        val awakeStartedAt = state.awakeStartedAt ?: return null
        if (state.lastAwakeSignalAt == null || state.memoryBuildInProgress) return null

        val latestClosedEndMs = latestClosedVideoEndMs ?: return null
        if (latestClosedEndMs < awakeStartedAt - PRE_ROLL_MS) return null

        val lastBuiltRangeEndMs = state.lastBuiltRangeEndMs
        if (lastBuiltRangeEndMs != null && latestClosedEndMs <= lastBuiltRangeEndMs) {
            return null
        }

        return issueBuildTrigger(requestedRangeEndMs = nowMs)
    }

    fun markMemoryBuildFinished(
        result: CoordinatedMemoryBuildResult,
        clearSessionOnSuccessfulBuild: Boolean = false
    ) {
        val builtRangeEndMs = result.effectiveRangeEndMs?.takeIf { result.outputFile != null }
        val nextBuiltRangeEndMs = maxOfOrNull(state.lastBuiltRangeEndMs, builtRangeEndMs)
        val nextState = state.copy(
            lastBuiltRangeEndMs = nextBuiltRangeEndMs,
            memoryBuildInProgress = false
        )

        if (clearSessionOnSuccessfulBuild && result.outputFile != null) {
            state = WakeMemoryState()
            return
        }

        val finalizationTargetEndMs = nextState.finalizationTargetEndMs
        if (
            result.outputFile != null &&
            finalizationTargetEndMs != null &&
            nextBuiltRangeEndMs != null &&
            nextBuiltRangeEndMs >= finalizationTargetEndMs
        ) {
            state = WakeMemoryState()
            return
        }

        state = nextState
    }

    fun isAwakeSessionActive(): Boolean {
        return state.awakeStartedAt != null || state.memoryBuildInProgress
    }

    private fun issueBuildTrigger(requestedRangeEndMs: Long): WakeMemoryTrigger? {
        val awakeStartedAt = state.awakeStartedAt ?: return null
        if (state.lastAwakeSignalAt == null || state.memoryBuildInProgress) return null

        state = state.copy(
            memoryBuildInProgress = true,
            lastBuildRequestedAtMs = requestedRangeEndMs
        )
        return WakeMemoryTrigger(
            awakeStartedAt = awakeStartedAt,
            requestedRangeEndMs = requestedRangeEndMs
        )
    }

    private fun maxOfOrNull(first: Long?, second: Long?): Long? {
        return when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }
    }

    companion object {
        const val SLEEP_STABLE_REQUIRED_MS = 3 * 60 * 1000L
        const val PRE_ROLL_MS = 3 * 60 * 1000L
        const val PERIODIC_BUILD_INTERVAL_MS = 60 * 1000L
    }
}
