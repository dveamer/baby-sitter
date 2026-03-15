package com.dveamer.babysitter.settings

import android.content.Context
import android.net.Uri
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
import com.dveamer.babysitter.R
import java.io.File
import java.io.IOException

private const val DATASTORE_NAME = "baby_sitter_settings"
private const val PLAYLIST_DELIMITER = "\u0001"
private const val RECORDINGS_DIR = "soothing-recordings"
private const val DEFAULT_BUNDLED_RECORDING_FILE_NAME = "lkoliks-lullaby-baby-sleep-music-331777.mp3"

private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {

    private val mutableState = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = mutableState

    suspend fun initialize() {
        ensureDefaultRecordingIfNeeded()
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

    private suspend fun ensureDefaultRecordingIfNeeded() {
        context.dataStore.edit { prefs ->
            val current = prefs.toSettingsState()
            if (current.musicPlaylist.isNotEmpty()) return@edit

            val defaultFile = ensureBundledRecordingFile()
            val defaultUri = Uri.fromFile(defaultFile).toString()
            prefs.fromSettingsState(current.copy(musicPlaylist = listOf(defaultUri)))
        }
    }

    private fun ensureBundledRecordingFile(): File {
        val outputDir = File(context.filesDir, RECORDINGS_DIR).apply { mkdirs() }
        val outputFile = File(outputDir, DEFAULT_BUNDLED_RECORDING_FILE_NAME)
        if (outputFile.exists() && outputFile.length() > 0L) {
            return outputFile
        }

        context.resources.openRawResource(R.raw.lkoliks_lullaby_baby_sleep_music_331777).use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile
    }

    private fun SettingsState.merge(update: SettingsUpdate): SettingsState {
        val p = update.patch
        var next = copy(
            sleepEnabled = p.sleepEnabled ?: sleepEnabled,
            webServiceEnabled = p.webServiceEnabled ?: webServiceEnabled,
            webCameraEnabled = p.webCameraEnabled ?: webCameraEnabled,
            soundMonitoringEnabled = p.soundMonitoringEnabled ?: soundMonitoringEnabled,
            soundSensitivity = p.soundSensitivity ?: soundSensitivity,
            cryThresholdSec = (p.cryThresholdSec ?: cryThresholdSec).coerceIn(10, 1_000),
            movementThresholdSec = p.movementThresholdSec ?: movementThresholdSec,
            motionSensitivity = p.motionSensitivity ?: motionSensitivity,
            cameraMonitoringEnabled = p.cameraMonitoringEnabled ?: cameraMonitoringEnabled,
            soothingMusicEnabled = p.soothingMusicEnabled ?: soothingMusicEnabled,
            soothingIotEnabled = p.soothingIotEnabled ?: soothingIotEnabled,
            awakeTriggerDelaySec = normalizeAwakeTriggerDelaySec(
                p.awakeTriggerDelaySec ?: awakeTriggerDelaySec
            ),
            wakeAlertEnabled = p.wakeAlertEnabled ?: wakeAlertEnabled,
            wakeAlertThresholdMin = p.wakeAlertThresholdMin ?: wakeAlertThresholdMin,
            musicPlaylist = p.musicPlaylist ?: musicPlaylist,
            themeMode = p.themeMode ?: themeMode,
            telegramBotToken = p.telegramBotToken ?: telegramBotToken,
            telegramChatId = p.telegramChatId ?: telegramChatId,
            iotEndpoint = p.iotEndpoint ?: iotEndpoint
        )

        if (!next.webServiceEnabled && next.webCameraEnabled) {
            next = next.copy(webCameraEnabled = false)
        }
        if (next.sleepEnabled && !next.soundMonitoringEnabled && !next.cameraMonitoringEnabled) {
            next = next.copy(sleepEnabled = false)
        }

        return next.copy(
            version = update.version,
            updatedAtEpochMs = update.updatedAtEpochMs,
            updatedBy = update.source
        )
    }

    private fun Preferences.toSettingsState(): SettingsState {
        val storedSoundSensitivity = this[Keys.SOUND_SENSITIVITY]
            ?.let { runCatching { SoundSensitivity.valueOf(it) }.getOrNull() }
        val soundSensitivity = storedSoundSensitivity ?: SoundSensitivity.MEDIUM

        return SettingsState(
            sleepEnabled = this[Keys.SLEEP_ENABLED] ?: false,
            webServiceEnabled = this[Keys.WEB_SERVICE_ENABLED] ?: false,
            webCameraEnabled = this[Keys.WEB_CAMERA_ENABLED] ?: false,
            soundMonitoringEnabled = this[Keys.SOUND_MONITORING_ENABLED] ?: false,
            soundSensitivity = soundSensitivity,
            cryThresholdSec = normalizeCryThreshold(
                threshold = this[Keys.CRY_THRESHOLD_SEC],
                soundSensitivity = storedSoundSensitivity
            ),
            movementThresholdSec = this[Keys.MOVEMENT_THRESHOLD_SEC] ?: 20,
            motionSensitivity = this[Keys.MOTION_SENSITIVITY]
                ?.let { runCatching { MotionSensitivity.valueOf(it) }.getOrNull() }
                ?: MotionSensitivity.MEDIUM,
            cameraMonitoringEnabled = this[Keys.CAMERA_MONITORING_ENABLED] ?: false,
            soothingMusicEnabled = this[Keys.SOOTHING_MUSIC_ENABLED] ?: true,
            soothingIotEnabled = this[Keys.SOOTHING_IOT_ENABLED] ?: false,
            awakeTriggerDelaySec = normalizeAwakeTriggerDelaySec(
                this[Keys.AWAKE_TRIGGER_DELAY_SEC]
            ),
            wakeAlertEnabled = this[Keys.WAKE_ALERT_ENABLED] ?: true,
            wakeAlertThresholdMin = this[Keys.WAKE_ALERT_THRESHOLD_MIN] ?: 3,
            musicPlaylist = (this[Keys.MUSIC_PLAYLIST] ?: "")
                .split(PLAYLIST_DELIMITER)
                .filter { it.isNotBlank() },
            themeMode = this[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT,
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

    private fun normalizeCryThreshold(
        threshold: Int?,
        soundSensitivity: SoundSensitivity?
    ): Int {
        val value = threshold ?: 500
        val normalized = when (soundSensitivity) {
            SoundSensitivity.HIGH -> when (value) {
                50, 300 -> 250
                else -> value
            }
            SoundSensitivity.MEDIUM -> when (value) {
                250, 700 -> 500
                else -> value
            }
            SoundSensitivity.LOW -> when (value) {
                700, 1000 -> 750
                else -> value
            }
            null -> when (value) {
                50, 300 -> 250
                1000 -> 750
                else -> value
            }
        }
        return normalized.coerceIn(10, 1_000)
    }

    private fun normalizeAwakeTriggerDelaySec(delay: Int?): Int {
        val clamped = (delay ?: DEFAULT_AWAKE_TRIGGER_DELAY_SEC)
            .coerceIn(MIN_AWAKE_TRIGGER_DELAY_SEC, MAX_AWAKE_TRIGGER_DELAY_SEC)
        val normalizedStep = ((clamped - MIN_AWAKE_TRIGGER_DELAY_SEC) + (AWAKE_TRIGGER_DELAY_STEP_SEC / 2)) /
            AWAKE_TRIGGER_DELAY_STEP_SEC
        return (
            MIN_AWAKE_TRIGGER_DELAY_SEC +
                normalizedStep * AWAKE_TRIGGER_DELAY_STEP_SEC
            ).coerceIn(MIN_AWAKE_TRIGGER_DELAY_SEC, MAX_AWAKE_TRIGGER_DELAY_SEC)
    }

    private fun MutablePreferences.fromSettingsState(state: SettingsState) {
        this[Keys.SLEEP_ENABLED] = state.sleepEnabled
        this[Keys.WEB_SERVICE_ENABLED] = state.webServiceEnabled
        this[Keys.WEB_CAMERA_ENABLED] = state.webCameraEnabled
        this[Keys.SOUND_MONITORING_ENABLED] = state.soundMonitoringEnabled
        this[Keys.SOUND_SENSITIVITY] = state.soundSensitivity.name
        this[Keys.CRY_THRESHOLD_SEC] = state.cryThresholdSec
        this[Keys.MOVEMENT_THRESHOLD_SEC] = state.movementThresholdSec
        this[Keys.MOTION_SENSITIVITY] = state.motionSensitivity.name
        this[Keys.CAMERA_MONITORING_ENABLED] = state.cameraMonitoringEnabled
        this[Keys.SOOTHING_MUSIC_ENABLED] = state.soothingMusicEnabled
        this[Keys.SOOTHING_IOT_ENABLED] = state.soothingIotEnabled
        this[Keys.AWAKE_TRIGGER_DELAY_SEC] = state.awakeTriggerDelaySec
        this[Keys.WAKE_ALERT_ENABLED] = state.wakeAlertEnabled
        this[Keys.WAKE_ALERT_THRESHOLD_MIN] = state.wakeAlertThresholdMin
        this[Keys.MUSIC_PLAYLIST] = state.musicPlaylist.joinToString(PLAYLIST_DELIMITER)
        this[Keys.THEME_MODE] = state.themeMode.name
        this[Keys.TELEGRAM_BOT_TOKEN] = state.telegramBotToken
        this[Keys.TELEGRAM_CHAT_ID] = state.telegramChatId
        this[Keys.IOT_ENDPOINT] = state.iotEndpoint
        this[Keys.VERSION] = state.version
        this[Keys.UPDATED_AT] = state.updatedAtEpochMs
        this[Keys.UPDATED_BY] = state.updatedBy.name
    }

    private object Keys {
        val SLEEP_ENABLED = booleanPreferencesKey("sleep_enabled")
        val WEB_SERVICE_ENABLED = booleanPreferencesKey("web_service_enabled")
        val WEB_CAMERA_ENABLED = booleanPreferencesKey("web_camera_enabled")
        val SOUND_MONITORING_ENABLED = booleanPreferencesKey("sound_monitoring_enabled")
        val SOUND_SENSITIVITY = stringPreferencesKey("sound_sensitivity")
        val CRY_THRESHOLD_SEC = intPreferencesKey("cry_threshold_sec")
        val MOVEMENT_THRESHOLD_SEC = intPreferencesKey("movement_threshold_sec")
        val MOTION_SENSITIVITY = stringPreferencesKey("motion_sensitivity")
        val CAMERA_MONITORING_ENABLED = booleanPreferencesKey("camera_monitoring_enabled")
        val SOOTHING_MUSIC_ENABLED = booleanPreferencesKey("soothing_music_enabled")
        val SOOTHING_IOT_ENABLED = booleanPreferencesKey("soothing_iot_enabled")
        val AWAKE_TRIGGER_DELAY_SEC = intPreferencesKey("awake_trigger_delay_sec")
        val WAKE_ALERT_ENABLED = booleanPreferencesKey("wake_alert_enabled")
        val WAKE_ALERT_THRESHOLD_MIN = intPreferencesKey("wake_alert_threshold_min")
        val MUSIC_PLAYLIST = stringPreferencesKey("music_playlist")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val IOT_ENDPOINT = stringPreferencesKey("iot_endpoint")
        val VERSION = longPreferencesKey("version")
        val UPDATED_AT = longPreferencesKey("updated_at")
        val UPDATED_BY = stringPreferencesKey("updated_by")
    }
}
