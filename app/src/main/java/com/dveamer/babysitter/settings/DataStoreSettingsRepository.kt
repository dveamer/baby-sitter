package com.dveamer.babysitter.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val DATASTORE_NAME = "baby_sitter_settings"
private const val PLAYLIST_DELIMITER = "\u0001"

private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {

    private val mutableState = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = mutableState

    suspend fun initialize() {
        mutableState.value = readPreferences().toSettingsState()
    }

    override suspend fun applyUpdate(update: SettingsUpdate): Boolean {
        context.dataStore.edit { prefs ->
            val current = prefs.toSettingsState()
            val effectiveVersion = if (update.version <= current.version) {
                current.version + 1
            } else {
                update.version
            }
            val next = current.merge(update.copy(version = effectiveVersion))
            prefs.fromSettingsState(next)
        }
        mutableState.value = readPreferences().toSettingsState()
        return true
    }

    private suspend fun readPreferences(): Preferences {
        return context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
    }

    private fun SettingsState.merge(update: SettingsUpdate): SettingsState {
        val p = update.patch
        return copy(
            sleepEnabled = p.sleepEnabled ?: sleepEnabled,
            cryThresholdSec = p.cryThresholdSec ?: cryThresholdSec,
            movementThresholdSec = p.movementThresholdSec ?: movementThresholdSec,
            cameraMonitoringEnabled = p.cameraMonitoringEnabled ?: cameraMonitoringEnabled,
            soothingMusicEnabled = p.soothingMusicEnabled ?: soothingMusicEnabled,
            soothingIotEnabled = p.soothingIotEnabled ?: soothingIotEnabled,
            wakeAlertThresholdMin = p.wakeAlertThresholdMin ?: wakeAlertThresholdMin,
            musicPlaylist = p.musicPlaylist ?: musicPlaylist,
            telegramBotToken = p.telegramBotToken ?: telegramBotToken,
            telegramChatId = p.telegramChatId ?: telegramChatId,
            iotEndpoint = p.iotEndpoint ?: iotEndpoint,
            version = update.version,
            updatedAtEpochMs = update.updatedAtEpochMs,
            updatedBy = update.source
        )
    }

    private fun Preferences.toSettingsState(): SettingsState {
        return SettingsState(
            sleepEnabled = this[Keys.SLEEP_ENABLED] ?: false,
            cryThresholdSec = this[Keys.CRY_THRESHOLD_SEC] ?: 10,
            movementThresholdSec = this[Keys.MOVEMENT_THRESHOLD_SEC] ?: 10,
            cameraMonitoringEnabled = this[Keys.CAMERA_MONITORING_ENABLED] ?: false,
            soothingMusicEnabled = this[Keys.SOOTHING_MUSIC_ENABLED] ?: true,
            soothingIotEnabled = this[Keys.SOOTHING_IOT_ENABLED] ?: false,
            wakeAlertThresholdMin = this[Keys.WAKE_ALERT_THRESHOLD_MIN] ?: 10,
            musicPlaylist = (this[Keys.MUSIC_PLAYLIST] ?: "")
                .split(PLAYLIST_DELIMITER)
                .filter { it.isNotBlank() },
            telegramBotToken = this[Keys.TELEGRAM_BOT_TOKEN] ?: "",
            telegramChatId = this[Keys.TELEGRAM_CHAT_ID] ?: "",
            iotEndpoint = this[Keys.IOT_ENDPOINT] ?: "",
            version = this[Keys.VERSION] ?: 0,
            updatedAtEpochMs = this[Keys.UPDATED_AT] ?: 0,
            updatedBy = this[Keys.UPDATED_BY]
                ?.let { runCatching { UpdateSource.valueOf(it) }.getOrNull() }
                ?: UpdateSource.SYSTEM
        )
    }

    private fun MutablePreferences.fromSettingsState(state: SettingsState) {
        this[Keys.SLEEP_ENABLED] = state.sleepEnabled
        this[Keys.CRY_THRESHOLD_SEC] = state.cryThresholdSec
        this[Keys.MOVEMENT_THRESHOLD_SEC] = state.movementThresholdSec
        this[Keys.CAMERA_MONITORING_ENABLED] = state.cameraMonitoringEnabled
        this[Keys.SOOTHING_MUSIC_ENABLED] = state.soothingMusicEnabled
        this[Keys.SOOTHING_IOT_ENABLED] = state.soothingIotEnabled
        this[Keys.WAKE_ALERT_THRESHOLD_MIN] = state.wakeAlertThresholdMin
        this[Keys.MUSIC_PLAYLIST] = state.musicPlaylist.joinToString(PLAYLIST_DELIMITER)
        this[Keys.TELEGRAM_BOT_TOKEN] = state.telegramBotToken
        this[Keys.TELEGRAM_CHAT_ID] = state.telegramChatId
        this[Keys.IOT_ENDPOINT] = state.iotEndpoint
        this[Keys.VERSION] = state.version
        this[Keys.UPDATED_AT] = state.updatedAtEpochMs
        this[Keys.UPDATED_BY] = state.updatedBy.name
    }

    private object Keys {
        val SLEEP_ENABLED = booleanPreferencesKey("sleep_enabled")
        val CRY_THRESHOLD_SEC = intPreferencesKey("cry_threshold_sec")
        val MOVEMENT_THRESHOLD_SEC = intPreferencesKey("movement_threshold_sec")
        val CAMERA_MONITORING_ENABLED = booleanPreferencesKey("camera_monitoring_enabled")
        val SOOTHING_MUSIC_ENABLED = booleanPreferencesKey("soothing_music_enabled")
        val SOOTHING_IOT_ENABLED = booleanPreferencesKey("soothing_iot_enabled")
        val WAKE_ALERT_THRESHOLD_MIN = intPreferencesKey("wake_alert_threshold_min")
        val MUSIC_PLAYLIST = stringPreferencesKey("music_playlist")
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val IOT_ENDPOINT = stringPreferencesKey("iot_endpoint")
        val VERSION = longPreferencesKey("version")
        val UPDATED_AT = longPreferencesKey("updated_at")
        val UPDATED_BY = stringPreferencesKey("updated_by")
    }
}
