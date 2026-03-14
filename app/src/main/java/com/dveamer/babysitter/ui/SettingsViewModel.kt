package com.dveamer.babysitter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.MotionSensitivity
import com.dveamer.babysitter.settings.SettingsPatch
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.SettingsState
import com.dveamer.babysitter.settings.SoundSensitivity
import com.dveamer.babysitter.settings.UpdateSource
import com.dveamer.babysitter.tutorial.TutorialRepository
import com.dveamer.babysitter.tutorial.TutorialState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val controller: SettingsController,
    private val tutorialRepository: TutorialRepository
) : ViewModel() {

    val settingsState: StateFlow<SettingsState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.state.value
    )
    val tutorialState: StateFlow<TutorialState> = tutorialRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = tutorialRepository.state.value
    )

    fun setSleep(enabled: Boolean) = update(SettingsPatch(sleepEnabled = enabled))

    fun setWebService(enabled: Boolean) =
        update(SettingsPatch(webServiceEnabled = enabled))

    fun setWebCamera(enabled: Boolean) =
        update(SettingsPatch(webCameraEnabled = enabled))

    fun setSoundMonitoring(enabled: Boolean) =
        update(SettingsPatch(soundMonitoringEnabled = enabled))

    fun setSoundSensitivity(value: SoundSensitivity) =
        update(SettingsPatch(soundSensitivity = value))

    fun setCryThresholdSec(sec: Int) =
        update(SettingsPatch(cryThresholdSec = sec.coerceIn(10, 1_000)))

    fun setMovementThresholdSec(sec: Int) =
        update(SettingsPatch(movementThresholdSec = sec.coerceIn(5, 100)))

    fun setMotionSensitivity(value: MotionSensitivity) =
        update(SettingsPatch(motionSensitivity = value))

    fun setWakeAlertThresholdMin(min: Int) =
        update(SettingsPatch(wakeAlertThresholdMin = min.coerceIn(1, 60)))

    fun setWakeAlert(enabled: Boolean) =
        update(SettingsPatch(wakeAlertEnabled = enabled))

    fun setCameraMonitoring(enabled: Boolean) =
        update(SettingsPatch(cameraMonitoringEnabled = enabled))

    fun setSoothingMusic(enabled: Boolean) =
        update(SettingsPatch(soothingMusicEnabled = enabled))

    fun setSoothingIot(enabled: Boolean) =
        update(SettingsPatch(soothingIotEnabled = enabled))

    fun addMusicTrack(uri: String) {
        val cleaned = uri.trim()
        if (cleaned.isBlank()) return
        val current = settingsState.value.musicPlaylist
        if (current.contains(cleaned)) return
        update(SettingsPatch(musicPlaylist = current + cleaned))
    }

    fun removeMusicTrackAt(index: Int) {
        val current = settingsState.value.musicPlaylist
        if (index !in current.indices) return
        val next = current.toMutableList().apply { removeAt(index) }
        update(SettingsPatch(musicPlaylist = next))
    }

    fun setTelegramBotToken(token: String) = update(SettingsPatch(telegramBotToken = token.trim()))

    fun setTelegramChatId(chatId: String) = update(SettingsPatch(telegramChatId = chatId.trim()))

    fun setIotEndpoint(endpoint: String) = update(SettingsPatch(iotEndpoint = endpoint.trim()))

    fun dismissWelcomeTutorial() = updateTutorial { tutorialRepository.dismissWelcome() }

    fun markSettingsVisited() = updateTutorial { tutorialRepository.markSettingsVisited() }

    fun finishFirstSettingsVisit() =
        updateTutorial { tutorialRepository.finishFirstSettingsVisit() }

    fun markSoundEnabled() = updateTutorial { tutorialRepository.markSoundEnabled() }

    fun markMotionEnabled() = updateTutorial { tutorialRepository.markMotionEnabled() }

    fun dismissSoundMotionTutorial() =
        updateTutorial { tutorialRepository.dismissSoundMotionCoach() }

    fun markRemoteTutorialReady() =
        updateTutorial { tutorialRepository.markRemoteCoachReady() }

    fun dismissRemoteTutorial() = updateTutorial { tutorialRepository.dismissRemoteCoach() }

    fun markCelebrationTutorialReady() =
        updateTutorial { tutorialRepository.markCelebrationReady() }

    fun dismissCelebrationTutorial() =
        updateTutorial { tutorialRepository.dismissCelebration() }

    private fun update(patch: SettingsPatch) {
        viewModelScope.launch {
            controller.update(patch, UpdateSource.LOCAL_UI)
        }
    }

    private fun updateTutorial(action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
        }
    }
}
