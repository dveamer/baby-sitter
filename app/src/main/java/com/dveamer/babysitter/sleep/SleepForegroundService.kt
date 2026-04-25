package com.dveamer.babysitter.sleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dveamer.babysitter.BabySitterApplication
import com.dveamer.babysitter.MainActivity
import com.dveamer.babysitter.R
import com.dveamer.babysitter.alert.AwakeAlertController
import com.dveamer.babysitter.collect.CollectInputPolicy
import com.dveamer.babysitter.collect.CollectAudioSource
import com.dveamer.babysitter.collect.CollectCameraSource
import com.dveamer.babysitter.collect.CollectClosedFileBus
import com.dveamer.babysitter.monitor.CameraMonitor
import com.dveamer.babysitter.monitor.MicrophoneMonitor
import com.dveamer.babysitter.monitor.Monitor
import com.dveamer.babysitter.monitor.MonitorKind
import com.dveamer.babysitter.settings.MotionSensitivity
import com.dveamer.babysitter.settings.SoundSensitivity
import com.dveamer.babysitter.soothing.IotSoothingListener
import com.dveamer.babysitter.soothing.MusicSoothingListener
import com.dveamer.babysitter.soothing.SequentialSoothingCoordinator
import com.dveamer.babysitter.soothing.SootheRequest
import com.dveamer.babysitter.soothing.SootheResult
import com.dveamer.babysitter.soothing.SoothingListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class MusicPlaybackOwner {
    AWAKE
}

