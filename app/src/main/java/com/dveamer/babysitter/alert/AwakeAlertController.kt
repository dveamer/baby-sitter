package com.dveamer.babysitter.alert

import com.dveamer.babysitter.settings.SettingsState

class AwakeAlertController(
    private val sender: AlertSender
) {

    private var lastAlertedAwakeSince: Long? = null

    suspend fun onAwake(awakeSinceMs: Long, nowMs: Long, settings: SettingsState) {
        val thresholdMs = settings.wakeAlertThresholdMin * 60_000L
        val awakeDurationMs = nowMs - awakeSinceMs

        if (awakeDurationMs < thresholdMs) return
        if (lastAlertedAwakeSince == awakeSinceMs) return

        sender.sendWakeAlert(settings, awakeDurationMs / 1_000L)
        lastAlertedAwakeSince = awakeSinceMs
    }

    fun onSleep() {
        lastAlertedAwakeSince = null
    }
}
