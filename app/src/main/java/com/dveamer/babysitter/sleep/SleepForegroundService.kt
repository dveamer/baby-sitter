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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class MusicPlaybackOwner {
    AWAKE,
    MICROPHONE_DIRECT
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
        val microphoneMusicController = MicrophoneMusicController()
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

                if (signal.kind == MonitorKind.MICROPHONE && musicSoothingListener != null) {
                    val directMusicActive = signal.active && !shouldSuppressMic
                    when (microphoneMusicController.onSignal(directMusicActive)) {
                        MicrophoneMusicAction.START -> {
                            Log.d(TAG, "microphone music start signal=${signal.monitorId}")
                            wakeMemoryManager.onAwakeSignal(now)
                            val result = musicSoothingListener.soothe(
                                SootheRequest(
                                    awakeSinceMs = now,
                                    reason = "${signal.monitorId}:direct",
                                    requestedAtMs = now
                                )
                            )
                            if (result == SootheResult.STARTED) {
                                musicPlaybackOwner = MusicPlaybackOwner.MICROPHONE_DIRECT
                            }
                        }

                        MicrophoneMusicAction.NONE -> Unit
                    }
                }
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
                            "wake memory trigger awakeStartedAt=${trigger.awakeStartedAt} sleepStableEndedAt=${trigger.sleepStableEndedAt}"
                        )
                        launchMemoryBuild(trigger)
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                musicSoothingListener?.stop("engine_finished")
            }
            microphoneMusicController.reset()
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
            val result = container.memoryBuildCoordinator.buildWakeMemory(trigger)

            result.outputFile?.let {
                Log.i(
                    TAG,
                    "memory generated file=${it.name} videos=${result.usedVideoFiles} audios=${result.usedAudioFiles}"
                )
            }
            if (result.outputFile == null) {
                Log.w(
                    TAG,
                    "memory build produced no file: reason=${result.skippedReason} videos=${result.usedVideoFiles} audios=${result.usedAudioFiles} start=${result.rangeStartMs} end=${result.effectiveRangeEndMs ?: result.requestedRangeEndMs}"
                )
            }

            wakeMemoryManager.markMemoryBuildFinished()
        }
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
