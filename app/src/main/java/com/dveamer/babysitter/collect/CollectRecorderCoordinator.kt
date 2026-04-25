package com.dveamer.babysitter.collect

import android.util.Log

data class WebPreviewDemandSnapshot(
    val subscriberCount: Int,
    val lastSubscriberDisconnectedAtMs: Long?
) {
    val active: Boolean
        get() = subscriberCount > 0
}

data class CollectInputPolicy(
    val cameraInputEnabled: Boolean = false,
    val audioInputEnabled: Boolean = false,
    val motionAnalysisEnabled: Boolean = false,
    val webPreviewAllowed: Boolean = false
)

private data class CollectInputSettings(
    val sleepEnabled: Boolean = false,
    val cameraMonitoringEnabled: Boolean = false,
    val webCameraEnabled: Boolean = false,
    val soundMonitoringEnabled: Boolean = false
)

/**
 * Collect 입력 소스의 활성 상태를 단일 지점에서 관리하는 코디네이터.
 * 현재 버전은 경로 준비와 활성 조건 정합성 유지에 집중한다.
 */
class CollectRecorderCoordinator(
    private val ensureDirectories: () -> Unit,
    private val onWebPreviewDemandChanged: (() -> Unit)? = null
) {
    private val lock = Any()

    @Volatile
    private var inputPolicy: CollectInputPolicy = CollectInputPolicy()

    private var inputSettings: CollectInputSettings = CollectInputSettings()

    private var webPreviewSubscriberCount: Int = 0
    private var lastWebPreviewDisconnectedAtMs: Long? = null

    fun start() {
        ensureDirectories()
    }

    fun stop() {
        synchronized(lock) {
            inputPolicy = CollectInputPolicy()
        }
    }

    fun updateInputs(
        sleepEnabled: Boolean,
        cameraMonitoringEnabled: Boolean,
        webCameraEnabled: Boolean,
        soundMonitoringEnabled: Boolean
    ): CollectInputPolicy {
        synchronized(lock) {
            inputSettings = CollectInputSettings(
                sleepEnabled = sleepEnabled,
                cameraMonitoringEnabled = cameraMonitoringEnabled,
                webCameraEnabled = webCameraEnabled,
                soundMonitoringEnabled = soundMonitoringEnabled
            )
            applyInputPolicyLocked(
                resolveInputPolicyLocked(
                    settings = inputSettings,
                    previewDemandActive = webPreviewSubscriberCount > 0
                )
            )
            return inputPolicy
        }
    }

    fun resolveInputPolicy(
        sleepEnabled: Boolean,
        cameraMonitoringEnabled: Boolean,
        webCameraEnabled: Boolean,
        soundMonitoringEnabled: Boolean
    ): CollectInputPolicy {
        synchronized(lock) {
            return resolveInputPolicyLocked(
                settings = CollectInputSettings(
                    sleepEnabled = sleepEnabled,
                    cameraMonitoringEnabled = cameraMonitoringEnabled,
                    webCameraEnabled = webCameraEnabled,
                    soundMonitoringEnabled = soundMonitoringEnabled
                ),
                previewDemandActive = webPreviewSubscriberCount > 0
            )
        }
    }

    fun currentPolicy(): CollectInputPolicy = inputPolicy

    fun onWebPreviewSubscriberConnected(): WebPreviewDemandSnapshot {
        val shouldNotify = synchronized(lock) {
            webPreviewSubscriberCount += 1
            val next = webPreviewSubscriberCount
            logDebug("web preview subscriber connected count=$next")
            val becameActive = next == 1
            if (becameActive) {
                applyInputPolicyLocked(
                    resolveInputPolicyLocked(
                        settings = inputSettings,
                        previewDemandActive = true
                    )
                )
            }
            becameActive
        }
        if (shouldNotify) {
            onWebPreviewDemandChanged?.invoke()
        }
        return webPreviewDemandSnapshot()
    }

    fun onWebPreviewSubscriberDisconnected(
        disconnectedAtMs: Long = System.currentTimeMillis()
    ): WebPreviewDemandSnapshot {
        val shouldNotify = synchronized(lock) {
            val current = webPreviewSubscriberCount
            if (current <= 0) {
                logWarn("web preview subscriber disconnect underflow")
                return@synchronized false
            }
            val next = current - 1
            webPreviewSubscriberCount = next
            if (next == 0) {
                lastWebPreviewDisconnectedAtMs = disconnectedAtMs
                applyInputPolicyLocked(
                    resolveInputPolicyLocked(
                        settings = inputSettings,
                        previewDemandActive = false
                    )
                )
            }
            logDebug("web preview subscriber disconnected count=$next")
            next == 0
        }
        if (shouldNotify) {
            onWebPreviewDemandChanged?.invoke()
        }
        return webPreviewDemandSnapshot()
    }

    fun isWebPreviewDemandActive(): Boolean = synchronized(lock) {
        webPreviewSubscriberCount > 0
    }

    fun webPreviewDemandSnapshot(): WebPreviewDemandSnapshot {
        synchronized(lock) {
            return WebPreviewDemandSnapshot(
                subscriberCount = webPreviewSubscriberCount,
                lastSubscriberDisconnectedAtMs = lastWebPreviewDisconnectedAtMs
            )
        }
    }

    private fun applyInputPolicyLocked(nextPolicy: CollectInputPolicy) {
        if (nextPolicy != inputPolicy) {
            logDebug(
                "collect inputs updated " +
                    "camera=${nextPolicy.cameraInputEnabled} " +
                    "audio=${nextPolicy.audioInputEnabled} " +
                    "motion=${nextPolicy.motionAnalysisEnabled} " +
                    "preview=${nextPolicy.webPreviewAllowed}"
            )
        }
        inputPolicy = nextPolicy
    }

    private fun resolveInputPolicyLocked(
        settings: CollectInputSettings,
        previewDemandActive: Boolean
    ): CollectInputPolicy {
        val motionAnalysisEnabled = settings.sleepEnabled && settings.cameraMonitoringEnabled
        val webPreviewAllowed = settings.webCameraEnabled && previewDemandActive
        return CollectInputPolicy(
            cameraInputEnabled = motionAnalysisEnabled || webPreviewAllowed,
            audioInputEnabled = settings.sleepEnabled && settings.soundMonitoringEnabled,
            motionAnalysisEnabled = motionAnalysisEnabled,
            webPreviewAllowed = webPreviewAllowed
        )
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private companion object {
        const val TAG = "CollectRecorderCoord"
    }
}
