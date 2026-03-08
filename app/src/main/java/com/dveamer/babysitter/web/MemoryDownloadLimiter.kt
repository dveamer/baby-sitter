package com.dveamer.babysitter.web

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MEMORY_DOWNLOAD_DATASTORE_NAME = "memory_download_quota"

private val Context.memoryDownloadDataStore by preferencesDataStore(name = MEMORY_DOWNLOAD_DATASTORE_NAME)

data class MemoryDownloadQuotaSnapshot(
    val dayKey: String,
    val dailyLimit: Int,
    val downloadsUsedToday: Int
) {
    val downloadsRemainingToday: Int
        get() = (dailyLimit - downloadsUsedToday).coerceAtLeast(0)

    val downloadAvailableToday: Boolean
        get() = downloadsRemainingToday > 0
}

data class MemoryDownloadQuotaDecision(
    val allowed: Boolean,
    val snapshot: MemoryDownloadQuotaSnapshot
)

data class MemoryDownloadQuotaRecord(
    val dayKey: String = "",
    val downloadsUsedToday: Int = 0
)

fun interface MemoryDownloadLimitProvider {
    fun dailyLimit(): Int
}

class StaticMemoryDownloadLimitProvider(
    private val limit: Int = DEFAULT_DAILY_MEMORY_DOWNLOAD_LIMIT
) : MemoryDownloadLimitProvider {
    override fun dailyLimit(): Int = limit

    companion object {
        const val DEFAULT_DAILY_MEMORY_DOWNLOAD_LIMIT = 1
    }
}

interface MemoryDownloadQuotaStore {
    suspend fun read(): MemoryDownloadQuotaRecord

    suspend fun update(transform: (MemoryDownloadQuotaRecord) -> MemoryDownloadQuotaRecord): MemoryDownloadQuotaRecord
}

class DataStoreMemoryDownloadQuotaStore(
    context: Context
) : MemoryDownloadQuotaStore {
    private val appContext = context.applicationContext

    override suspend fun read(): MemoryDownloadQuotaRecord {
        return readPreferences().toRecord()
    }

    override suspend fun update(
        transform: (MemoryDownloadQuotaRecord) -> MemoryDownloadQuotaRecord
    ): MemoryDownloadQuotaRecord {
        var updated = MemoryDownloadQuotaRecord()
        appContext.memoryDownloadDataStore.edit { prefs ->
            updated = transform(prefs.toRecord())
            prefs[Keys.DAY_KEY] = updated.dayKey
            prefs[Keys.DOWNLOADS_USED_TODAY] = updated.downloadsUsedToday
        }
        return updated
    }

    private suspend fun readPreferences(): Preferences {
        return appContext.memoryDownloadDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
    }

    private fun Preferences.toRecord(): MemoryDownloadQuotaRecord {
        return MemoryDownloadQuotaRecord(
            dayKey = this[Keys.DAY_KEY] ?: "",
            downloadsUsedToday = (this[Keys.DOWNLOADS_USED_TODAY] ?: 0).coerceAtLeast(0)
        )
    }

    private object Keys {
        val DAY_KEY = stringPreferencesKey("day_key")
        val DOWNLOADS_USED_TODAY = intPreferencesKey("downloads_used_today")
    }
}

class MemoryDownloadLimiter(
    private val quotaStore: MemoryDownloadQuotaStore,
    private val limitProvider: MemoryDownloadLimitProvider = StaticMemoryDownloadLimitProvider(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val mutex = Mutex()

    suspend fun currentSnapshot(nowMs: Long = clock()): MemoryDownloadQuotaSnapshot {
        return mutex.withLock {
            buildSnapshot(quotaStore.read(), nowMs)
        }
    }

    suspend fun tryConsume(nowMs: Long = clock()): MemoryDownloadQuotaDecision {
        return mutex.withLock {
            val snapshot = buildSnapshot(quotaStore.read(), nowMs)
            if (!snapshot.downloadAvailableToday) {
                return@withLock MemoryDownloadQuotaDecision(
                    allowed = false,
                    snapshot = snapshot
                )
            }

            val updated = quotaStore.update { current ->
                val normalized = normalizeRecord(current, snapshot.dayKey)
                normalized.copy(downloadsUsedToday = normalized.downloadsUsedToday + 1)
            }
            MemoryDownloadQuotaDecision(
                allowed = true,
                snapshot = buildSnapshot(updated, nowMs)
            )
        }
    }

    private fun buildSnapshot(
        record: MemoryDownloadQuotaRecord,
        nowMs: Long
    ): MemoryDownloadQuotaSnapshot {
        val dayKey = resolveDayKey(nowMs)
        val normalized = normalizeRecord(record, dayKey)
        val dailyLimit = limitProvider.dailyLimit().coerceAtLeast(0)
        return MemoryDownloadQuotaSnapshot(
            dayKey = dayKey,
            dailyLimit = dailyLimit,
            downloadsUsedToday = normalized.downloadsUsedToday
        )
    }

    private fun normalizeRecord(
        record: MemoryDownloadQuotaRecord,
        dayKey: String
    ): MemoryDownloadQuotaRecord {
        return if (record.dayKey == dayKey) {
            record.copy(downloadsUsedToday = record.downloadsUsedToday.coerceAtLeast(0))
        } else {
            MemoryDownloadQuotaRecord(dayKey = dayKey, downloadsUsedToday = 0)
        }
    }

    private fun resolveDayKey(nowMs: Long): String {
        val localDate = Instant.ofEpochMilli(nowMs).atZone(zoneIdProvider()).toLocalDate()
        return localDate.toString()
    }
}
