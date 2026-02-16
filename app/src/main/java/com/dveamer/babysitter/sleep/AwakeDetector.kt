package com.dveamer.babysitter.sleep

import android.util.Log
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
        private const val SIGNAL_WINDOW_MS = 5_000L
        private const val REQUIRED_CONSECUTIVE_WINDOWS = 3
        private const val ACTIVE_SINCE_TTL_MS = 10 * 60 * 1_000L
    }

    private val activeSince = mutableMapOf<String, Long>()
    private val kindById = mutableMapOf<String, MonitorKind>()
    private val lastActiveTrueAt = mutableMapOf<String, Long>()
    private val currentWindowStartMs = mutableMapOf<String, Long>()
    private val activeSeenInWindow = mutableMapOf<String, Boolean>()
    private val consecutiveActiveWindows = mutableMapOf<String, Int>()
    private val consecutiveSince = mutableMapOf<String, Long>()

    override fun onSignal(signal: MonitorSignal, nowMs: Long): AwakeState {
        kindById[signal.monitorId] = signal.kind

        ensureWindowInitialized(signal.monitorId, signal.timestampMs)
        advanceWindows(signal.monitorId, signal.timestampMs)

        if (signal.active) {
            Log.d("AwakeDetector", "signal.active ${signal.active}")
            activeSince.putIfAbsent(signal.monitorId, signal.timestampMs)
            lastActiveTrueAt[signal.monitorId] = signal.timestampMs
            activeSeenInWindow[signal.monitorId] = true
        }

        val allMonitorIds = (kindById.keys + currentWindowStartMs.keys).toSet()
        allMonitorIds.forEach { id ->
            ensureWindowInitialized(id, nowMs)
            advanceWindows(id, nowMs)
        }

        allMonitorIds.forEach { id ->
            val lastTrueAt = lastActiveTrueAt[id]
            if (lastTrueAt != null && nowMs - lastTrueAt >= ACTIVE_SINCE_TTL_MS) {
                activeSince.remove(id)
                lastActiveTrueAt.remove(id)
            }
        }

        // Keep settings access for compatibility with current constructor contract.
        settingsProvider()
        val triggered = consecutiveActiveWindows
            .mapNotNull { (id, count) ->
                if (count >= REQUIRED_CONSECUTIVE_WINDOWS) {
                    id to (consecutiveSince[id] ?: nowMs)
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

    private fun ensureWindowInitialized(monitorId: String, timestampMs: Long) {
        if (currentWindowStartMs.containsKey(monitorId)) return
        currentWindowStartMs[monitorId] = alignToWindowStart(timestampMs)
        activeSeenInWindow[monitorId] = false
        consecutiveActiveWindows.putIfAbsent(monitorId, 0)
    }

    private fun advanceWindows(monitorId: String, timestampMs: Long) {
        var startMs = currentWindowStartMs[monitorId] ?: return
        while (timestampMs >= startMs + SIGNAL_WINDOW_MS) {
            val activeSeen = activeSeenInWindow[monitorId] == true
            if (activeSeen) {
                val previous = consecutiveActiveWindows[monitorId] ?: 0
                if (previous == 0) {
                    consecutiveSince[monitorId] = startMs
                }
                consecutiveActiveWindows[monitorId] = previous + 1
            } else {
                consecutiveActiveWindows[monitorId] = 0
                consecutiveSince.remove(monitorId)
            }

            startMs += SIGNAL_WINDOW_MS
            currentWindowStartMs[monitorId] = startMs
            activeSeenInWindow[monitorId] = false
        }
    }

    private fun alignToWindowStart(timestampMs: Long): Long {
        return timestampMs - (timestampMs % SIGNAL_WINDOW_MS)
    }
}
