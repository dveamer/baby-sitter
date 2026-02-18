package com.dveamer.babysitter.settings

data class SettingsState(
    val sleepEnabled: Boolean = false,
    val webServiceEnabled: Boolean = false,
    val webCameraEnabled: Boolean = false,
    val soundMonitoringEnabled: Boolean = true,
    val cryThresholdSec: Int = 10,
    val movementThresholdSec: Int = 10,
    val cameraMonitoringEnabled: Boolean = false,
    val soothingMusicEnabled: Boolean = true,
    val soothingIotEnabled: Boolean = false,
    val wakeAlertThresholdMin: Int = 3,
    val musicPlaylist: List<String> = emptyList(),
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val iotEndpoint: String = "",
    val version: Long = 0,
    val updatedAtEpochMs: Long = 0,
    val updatedBy: UpdateSource = UpdateSource.SYSTEM
)

data class SettingsPatch(
    val sleepEnabled: Boolean? = null,
    val webServiceEnabled: Boolean? = null,
    val webCameraEnabled: Boolean? = null,
    val soundMonitoringEnabled: Boolean? = null,
    val cryThresholdSec: Int? = null,
    val movementThresholdSec: Int? = null,
    val cameraMonitoringEnabled: Boolean? = null,
    val soothingMusicEnabled: Boolean? = null,
    val soothingIotEnabled: Boolean? = null,
    val wakeAlertThresholdMin: Int? = null,
    val musicPlaylist: List<String>? = null,
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null,
    val iotEndpoint: String? = null
)

enum class UpdateSource {
    LOCAL_UI,
    REMOTE,
    SYSTEM
}

data class SettingsUpdate(
    val patch: SettingsPatch,
    val source: UpdateSource,
    val updatedAtEpochMs: Long,
    val version: Long
)
