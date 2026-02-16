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

    private val activeSince = mutableMapOf<String, Long>()
    private val kindById = mutableMapOf<String, MonitorKind>()

    override fun onSignal(signal: MonitorSignal, nowMs: Long): AwakeState {
        kindById[signal.monitorId] = signal.kind

        if (signal.active) {
            activeSince.putIfAbsent(signal.monitorId, signal.timestampMs)
//        } else {
//            activeSince.remove(signal.monitorId)
        }

        val settings = settingsProvider()
        val triggered = activeSince
            .mapNotNull { (id, since) ->
                val sec = when (kindById[id]) {
                    MonitorKind.MICROPHONE -> settings.cryThresholdSec
                    MonitorKind.CAMERA -> settings.movementThresholdSec
                    else -> settings.cryThresholdSec
                }
                val durationMs = nowMs - since
                if (durationMs >= sec * 1_000L) id to since else null
            }

        if (triggered.isEmpty()) {
            return AwakeState(isAwake = false)
        }

        val awakeSinceMs = triggered.minOf { it.second }
        val reason = triggered.joinToString(",") { it.first }
        return AwakeState(isAwake = true, awakeSinceMs = awakeSinceMs, reason = reason)
    }
}
