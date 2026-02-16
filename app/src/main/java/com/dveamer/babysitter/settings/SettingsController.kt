package com.dveamer.babysitter.settings

class SettingsController(
    private val repository: SettingsRepository,
    private val versionProvider: VersionProvider,
    private val clock: Clock
) {

    suspend fun update(patch: SettingsPatch, source: UpdateSource): Boolean {
        val update = SettingsUpdate(
            patch = patch,
            source = source,
            updatedAtEpochMs = clock.nowEpochMs(),
            version = versionProvider.nextVersion()
        )
        return repository.applyUpdate(update)
    }
}

fun interface VersionProvider {
    fun nextVersion(): Long
}

fun interface Clock {
    fun nowEpochMs(): Long
}
