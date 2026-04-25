package com.dveamer.babysitter

import android.content.Context
import com.dveamer.babysitter.alert.TelegramAlertSender
import com.dveamer.babysitter.audio.DeviceVolumeController
import com.dveamer.babysitter.billing.MemoryDownloadEntitlementRepository
import com.dveamer.babysitter.billing.MemoryDownloadPurchaseManager
import com.dveamer.babysitter.collect.CollectCatalog
import com.dveamer.babysitter.collect.CollectRecorderCoordinator
import com.dveamer.babysitter.collect.CollectStoragePaths
import com.dveamer.babysitter.collect.MemoryRepository
import com.dveamer.babysitter.remote.RemoteCommandHandler
import com.dveamer.babysitter.settings.Clock
import com.dveamer.babysitter.settings.DataStoreSettingsRepository
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.VersionProvider
import com.dveamer.babysitter.sleep.MemoryBuildCoordinator
import com.dveamer.babysitter.sleep.ForegroundServiceSleepRuntime
import com.dveamer.babysitter.sleep.MemoryAssembler
import com.dveamer.babysitter.sleep.SleepRuntime
import com.dveamer.babysitter.sleep.SleepRuntimeOrchestrator
import com.dveamer.babysitter.tutorial.DataStoreTutorialRepository
import com.dveamer.babysitter.tutorial.TutorialRepository
import com.dveamer.babysitter.web.DataStoreMemoryDownloadQuotaStore
import com.dveamer.babysitter.web.EntitledMemoryDownloadLimitProvider
import com.dveamer.babysitter.web.LocalSettingsHttpServer
import com.dveamer.babysitter.web.MemoryDownloadLimiter
import com.dveamer.babysitter.web.WebServiceOrchestrator
import java.util.concurrent.atomic.AtomicLong

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val versionCounter = AtomicLong(System.currentTimeMillis())

    private val dataStoreRepository = DataStoreSettingsRepository(appContext)
    private val dataStoreTutorialRepository = DataStoreTutorialRepository(appContext)
    val settingsRepository: SettingsRepository = dataStoreRepository
    val tutorialRepository: TutorialRepository = dataStoreTutorialRepository

    val settingsController: SettingsController = SettingsController(
        repository = settingsRepository,
        versionProvider = VersionProvider { versionCounter.incrementAndGet() },
        clock = Clock { System.currentTimeMillis() }
    )
    val deviceVolumeController = DeviceVolumeController(appContext)

    val remoteCommandHandler = RemoteCommandHandler(settingsController)

    val alertSender = TelegramAlertSender(appContext)

    val collectStoragePaths = CollectStoragePaths(appContext)
    val collectCatalog = CollectCatalog(collectStoragePaths)
    private val sleepRuntime = ForegroundServiceSleepRuntime(appContext)
    val collectRecorderCoordinator = CollectRecorderCoordinator(
        ensureDirectories = collectStoragePaths::ensureDirectories,
        onWebPreviewDemandChanged = {
            val state = settingsRepository.state.value
            if (state.sleepEnabled || state.webServiceEnabled) {
                sleepRuntime.refresh()
            }
        }
    )
    val memoryAssembler = MemoryAssembler(collectStoragePaths, collectCatalog)
    val memoryBuildCoordinator = MemoryBuildCoordinator(memoryAssembler, collectCatalog)
    val memoryRepository = MemoryRepository(collectCatalog)
    val memoryDownloadEntitlementRepository = MemoryDownloadEntitlementRepository(appContext)
    val memoryDownloadPurchaseManager = MemoryDownloadPurchaseManager(
        context = appContext,
        entitlementRepository = memoryDownloadEntitlementRepository
    )
    private val memoryDownloadQuotaStore = DataStoreMemoryDownloadQuotaStore(appContext)
    private val memoryDownloadLimitProvider = EntitledMemoryDownloadLimitProvider(
        entitlementRepository = memoryDownloadEntitlementRepository
    )
    val memoryDownloadLimiter = MemoryDownloadLimiter(
        quotaStore = memoryDownloadQuotaStore,
        limitProvider = memoryDownloadLimitProvider
    )

    private val sleepRuntimeInterface: SleepRuntime = sleepRuntime

    val sleepRuntimeOrchestrator = SleepRuntimeOrchestrator(settingsRepository, sleepRuntimeInterface)
    private val localSettingsHttpServer =
        LocalSettingsHttpServer(
            context = appContext,
            settingsRepository = settingsRepository,
            settingsController = settingsController,
            deviceVolumeController = deviceVolumeController,
            collectRecorderCoordinator = collectRecorderCoordinator,
            memoryRepository = memoryRepository,
            memoryBuildCoordinator = memoryBuildCoordinator,
            memoryDownloadLimiter = memoryDownloadLimiter
        )
    val webServiceOrchestrator = WebServiceOrchestrator(settingsRepository, localSettingsHttpServer)

    suspend fun initialize() {
        collectStoragePaths.ensureDirectories()
        dataStoreRepository.initialize()
        dataStoreTutorialRepository.initialize(settingsRepository.state.value)
        memoryDownloadPurchaseManager.initialize()
    }
}