class SleepForegroundService : Service() {

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "unhandled service coroutine error", throwable)
    }
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + serviceExceptionHandler
    )
    private val fileWorkerDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val startStopLock = Mutex()

    private var monitoringJob: Job? = null
    private var wakeMemoryBuildJob: Job? = null
    private var wakeMemoryFollowUpJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeMemoryManager: WakeMemoryManager = WakeMemoryManager()
    private var collectCameraSource: CollectCameraSource? = null
    private var collectAudioSource: CollectAudioSource? = null

    private val container by lazy {
        (application as BabySitterApplication).container
    }

    private val maintenanceScheduler by lazy {
        MaintenanceScheduler(
            paths = container.collectStoragePaths,
            catalog = container.collectCatalog,
            isAwakeSessionActive = { wakeMemoryManager.isAwakeSessionActive() },
            workerDispatcher = fileWorkerDispatcher
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    stopMonitoringAndService()
                }
            }

            ACTION_REFRESH -> {
                val started = runCatching {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }.onFailure { e ->
                    Log.w(TAG, "failed to enter foreground mode", e)
                }.isSuccess
                if (!started) {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                serviceScope.launch {
                    refreshCollectInputs()
                }
            }

            ACTION_START, null -> {
                val started = runCatching {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }.onFailure { e ->
                    Log.w(TAG, "failed to enter foreground mode", e)
                }.isSuccess
                if (!started) {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                serviceScope.launch {
                    restartMonitoring()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun restartMonitoring() {
        startStopLock.withLock {
            val current = container.settingsRepository.state.value
            transitionOutOfMonitoring(
                clearSessionOnSuccessfulFlush = !current.sleepEnabled
            )
            container.collectRecorderCoordinator.start()
            val collectInputPolicy = container.collectRecorderCoordinator.updateInputs(
                sleepEnabled = current.sleepEnabled,
                cameraMonitoringEnabled = current.cameraMonitoringEnabled,
                webCameraEnabled = current.webCameraEnabled,
                soundMonitoringEnabled = current.soundMonitoringEnabled
            )
            ensureCollectSourcesRunning(
                collectInputPolicy = collectInputPolicy
            )

            val shouldHoldWakeLock = current.sleepEnabled
            if (shouldHoldWakeLock) {
                acquireWakeLock()
            } else {
                releaseWakeLock()
            }
            maintenanceScheduler.start(serviceScope)
            startWakeMemoryFollowUpLoop()
            monitoringJob = serviceScope.launch {
                runEngine()
            }
        }
    }

    private suspend fun refreshCollectInputs() {
        startStopLock.withLock {
            val current = container.settingsRepository.state.value
            container.collectRecorderCoordinator.start()
            val collectInputPolicy = container.collectRecorderCoordinator.updateInputs(
                sleepEnabled = current.sleepEnabled,
                cameraMonitoringEnabled = current.cameraMonitoringEnabled,
                webCameraEnabled = current.webCameraEnabled,
                soundMonitoringEnabled = current.soundMonitoringEnabled
            )
            ensureCollectSourcesRunning(
                collectInputPolicy = collectInputPolicy
            )
        }
    }

    private suspend fun stopMonitoringAndService() {
        startStopLock.withLock {
            transitionOutOfMonitoring(clearSessionOnSuccessfulFlush = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        stopMonitoringCore()
        resetMonitoringState()
    }

    private suspend fun transitionOutOfMonitoring(
        clearSessionOnSuccessfulFlush: Boolean
    ) {
        stopMonitoringCore()
        awaitWakeMemoryBuildCompletion()
        flushPendingWakeMemoryBuild(clearSessionOnSuccessfulFlush)
        resetMonitoringState()
    }

    private fun stopMonitoringCore() {
        monitoringJob?.cancel()
        monitoringJob = null
        wakeMemoryFollowUpJob?.cancel()
        wakeMemoryFollowUpJob = null
        maintenanceScheduler.stop()
        container.collectRecorderCoordinator.stop()
        collectCameraSource?.stop()
        collectAudioSource?.stop()
        collectCameraSource = null
        collectAudioSource = null
        releaseWakeLock()
    }

    private fun resetMonitoringState() {
        CollectClosedFileBus.clear()
        SleepRuntimeStatusStore.reset()
    }

    private suspend fun awaitWakeMemoryBuildCompletion() {
        wakeMemoryBuildJob?.let { buildJob ->
            if (buildJob.isActive) {
                runCatching { buildJob.join() }
                    .onFailure { Log.w(TAG, "waiting for wake memory build failed", it) }
            }
        }
        wakeMemoryBuildJob = null
    }

    private suspend fun flushPendingWakeMemoryBuild(
        clearSessionOnSuccessfulFlush: Boolean
    ) {
        val latestClosedVideoEndMs = container.memoryBuildCoordinator.latestClosedVideoEndMs()
        val trigger = wakeMemoryManager.onForceBuildCheck(
            latestClosedVideoEndMs = latestClosedVideoEndMs,
            nowMs = System.currentTimeMillis()
        ) ?: return

        Log.i(
            TAG,
            "wake memory flush trigger awakeStartedAt=${trigger.awakeStartedAt} requestedRangeEndMs=${trigger.requestedRangeEndMs}"
        )
        runMemoryBuild(
            trigger = trigger,
            reason = "transition_flush",
            clearSessionOnSuccessfulBuild = clearSessionOnSuccessfulFlush
        )
    }

    private fun startWakeMemoryFollowUpLoop() {
        wakeMemoryFollowUpJob?.cancel()
        wakeMemoryFollowUpJob = serviceScope.launch(fileWorkerDispatcher) {
            while (isActive) {
                delay(WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS)
                val latestClosedVideoEndMs = container.memoryBuildCoordinator.latestClosedVideoEndMs()
                val trigger = wakeMemoryManager.onPeriodicCheck(
                    latestClosedVideoEndMs = latestClosedVideoEndMs,
                    nowMs = System.currentTimeMillis()
                ) ?: continue

                Log.i(
                    TAG,
                    "wake memory periodic trigger awakeStartedAt=${trigger.awakeStartedAt} requestedRangeEndMs=${trigger.requestedRangeEndMs}"
                )
                launchMemoryBuild(trigger = trigger, reason = "periodic_follow_up")
            }
        }
    }

    private suspend fun runEngine() {
        val settings = container.settingsRepository.state.value
        if (!settings.sleepEnabled) {
            return
        }

        val monitors = buildList<Monitor> {
            if (settings.soundMonitoringEnabled) {
                val baseThreshold = settings.cryThresholdSec.coerceIn(50, 8_000)
                val soundThreshold = (baseThreshold * 0.75).coerceIn(120.0, 2_500.0)
                add(MicrophoneMonitor(serviceScope, amplitudeThreshold = soundThreshold))
            }
            if (settings.cameraMonitoringEnabled) {
                val diffThreshold = settings.movementThresholdSec.coerceIn(5, 100)
                val minChangedRatio = when (settings.motionSensitivity) {
                    MotionSensitivity.HIGH -> 0.012
                    MotionSensitivity.MEDIUM -> 0.03
                    MotionSensitivity.LOW -> 0.06
                }
                add(
                    CameraMonitor(
                        scope = serviceScope,
                        diffThreshold = diffThreshold,
                        minChangedRatio = minChangedRatio
                    )
                )
            }
        }

        val musicSoothingListener = if (settings.soothingMusicEnabled) {
            MusicSoothingListener(
                scope = serviceScope,
                context = this@SleepForegroundService,
                settingsRepository = container.settingsRepository,
                onPlaybackStateChanged = SleepRuntimeStatusStore::setLullabyActive
            )
        } else {
            null
        }

        val soothingListeners = buildList<SoothingListener> {
            if (settings.soothingIotEnabled) {
                add(IotSoothingListener(container.settingsRepository))
            }
        }

        val detector = ContinuousAwakeDetector { container.settingsRepository.state.value }
        val soothingCoordinator = SequentialSoothingCoordinator(soothingListeners)
        val alertController = AwakeAlertController(container.alertSender)
        val awakeMusicStopController = PlaybackInactivityController(AWAKE_MUSIC_STOP_GRACE_MS)

        try {
            monitors.forEach { it.start() }
            if (monitors.isEmpty()) {
                if (settings.sleepEnabled) {
                    Log.w(TAG, "sleep enabled but no monitor is available")
                }
                return
            }
            SleepRuntimeStatusStore.setMonitoringActive(true)
            var lastLullabyActiveAtMs: Long = 0L
            var micSuppressedUntilMs: Long = 0L
            var lastSoothedAwakeSinceMs: Long? = null
            var musicPlaybackOwner: MusicPlaybackOwner? = null
            merge(*monitors.map { it.signals }.toTypedArray()).collect { signal ->
                val now = System.currentTimeMillis()
                val lullabyActive = SleepRuntimeStatusStore.state.value.lullabyActive
                if (lullabyActive) {
                    lastLullabyActiveAtMs = now
                } else {
                    musicPlaybackOwner = null
                    awakeMusicStopController.reset()
                }

                val shouldSuppressMic = signal.kind == MonitorKind.MICROPHONE &&
                    (
                        lullabyActive ||
                            now <= micSuppressedUntilMs ||
                            now - lastLullabyActiveAtMs <= MIC_SUPPRESS_AFTER_LULLABY_MS
                        )
                val effectiveSignal = if (shouldSuppressMic && signal.active) {
                    signal.copy(active = false)
                } else {
                    signal
                }
                val awake = detector.onSignal(effectiveSignal, now)

                if (musicSoothingListener != null && lullabyActive && musicPlaybackOwner == MusicPlaybackOwner.AWAKE) {
                    when (awakeMusicStopController.onConditionChanged(awake.isAwake, now)) {
                        PlaybackInactivityAction.REQUEST_STOP_AFTER_CURRENT_TRACK -> {
                            musicSoothingListener.stopAfterCurrentTrack("awake_inactive")
                        }

                        PlaybackInactivityAction.CLEAR_STOP_REQUEST -> {
                            musicSoothingListener.clearPendingStop("awake_resumed")
                        }

                        PlaybackInactivityAction.NONE -> Unit
                    }
                } else {
                    awakeMusicStopController.reset()
                }

                if (awake.isAwake && awake.awakeSinceMs != null) {
                    Log.d(TAG, "awake signal reason=${awake.reason} awakeSince=${awake.awakeSinceMs}")
                    wakeMemoryManager.onAwakeSignal(now)
                    val sootheRequest = SootheRequest(
                        awakeSinceMs = awake.awakeSinceMs,
                        reason = awake.reason,
                        requestedAtMs = now
                    )
                    if (lastSoothedAwakeSinceMs != awake.awakeSinceMs) {
                        soothingCoordinator.soothe(sootheRequest)
                        if (awake.reason.contains("microphone")) {
                            micSuppressedUntilMs = now + MIC_SUPPRESS_AFTER_SOOTHE_MS
                        }
                        lastSoothedAwakeSinceMs = awake.awakeSinceMs
                    }
                    if (musicSoothingListener != null && !lullabyActive) {
                        val result = musicSoothingListener.soothe(sootheRequest)
                        if (result == SootheResult.STARTED) {
                            musicPlaybackOwner = MusicPlaybackOwner.AWAKE
                            awakeMusicStopController.reset()
                            if (awake.reason.contains("microphone")) {
                                micSuppressedUntilMs = now + MIC_SUPPRESS_AFTER_SOOTHE_MS
                            }
                        }
                    }
                    alertController.onAwake(
                        awakeSinceMs = awake.awakeSinceMs,
                        nowMs = now,
                        settings = container.settingsRepository.state.value
                    )
                } else {
                    alertController.onSleep()
                    lastSoothedAwakeSinceMs = null
                    val trigger = wakeMemoryManager.onPassiveSignal(
                        lullabyActive = lullabyActive,
                        nowMs = now
                    )
                    if (trigger != null) {
                        Log.i(
                            TAG,
                            "wake memory trigger awakeStartedAt=${trigger.awakeStartedAt} requestedRangeEndMs=${trigger.requestedRangeEndMs}"
                        )
                        launchMemoryBuild(trigger, reason = "sleep_stable")
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                musicSoothingListener?.stop("engine_finished")
            }
            SleepRuntimeStatusStore.reset()
            monitors.forEach { it.stop() }
        }
    }

    private fun ensureCollectSourcesRunning(collectInputPolicy: CollectInputPolicy) {
        if (collectInputPolicy.cameraInputEnabled) {
            val cameraSource = collectCameraSource ?: CollectCameraSource(
                    context = this,
                    paths = container.collectStoragePaths,
                    scope = serviceScope,
                    motionAnalysisEnabled = collectInputPolicy.motionAnalysisEnabled,
                    webPreviewAllowed = collectInputPolicy.webPreviewAllowed,
                    isPreviewDemandActive = container.collectRecorderCoordinator::isWebPreviewDemandActive
                ).also {
                    collectCameraSource = it
                }
            cameraSource.updateCapturePolicy(
                motionAnalysisEnabled = collectInputPolicy.motionAnalysisEnabled,
                webPreviewAllowed = collectInputPolicy.webPreviewAllowed
            )
            cameraSource.start()
        } else {
            collectCameraSource?.stop()
            collectCameraSource = null
        }

        if (collectInputPolicy.audioInputEnabled) {
            if (collectAudioSource == null) {
                collectAudioSource = CollectAudioSource(
                    paths = container.collectStoragePaths,
                    scope = serviceScope
                )
            }
            collectAudioSource?.start()
        } else {
            collectAudioSource?.stop()
            collectAudioSource = null
        }
    }

    private fun launchMemoryBuild(
        trigger: WakeMemoryTrigger,
        reason: String,
        clearSessionOnSuccessfulBuild: Boolean = false
    ) {
        wakeMemoryBuildJob = serviceScope.launch(fileWorkerDispatcher) {
            runMemoryBuild(
                trigger = trigger,
                reason = reason,
                clearSessionOnSuccessfulBuild = clearSessionOnSuccessfulBuild
            )
        }
    }

    private suspend fun runMemoryBuild(
        trigger: WakeMemoryTrigger,
        reason: String,
        clearSessionOnSuccessfulBuild: Boolean = false
    ) {
        val result = runCatching {
            container.memoryBuildCoordinator.buildWakeMemory(trigger)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            Log.w(TAG, "memory build crashed reason=$reason", throwable)
            CoordinatedMemoryBuildResult(
                outputFile = null,
                usedVideoFiles = 0,
                usedAudioFiles = 0,
                skippedReason = MemoryBuildCoordinator.SKIP_BUILD_FAILED,
                rangeStartMs = trigger.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS,
                requestedRangeEndMs = trigger.requestedRangeEndMs
            )
        }

        result.outputFile?.let {
            Log.i(
                TAG,
                "memory generated reason=$reason file=${it.name} videos=${result.usedVideoFiles} audios=${result.usedAudioFiles}"
            )
        }
        if (result.outputFile == null) {
            Log.w(
                TAG,
                "memory build produced no file: reason=$reason skip=${result.skippedReason} videos=${result.usedVideoFiles} audios=${result.usedAudioFiles} start=${result.rangeStartMs} end=${result.effectiveRangeEndMs ?: result.requestedRangeEndMs}"
            )
        }

        wakeMemoryManager.markMemoryBuildFinished(
            result = result,
            clearSessionOnSuccessfulBuild = clearSessionOnSuccessfulBuild
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:SleepWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.fgs_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.fgs_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.dveamer.babysitter.action.START"
        const val ACTION_STOP = "com.dveamer.babysitter.action.STOP"
        const val ACTION_REFRESH = "com.dveamer.babysitter.action.REFRESH"

        private const val TAG = "SleepForegroundSvc"
        private const val CHANNEL_ID = "sleep_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MIC_SUPPRESS_AFTER_LULLABY_MS = 20_000L
        private const val MIC_SUPPRESS_AFTER_SOOTHE_MS = 60_000L
        private const val AWAKE_MUSIC_STOP_GRACE_MS = 15_000L
    }
}
