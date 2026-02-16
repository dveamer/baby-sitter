package com.dveamer.babysitter.alert

import com.dveamer.babysitter.settings.SettingsState

interface AlertSender {
    suspend fun sendWakeAlert(settings: SettingsState, awakeDurationSec: Long)
}
