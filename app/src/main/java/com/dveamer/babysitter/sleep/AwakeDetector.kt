package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.monitor.MonitorKind
import com.dveamer.babysitter.monitor.MonitorSignal
import com.dveamer.babysitter.settings.SettingsState

interface AwakeDetector {
    fun onSignal(signal: MonitorSignal, nowMs: Long = System.currentTimeMillis()): AwakeState
}

data class AwakeState(
    val isAwake: Boolean,
    val awakeSinceMs: Long? = null,
    val reason: String = ""
)

class ContinuousAwakeDetector(
    private val settingsProvider: () -> SettingsState
) : AwakeDetector {

    companion object {
        private const val REQUIRED_ACTIVE_DURATION_MS = 20_000L
        private const val INACTIVE_RESET_MS = 3_000L
        private const val ACTIVE_SINCE_TTL_MS = 10 * 60 * 1_000L
    }

    private val activeSince = mutableMapOf<String, Long>()
    private val kindById = mutableMapOf<String, MonitorKind>()
    private val lastActiveTrueAt = mutableMapOf<String, Long>()

    override fun onSignal(signal: MonitorSignal, nowMs: Long): AwakeState {
        kindById[signal.monitorId] = signal.kind

        if (signal.active) {
            activeSince.putIfAbsent(signal.monitorId, signal.timestampMs)
            lastActiveTrueAt[signal.monitorId] = signal.timestampMs
        } else {
            val lastTrueAt = lastActiveTrueAt[signal.monitorId]
            if (lastTrueAt != null && signal.timestampMs - lastTrueAt >= INACTIVE_RESET_MS) {
                activeSince.remove(signal.monitorId)
                lastActiveTrueAt.remove(signal.monitorId)
            }
        }

        val allMonitorIds = kindById.keys.toSet()
        allMonitorIds.forEach { id ->
            val lastTrueAt = lastActiveTrueAt[id]
            if (lastTrueAt != null && nowMs - lastTrueAt >= ACTIVE_SINCE_TTL_MS) {
                activeSince.remove(id)
                lastActiveTrueAt.remove(id)
            }
        }

        // Keep settings access for compatibility with current constructor contract.
        settingsProvider()
        val triggered = allMonitorIds.mapNotNull { id ->
            val since = activeSince[id] ?: return@mapNotNull null
            val lastTrueAt = lastActiveTrueAt[id] ?: return@mapNotNull null
            val gapMs = nowMs - lastTrueAt
            val activeDurationMs = nowMs - since
            if (gapMs <= INACTIVE_RESET_MS && activeDurationMs >= REQUIRED_ACTIVE_DURATION_MS) {
                id to since
            } else {
                null
            }
        }
        if (triggered.isEmpty()) {
            return AwakeState(isAwake = false)
        }

        val awakeSinceMs = triggered.minOf { it.second }
        val reason = triggered.joinToString(",") { it.first }
        return AwakeState(isAwake = true, awakeSinceMs = awakeSinceMs, reason = reason)
    }
}
