package com.dveamer.babysitter.collect

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class WebPreviewDemandSnapshot(
    val subscriberCount: Int,
    val lastSubscriberDisconnectedAtMs: Long?
) {
    val active: Boolean
        get() = subscriberCount > 0
}

/**
 * Collect 입력 소스의 활성 상태를 단일 지점에서 관리하는 코디네이터.
 * 현재 버전은 경로 준비와 활성 조건 정합성 유지에 집중한다.
 */
class CollectRecorderCoordinator(
    private val ensureDirectories: () -> Unit
) {
    @Volatile
    private var cameraInputEnabled: Boolean = false

    @Volatile
    private var audioInputEnabled: Boolean = false

    private val webPreviewSubscriberCount = AtomicInteger(0)
    private val lastWebPreviewDisconnectedAtMs = AtomicLong(0L)

    fun start() {
        ensureDirectories()
    }

    fun stop() {
        cameraInputEnabled = false
        audioInputEnabled = false
    }

    fun updateInputs(
        cameraMonitoringEnabled: Boolean,
        webCameraEnabled: Boolean,
        soundMonitoringEnabled: Boolean
    ) {
        val nextCameraEnabled = cameraMonitoringEnabled || webCameraEnabled
        val nextAudioEnabled = soundMonitoringEnabled

        if (nextCameraEnabled != cameraInputEnabled || nextAudioEnabled != audioInputEnabled) {
            logDebug("collect inputs updated camera=$nextCameraEnabled audio=$nextAudioEnabled")
        }

        cameraInputEnabled = nextCameraEnabled
        audioInputEnabled = nextAudioEnabled
    }

    fun isCameraInputEnabled(): Boolean = cameraInputEnabled

    fun isAudioInputEnabled(): Boolean = audioInputEnabled

    fun onWebPreviewSubscriberConnected(): WebPreviewDemandSnapshot {
        val next = webPreviewSubscriberCount.incrementAndGet()
        logDebug("web preview subscriber connected count=$next")
        return webPreviewDemandSnapshot()
    }

    fun onWebPreviewSubscriberDisconnected(
        disconnectedAtMs: Long = System.currentTimeMillis()
    ): WebPreviewDemandSnapshot {
        while (true) {
            val current = webPreviewSubscriberCount.get()
            if (current <= 0) {
                logWarn("web preview subscriber disconnect underflow")
                return webPreviewDemandSnapshot()
            }
            val next = current - 1
            if (webPreviewSubscriberCount.compareAndSet(current, next)) {
                if (next == 0) {
                    lastWebPreviewDisconnectedAtMs.set(disconnectedAtMs)
                }
                logDebug("web preview subscriber disconnected count=$next")
                return webPreviewDemandSnapshot()
            }
        }
    }

    fun isWebPreviewDemandActive(): Boolean = webPreviewSubscriberCount.get() > 0

    fun webPreviewDemandSnapshot(): WebPreviewDemandSnapshot {
        val lastDisconnectedAtMs = lastWebPreviewDisconnectedAtMs.get()
        return WebPreviewDemandSnapshot(
            subscriberCount = webPreviewSubscriberCount.get(),
            lastSubscriberDisconnectedAtMs = lastDisconnectedAtMs.takeIf { it > 0L }
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
