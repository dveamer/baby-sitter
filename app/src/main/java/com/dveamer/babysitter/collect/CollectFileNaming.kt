package com.dveamer.babysitter.collect

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CollectFileNaming {
    private val minuteFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    private val videoRegex = Regex("^collect_(\\d{8}_\\d{4})\\.mp4$")
    private val audioRegex = Regex("^collect_(\\d{8}_\\d{4})\\.m4a$")
    private val memoryRegex = Regex("^memory_(\\d{8}_\\d{4})\\.mp4$")

    fun collectVideoFileName(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return "collect_${formatMinute(epochMs, zoneId)}.mp4"
    }

    fun collectAudioFileName(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return "collect_${formatMinute(epochMs, zoneId)}.m4a"
    }

    fun memoryVideoFileName(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return "memory_${formatMinute(epochMs, zoneId)}.mp4"
    }

    fun parseCollectVideoStartMs(file: File, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val token = videoRegex.matchEntire(file.name)?.groupValues?.getOrNull(1) ?: return null
        return parseMinuteToken(token, zoneId)
    }

    fun parseCollectAudioStartMs(file: File, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val token = audioRegex.matchEntire(file.name)?.groupValues?.getOrNull(1) ?: return null
        return parseMinuteToken(token, zoneId)
    }

    fun parseMemoryStartMs(file: File, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val token = memoryRegex.matchEntire(file.name)?.groupValues?.getOrNull(1) ?: return null
        return parseMinuteToken(token, zoneId)
    }

    fun minuteFloor(epochMs: Long): Long = (epochMs / 60_000L) * 60_000L

    private fun formatMinute(epochMs: Long, zoneId: ZoneId): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(minuteFloor(epochMs)), zoneId)
            .format(minuteFormatter)
    }

    private fun parseMinuteToken(token: String, zoneId: ZoneId): Long? {
        return runCatching {
            LocalDateTime.parse(token, minuteFormatter)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
