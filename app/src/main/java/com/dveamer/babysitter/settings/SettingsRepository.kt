package com.dveamer.babysitter.settings

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val state: StateFlow<SettingsState>

    suspend fun applyUpdate(update: SettingsUpdate): Boolean
}
