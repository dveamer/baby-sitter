package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.monitor.MonitorKind

internal data class AwakeConfirmationResult(
    val awake: AwakeState,
    val tentative: Boolean
)

/**
 * Adds a confirmation stage for camera-only awake results. Sound and other
 * monitor kinds still have to pass AwakeDetector, but are confirmed without the
 * extra camera-only delay.
 */
internal class CameraAwakeConfirmationController(
    private val cameraConfirmationDelayMs: Long = DEFAULT_CAMERA_CONFIRMATION_DELAY_MS,
    private val tentativeCancelDelayMs: Long = DEFAULT_TENTATIVE_CANCEL_DELAY_MS
) {
    private var tentativeAwakeSinceMs: Long? = null
    private var tentativeStartedAtMs: Long? = null
    private var tentativeInactiveSinceMs: Long? = null
    private var confirmedAwakeSinceMs: Long? = null

    fun onAwakeState(state: AwakeState, nowMs: Long): AwakeConfirmationResult {
        if (!state.isAwake || state.awakeSinceMs == null) {
            return onInactiveState(nowMs)
        }

        if (confirmedAwakeSinceMs != null) {
            return AwakeConfirmationResult(
                awake = state.copy(awakeSinceMs = confirmedAwakeSinceMs),
                tentative = false
            )
        }

        val cameraOnly = state.triggeredKinds == setOf(MonitorKind.CAMERA)
        if (!cameraOnly) {
            val confirmedSince = minOf(
                state.awakeSinceMs,
                tentativeAwakeSinceMs ?: state.awakeSinceMs
            )
            confirmedAwakeSinceMs = confirmedSince
            clearTentative()
            return AwakeConfirmationResult(
                awake = state.copy(awakeSinceMs = confirmedSince),
                tentative = false
            )
        }

        val candidateChanged = tentativeAwakeSinceMs != state.awakeSinceMs
        if (candidateChanged) {
            tentativeAwakeSinceMs = state.awakeSinceMs
            tentativeStartedAtMs = nowMs
        }
        tentativeInactiveSinceMs = null

        val tentativeStartedAt = tentativeStartedAtMs ?: nowMs.also {
            tentativeStartedAtMs = it
        }
        if (nowMs - tentativeStartedAt >= cameraConfirmationDelayMs) {
            val confirmedSince = tentativeAwakeSinceMs ?: state.awakeSinceMs
            confirmedAwakeSinceMs = confirmedSince
            clearTentative()
            return AwakeConfirmationResult(
                awake = state.copy(awakeSinceMs = confirmedSince),
                tentative = false
            )
        }

        return AwakeConfirmationResult(
            awake = AwakeState(isAwake = false),
            tentative = true
        )
    }

    private fun onInactiveState(nowMs: Long): AwakeConfirmationResult {
        if (confirmedAwakeSinceMs != null) {
            clearAll()
            return AwakeConfirmationResult(
                awake = AwakeState(isAwake = false),
                tentative = false
            )
        }

        if (tentativeAwakeSinceMs != null) {
            val inactiveSince = tentativeInactiveSinceMs ?: nowMs.also {
                tentativeInactiveSinceMs = it
            }
            if (nowMs - inactiveSince >= tentativeCancelDelayMs) {
                clearTentative()
            }
        }

        return AwakeConfirmationResult(
            awake = AwakeState(isAwake = false),
            tentative = tentativeAwakeSinceMs != null
        )
    }

    private fun clearTentative() {
        tentativeAwakeSinceMs = null
        tentativeStartedAtMs = null
        tentativeInactiveSinceMs = null
    }

    private fun clearAll() {
        confirmedAwakeSinceMs = null
        clearTentative()
    }

    private companion object {
        const val DEFAULT_CAMERA_CONFIRMATION_DELAY_MS = 15_000L
        const val DEFAULT_TENTATIVE_CANCEL_DELAY_MS = 10_000L
    }
}
