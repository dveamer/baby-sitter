package com.dveamer.babysitter.collect

import android.util.Log

/**
 * Collect 입력 소스의 활성 상태를 단일 지점에서 관리하는 코디네이터.
 * 현재 버전은 경로 준비와 활성 조건 정합성 유지에 집중한다.
 */
class CollectRecorderCoordinator(
    private val paths: CollectStoragePaths
) {
    @Volatile
    private var cameraInputEnabled: Boolean = false

    @Volatile
    private var audioInputEnabled: Boolean = false

    fun start() {
        paths.ensureDirectories()
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
            Log.d(
                TAG,
                "collect inputs updated camera=$nextCameraEnabled audio=$nextAudioEnabled"
            )
        }

        cameraInputEnabled = nextCameraEnabled
        audioInputEnabled = nextAudioEnabled
    }

    fun isCameraInputEnabled(): Boolean = cameraInputEnabled

    fun isAudioInputEnabled(): Boolean = audioInputEnabled

    private companion object {
        const val TAG = "CollectRecorderCoord"
    }
}
