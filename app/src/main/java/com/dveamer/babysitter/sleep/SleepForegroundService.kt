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
import com.dveamer.babysitter.collect.CollectAudioSource
import com.dveamer.babysitter.collect.CollectCameraSource
import com.dveamer.babysitter.collect.CollectClosedFileBus
import com.dveamer.babysitter.collect.CollectFileType
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
import com.dveamer.babysitter.soothing.SoothingListener
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

            ACTION_START, ACTION_REFRESH, null -> {
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
            stopMonitoring()
            SleepRuntimeStatusStore.reset()
            wakeMemoryManager = WakeMemoryManager()
            CollectClosedFileBus.clear()

            val current = container.settingsRepository.state.value
            container.collectRecorderCoordinator.start()
            container.collectRecorderCoordinator.updateInputs(
                cameraMonitoringEnabled = current.cameraMonitoringEnabled,
                webCameraEnabled = current.webCameraEnabled,
                soundMonitoringEnabled = current.soundMonitoringEnabled
            )
            ensureCollectSourcesRunning()

            val shouldHoldWakeLock = current.sleepEnabled
            if (shouldHoldWakeLock) {
                acquireWakeLock()
            } else {
                releaseWakeLock()
            }
            maintenanceScheduler.start(serviceScope)
            monitoringJob = serviceScope.launch {
                runEngine()
            }
        }
    }

    private suspend fun stopMonitoringAndService() {
        startStopLock.withLock {
            stopMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        maintenanceScheduler.stop()
        container.collectRecorderCoordinator.stop()
        collectCameraSource?.stop()
        collectAudioSource?.stop()
        collectCameraSource = null
        collectAudioSource = null
        CollectClosedFileBus.clear()
        SleepRuntimeStatusStore.reset()
        releaseWakeLock()
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

        val soothingListeners = buildList<SoothingListener> {
            if (settings.soothingMusicEnabled) {
                add(
                    MusicSoothingListener(
                        context = this@SleepForegroundService,
                        settingsRepository = container.settingsRepository,
                        onPlaybackStateChanged = SleepRuntimeStatusStore::setLullabyActive
                    )
                )
            }
            if (settings.soothingIotEnabled) {
                add(IotSoothingListener(container.settingsRepository))
            }
        }

        val detector = ContinuousAwakeDetector { container.settingsRepository.state.value }
        val soothingCoordinator = SequentialSoothingCoordinator(soothingListeners)
        val alertController = AwakeAlertController(container.alertSender)

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
            merge(*monitors.map { it.signals }.toTypedArray()).collect { signal ->
                val now = System.currentTimeMillis()
                val lullabyActive = SleepRuntimeStatusStore.state.value.lullabyActive
                if (lullabyActive) {
                    lastLullabyActiveAtMs = now
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

                if (awake.isAwake && awake.awakeSinceMs != null) {
                    Log.d(TAG, "awake signal reason=${awake.reason} awakeSince=${awake.awakeSinceMs}")
                    wakeMemoryManager.onAwakeSignal(now)
                    if (lastSoothedAwakeSinceMs != awake.awakeSinceMs) {
                        soothingCoordinator.soothe(
                            SootheRequest(
                                awakeSinceMs = awake.awakeSinceMs,
                                reason = awake.reason,
                                requestedAtMs = now
                            )
                        )
                        if (awake.reason.contains("microphone")) {
                            micSuppressedUntilMs = now + MIC_SUPPRESS_AFTER_SOOTHE_MS
                        }
                        lastSoothedAwakeSinceMs = awake.awakeSinceMs
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
                            "wake memory trigger awakeStartedAt=${trigger.awakeStartedAt} sleepStableEndedAt=${trigger.sleepStableEndedAt}"
                        )
                        launchMemoryBuild(trigger)
                    }
                }
            }
        } finally {
            SleepRuntimeStatusStore.reset()
            monitors.forEach { it.stop() }
        }
    }

    private fun ensureCollectSourcesRunning() {
        if (container.collectRecorderCoordinator.isCameraInputEnabled()) {
            if (collectCameraSource == null) {
                collectCameraSource = CollectCameraSource(
                    context = this,
                    paths = container.collectStoragePaths,
                    scope = serviceScope
                )
            }
            collectCameraSource?.start()
        } else {
            collectCameraSource?.stop()
            collectCameraSource = null
        }

        if (container.collectRecorderCoordinator.isAudioInputEnabled()) {
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

    private fun launchMemoryBuild(trigger: WakeMemoryTrigger) {
        serviceScope.launch(fileWorkerDispatcher) {
            SleepRuntimeStatusStore.setMemoryBuildInProgress(true)
            val rangeStart = trigger.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS
            var result: MemoryBuildResult? = null
            var lastEffectiveEnd = trigger.sleepStableEndedAt
            for (attempt in 0 until MEMORY_BUILD_MAX_WAIT_ATTEMPTS) {
                val effectiveEnd = resolveMemoryRangeEndMs(trigger.sleepStableEndedAt)
                lastEffectiveEnd = effectiveEnd
                if (effectiveEnd >= rangeStart) {
                    result = runCatching {
                        container.memoryAssembler.build(
                            MemoryBuildRequest(
                                rangeStartMs = rangeStart,
                                rangeEndMs = effectiveEnd
                            )
                        )
                    }.onFailure {
                        Log.w(TAG, "memory build failed", it)
                    }.getOrNull()
                    break
                }
                if (attempt < MEMORY_BUILD_MAX_WAIT_ATTEMPTS - 1) {
                    delay(MEMORY_BUILD_WAIT_MS)
                }
            }

            result?.outputFile?.let {
                SleepRuntimeStatusStore.setLastMemoryBuiltAt(System.currentTimeMillis())
                Log.i(
                    TAG,
                    "memory generated file=${it.name} videos=${result.usedVideoFiles} audios=${result.usedAudioFiles}"
                )
            }
            if (result == null) {
                Log.w(
                    TAG,
                    "memory build skipped: no eligible closed collect range start=$rangeStart end=$lastEffectiveEnd"
                )
            } else if (result.outputFile == null) {
                Log.w(
                    TAG,
                    "memory build produced no file: reason=${result.skippedReason} videos=${result.usedVideoFiles} audios=${result.usedAudioFiles} start=$rangeStart end=$lastEffectiveEnd"
                )
            }

            wakeMemoryManager.markMemoryBuildFinished()
            SleepRuntimeStatusStore.setMemoryBuildInProgress(false)
        }
    }

    private fun resolveMemoryRangeEndMs(triggerEndMs: Long): Long {
        val closedVideo = CollectClosedFileBus.latest(CollectFileType.VIDEO)
            ?: return triggerEndMs
        val closedEndMs = closedVideo.startMs + CLOSED_MINUTE_DURATION_MS
        return minOf(triggerEndMs, closedEndMs)
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
        private const val CLOSED_MINUTE_DURATION_MS = 59_999L
        private const val MEMORY_BUILD_WAIT_MS = 15_000L
        private const val MEMORY_BUILD_MAX_WAIT_ATTEMPTS = 4
        private const val MIC_SUPPRESS_AFTER_LULLABY_MS = 20_000L
        private const val MIC_SUPPRESS_AFTER_SOOTHE_MS = 60_000L
    }
}
