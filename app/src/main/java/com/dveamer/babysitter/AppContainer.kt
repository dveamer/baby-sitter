package com.dveamer.babysitter

import android.content.Context
import com.dveamer.babysitter.alert.TelegramAlertSender
import com.dveamer.babysitter.remote.RemoteCommandHandler
import com.dveamer.babysitter.settings.Clock
import com.dveamer.babysitter.settings.DataStoreSettingsRepository
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.VersionProvider
import com.dveamer.babysitter.sleep.ForegroundServiceSleepRuntime
import com.dveamer.babysitter.sleep.SleepRuntime
import com.dveamer.babysitter.sleep.SleepRuntimeOrchestrator
import java.util.concurrent.atomic.AtomicLong

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val versionCounter = AtomicLong(System.currentTimeMillis())

    private val dataStoreRepository = DataStoreSettingsRepository(appContext)
    val settingsRepository: SettingsRepository = dataStoreRepository

    val settingsController: SettingsController = SettingsController(
        repository = settingsRepository,
        versionProvider = VersionProvider { versionCounter.incrementAndGet() },
        clock = Clock { System.currentTimeMillis() }
    )

    val remoteCommandHandler = RemoteCommandHandler(settingsController)

    val alertSender = TelegramAlertSender(appContext)

    private val sleepRuntime: SleepRuntime = ForegroundServiceSleepRuntime(appContext)

    val sleepRuntimeOrchestrator = SleepRuntimeOrchestrator(settingsRepository, sleepRuntime)

    suspend fun initialize() {
        dataStoreRepository.initialize()
    }
}
