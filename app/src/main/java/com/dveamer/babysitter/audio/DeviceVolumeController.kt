package com.dveamer.babysitter.audio

import android.content.Context
import android.media.AudioManager

data class DeviceVolumeSnapshot(
    val currentLevel: Int,
    val maxLevel: Int
) {
    val percent: Int
        get() = DeviceVolumeMath.levelToPercent(currentLevel, maxLevel)
}

object DeviceVolumeMath {
    fun percentToLevel(percent: Int, maxLevel: Int): Int {
        if (maxLevel <= 0) return 0
        val clampedPercent = percent.coerceIn(0, 100)
        return ((maxLevel * clampedPercent) / 100.0)
            .toInt()
            .coerceIn(0, maxLevel)
    }

    fun levelToPercent(level: Int, maxLevel: Int): Int {
        if (maxLevel <= 0) return 0
        val clampedLevel = level.coerceIn(0, maxLevel)
        return ((clampedLevel * 100.0) / maxLevel)
            .toInt()
            .coerceIn(0, 100)
    }
}

class DeviceVolumeController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    fun currentSnapshot(): DeviceVolumeSnapshot {
        val manager = audioManager ?: return DeviceVolumeSnapshot(currentLevel = 0, maxLevel = 0)
        val maxLevel = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
        val currentLevel = manager
            .getStreamVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(0, maxLevel.coerceAtLeast(0))
        return DeviceVolumeSnapshot(currentLevel = currentLevel, maxLevel = maxLevel)
    }

    fun setMusicVolumePercent(percent: Int): DeviceVolumeSnapshot {
        val manager = audioManager ?: return DeviceVolumeSnapshot(currentLevel = 0, maxLevel = 0)
        val maxLevel = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
        val level = DeviceVolumeMath.percentToLevel(percent, maxLevel)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        return currentSnapshot()
    }
}
