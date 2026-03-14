package com.dveamer.babysitter.tutorial

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.dveamer.babysitter.settings.SettingsState
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private const val TUTORIAL_DATASTORE_NAME = "baby_sitter_tutorial"

private val Context.tutorialDataStore by preferencesDataStore(name = TUTORIAL_DATASTORE_NAME)

class DataStoreTutorialRepository(
    private val context: Context
) : TutorialRepository {

    private val mutableState = MutableStateFlow(TutorialState())
    override val state: StateFlow<TutorialState> = mutableState

    suspend fun initialize(settingsState: SettingsState) {
        seedExistingInstallIfNeeded(settingsState)
        migrateCelebrationStateIfNeeded()
        mutableState.value = readPreferences().toTutorialState()
    }

    override suspend fun dismissWelcome() =
        updateState { copy(welcomeDismissed = true) }

    override suspend fun markSettingsVisited() =
        updateState { copy(hasVisitedSettings = true) }

    override suspend fun finishFirstSettingsVisit() =
        updateState { copy(firstSettingsVisitFinished = true) }

    override suspend fun markSoundEnabled() =
        updateState { copy(soundEverEnabled = true) }

    override suspend fun markMotionEnabled() =
        updateState { copy(motionEverEnabled = true) }

    override suspend fun dismissSoundMotionCoach() =
        updateState { copy(soundMotionCoachDismissed = true) }

    override suspend fun markRemoteCoachReady() =
        updateState { copy(remoteCoachReady = true) }

    override suspend fun dismissRemoteCoach() =
        updateState { copy(remoteCoachDismissed = true) }

    override suspend fun markCelebrationReady() =
        updateState { copy(celebrationReady = true) }

    override suspend fun dismissCelebration() =
        updateState { copy(celebrationDismissed = true) }

    private suspend fun seedExistingInstallIfNeeded(settingsState: SettingsState) {
        context.tutorialDataStore.edit { prefs ->
            if (prefs.contains(Keys.WELCOME_DISMISSED)) return@edit
            if (!shouldSkipInitialTutorial(settingsState)) return@edit

            prefs.fromTutorialState(
                TutorialState(
                    welcomeDismissed = true,
                    hasVisitedSettings = true,
                    firstSettingsVisitFinished = true,
                    soundEverEnabled = settingsState.soundMonitoringEnabled,
                    motionEverEnabled = settingsState.cameraMonitoringEnabled,
                    soundMotionCoachDismissed = true,
                    remoteCoachReady = true,
                    remoteCoachDismissed = true,
                    celebrationReady = true,
                    celebrationDismissed = true
                )
            )
        }
    }

    private suspend fun migrateCelebrationStateIfNeeded() {
        context.tutorialDataStore.edit { prefs ->
            if (!prefs.contains(Keys.WELCOME_DISMISSED)) return@edit
            if (prefs.contains(Keys.CELEBRATION_DISMISSED)) return@edit

            val remoteDismissed = prefs[Keys.REMOTE_COACH_DISMISSED] ?: false
            if (remoteDismissed) {
                prefs[Keys.CELEBRATION_READY] = true
                prefs[Keys.CELEBRATION_DISMISSED] = true
            } else {
                prefs[Keys.CELEBRATION_READY] = false
                prefs[Keys.CELEBRATION_DISMISSED] = false
            }
        }
    }

    private fun shouldSkipInitialTutorial(settingsState: SettingsState): Boolean {
        return settingsState.updatedAtEpochMs > 0L ||
            settingsState.sleepEnabled ||
            settingsState.webServiceEnabled ||
            settingsState.webCameraEnabled ||
            settingsState.soundMonitoringEnabled ||
            settingsState.cameraMonitoringEnabled ||
            settingsState.musicPlaylist.any(::isM4aRecordingUri)
    }

    private fun isM4aRecordingUri(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return false
        return name.substringAfterLast('.', "").equals("m4a", ignoreCase = true)
    }

    private suspend fun updateState(transform: TutorialState.() -> TutorialState) {
        context.tutorialDataStore.edit { prefs ->
            val current = prefs.toTutorialState()
            prefs.fromTutorialState(current.transform())
        }
        mutableState.value = readPreferences().toTutorialState()
    }

    private suspend fun readPreferences(): Preferences {
        return context.tutorialDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
    }

    private fun Preferences.toTutorialState(): TutorialState {
        return TutorialState(
            welcomeDismissed = this[Keys.WELCOME_DISMISSED] ?: false,
            hasVisitedSettings = this[Keys.HAS_VISITED_SETTINGS] ?: false,
            firstSettingsVisitFinished = this[Keys.FIRST_SETTINGS_VISIT_FINISHED] ?: false,
            soundEverEnabled = this[Keys.SOUND_EVER_ENABLED] ?: false,
            motionEverEnabled = this[Keys.MOTION_EVER_ENABLED] ?: false,
            soundMotionCoachDismissed = this[Keys.SOUND_MOTION_COACH_DISMISSED] ?: false,
            remoteCoachReady = this[Keys.REMOTE_COACH_READY] ?: false,
            remoteCoachDismissed = this[Keys.REMOTE_COACH_DISMISSED] ?: false,
            celebrationReady = this[Keys.CELEBRATION_READY] ?: false,
            celebrationDismissed = this[Keys.CELEBRATION_DISMISSED] ?: false
        )
    }

    private fun MutablePreferences.fromTutorialState(state: TutorialState) {
        this[Keys.WELCOME_DISMISSED] = state.welcomeDismissed
        this[Keys.HAS_VISITED_SETTINGS] = state.hasVisitedSettings
        this[Keys.FIRST_SETTINGS_VISIT_FINISHED] = state.firstSettingsVisitFinished
        this[Keys.SOUND_EVER_ENABLED] = state.soundEverEnabled
        this[Keys.MOTION_EVER_ENABLED] = state.motionEverEnabled
        this[Keys.SOUND_MOTION_COACH_DISMISSED] = state.soundMotionCoachDismissed
        this[Keys.REMOTE_COACH_READY] = state.remoteCoachReady
        this[Keys.REMOTE_COACH_DISMISSED] = state.remoteCoachDismissed
        this[Keys.CELEBRATION_READY] = state.celebrationReady
        this[Keys.CELEBRATION_DISMISSED] = state.celebrationDismissed
    }

    private object Keys {
        val WELCOME_DISMISSED = booleanPreferencesKey("welcome_dismissed")
        val HAS_VISITED_SETTINGS = booleanPreferencesKey("has_visited_settings")
        val FIRST_SETTINGS_VISIT_FINISHED = booleanPreferencesKey("first_settings_visit_finished")
        val SOUND_EVER_ENABLED = booleanPreferencesKey("sound_ever_enabled")
        val MOTION_EVER_ENABLED = booleanPreferencesKey("motion_ever_enabled")
        val SOUND_MOTION_COACH_DISMISSED =
            booleanPreferencesKey("sound_motion_coach_dismissed")
        val REMOTE_COACH_READY = booleanPreferencesKey("remote_coach_ready")
        val REMOTE_COACH_DISMISSED = booleanPreferencesKey("remote_coach_dismissed")
        val CELEBRATION_READY = booleanPreferencesKey("celebration_ready")
        val CELEBRATION_DISMISSED = booleanPreferencesKey("celebration_dismissed")
    }
}
