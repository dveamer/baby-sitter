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
import com.dveamer.babysitter.monitor.CameraMonitor
import com.dveamer.babysitter.monitor.MicrophoneMonitor
import com.dveamer.babysitter.monitor.Monitor
import com.dveamer.babysitter.settings.MotionSensitivity
import com.dveamer.babysitter.settings.SoundSensitivity
import com.dveamer.babysitter.soothing.IotSoothingListener
import com.dveamer.babysitter.soothing.MusicSoothingListener
import com.dveamer.babysitter.soothing.SequentialSoothingCoordinator
import com.dveamer.babysitter.soothing.SootheRequest
import com.dveamer.babysitter.soothing.SoothingListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SleepForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startStopLock = Mutex()

    private var monitoringJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val container by lazy {
        (application as BabySitterApplication).container
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
                startForeground(NOTIFICATION_ID, buildNotification())
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
            acquireWakeLock()
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
        releaseWakeLock()
    }

    private suspend fun runEngine() {
        val settings = container.settingsRepository.state.value

        val monitors = buildList<Monitor> {
            if (settings.soundMonitoringEnabled) {
                val soundThreshold = when (settings.soundSensitivity) {
                    SoundSensitivity.HIGH -> 650.0
                    SoundSensitivity.MEDIUM -> 900.0
                    SoundSensitivity.LOW -> 1300.0
                }
                add(MicrophoneMonitor(serviceScope, amplitudeThreshold = soundThreshold))
            }
            if (settings.cameraMonitoringEnabled) {
                val (diffThreshold, minChangedRatio) = when (settings.motionSensitivity) {
                    MotionSensitivity.HIGH -> 14 to 0.012
                    MotionSensitivity.MEDIUM -> 20 to 0.03
                    MotionSensitivity.LOW -> 28 to 0.06
                }
                add(
                    CameraMonitor(
                        scope = serviceScope,
                        appContext = this@SleepForegroundService,
                        diffThreshold = diffThreshold,
                        minChangedRatio = minChangedRatio
                    )
                )
            }
        }

        val soothingListeners = buildList<SoothingListener> {
            if (settings.soothingMusicEnabled) {
                add(MusicSoothingListener(this@SleepForegroundService, container.settingsRepository))
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
            merge(*monitors.map { it.signals }.toTypedArray()).collect { signal ->
                val now = System.currentTimeMillis()
                val awake = detector.onSignal(signal, now)

                if (awake.isAwake && awake.awakeSinceMs != null) {
                    Log.d("merge", "awake : $awake")
                    soothingCoordinator.soothe(
                        SootheRequest(
                            awakeSinceMs = awake.awakeSinceMs,
                            reason = awake.reason,
                            requestedAtMs = now
                        )
                    )
                    alertController.onAwake(
                        awakeSinceMs = awake.awakeSinceMs,
                        nowMs = now,
                        settings = container.settingsRepository.state.value
                    )
                } else {
                    alertController.onSleep()
                }
            }
        } finally {
            monitors.forEach { it.stop() }
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

        private const val CHANNEL_ID = "sleep_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
