package com.dveamer.babysitter.remote

import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsPatch
import com.dveamer.babysitter.settings.UpdateSource

class RemoteCommandHandler(
    private val settingsController: SettingsController
) {
    suspend fun onRemoteSleep(enabled: Boolean): Boolean {
        return settingsController.update(
            patch = SettingsPatch(sleepEnabled = enabled),
            source = UpdateSource.REMOTE
        )
    }

    suspend fun onRemoteSettings(patch: SettingsPatch): Boolean {
        return settingsController.update(
            patch = patch,
            source = UpdateSource.REMOTE
        )
    }
}
