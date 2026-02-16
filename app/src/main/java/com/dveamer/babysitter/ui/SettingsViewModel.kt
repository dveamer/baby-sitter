package com.dveamer.babysitter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsPatch
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.SettingsState
import com.dveamer.babysitter.settings.UpdateSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val controller: SettingsController
) : ViewModel() {

    val settingsState: StateFlow<SettingsState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.state.value
    )

    fun setSleep(enabled: Boolean) = update(SettingsPatch(sleepEnabled = enabled))

    fun setCryThresholdSec(sec: Int) = update(SettingsPatch(cryThresholdSec = sec.coerceIn(3, 60)))

    fun setMovementThresholdSec(sec: Int) =
        update(SettingsPatch(movementThresholdSec = sec.coerceIn(3, 60)))

    fun setWakeAlertThresholdMin(min: Int) =
        update(SettingsPatch(wakeAlertThresholdMin = min.coerceIn(1, 60)))

    fun setCameraMonitoring(enabled: Boolean) =
        update(SettingsPatch(cameraMonitoringEnabled = enabled))

    fun setSoothingMusic(enabled: Boolean) =
        update(SettingsPatch(soothingMusicEnabled = enabled))

    fun setSoothingIot(enabled: Boolean) =
        update(SettingsPatch(soothingIotEnabled = enabled))

    fun setMusicPlaylistFromText(text: String) {
        val list = text
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        update(SettingsPatch(musicPlaylist = list))
    }

    fun setTelegramBotToken(token: String) = update(SettingsPatch(telegramBotToken = token.trim()))

    fun setTelegramChatId(chatId: String) = update(SettingsPatch(telegramChatId = chatId.trim()))

    fun setIotEndpoint(endpoint: String) = update(SettingsPatch(iotEndpoint = endpoint.trim()))

    private fun update(patch: SettingsPatch) {
        viewModelScope.launch {
            controller.update(patch, UpdateSource.LOCAL_UI)
        }
    }
}
