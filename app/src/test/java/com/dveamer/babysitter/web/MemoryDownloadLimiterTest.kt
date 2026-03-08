package com.dveamer.babysitter.web

import com.dveamer.babysitter.billing.MemoryDownloadPassCatalog
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDownloadLimiterTest {

    @Test
    fun `default limit allows one download per day and resets next day`() = runBlocking {
        val store = InMemoryQuotaStore()
        var nowMs = Instant.parse("2026-03-08T01:00:00Z").toEpochMilli()
        val limiter = MemoryDownloadLimiter(
            quotaStore = store,
            limitProvider = StaticMemoryDownloadLimitProvider(),
            clock = { nowMs },
            zoneIdProvider = { ZoneId.of("UTC") }
        )

        val initial = limiter.currentSnapshot()
        assertEquals(1, initial.dailyLimit)
        assertEquals(0, initial.downloadsUsedToday)
        assertEquals(1, initial.downloadsRemainingToday)
        assertTrue(initial.downloadAvailableToday)

        val first = limiter.tryConsume()
        assertTrue(first.allowed)
        assertEquals(1, first.snapshot.downloadsUsedToday)
        assertEquals(0, first.snapshot.downloadsRemainingToday)
        assertFalse(first.snapshot.downloadAvailableToday)

        val second = limiter.tryConsume()
        assertFalse(second.allowed)
        assertEquals(1, second.snapshot.downloadsUsedToday)
        assertEquals(0, second.snapshot.downloadsRemainingToday)

        nowMs = Instant.parse("2026-03-09T01:00:00Z").toEpochMilli()

        val nextDay = limiter.currentSnapshot()
        assertEquals(0, nextDay.downloadsUsedToday)
        assertEquals(1, nextDay.downloadsRemainingToday)
        assertTrue(nextDay.downloadAvailableToday)
    }

    @Test
    fun `daily limit can change without rebuilding the limiter`() = runBlocking {
        val store = InMemoryQuotaStore()
        var limit = 1
        val nowMs = Instant.parse("2026-03-08T01:00:00Z").toEpochMilli()
        val limiter = MemoryDownloadLimiter(
            quotaStore = store,
            limitProvider = MemoryDownloadLimitProvider {
                MemoryDownloadAccessPolicy(dailyLimit = limit)
            },
            clock = { nowMs },
            zoneIdProvider = { ZoneId.of("UTC") }
        )

        assertTrue(limiter.tryConsume().allowed)
        assertFalse(limiter.tryConsume().allowed)

        limit = 2

        val updatedSnapshot = limiter.currentSnapshot()
        assertEquals(2, updatedSnapshot.dailyLimit)
        assertEquals(1, updatedSnapshot.downloadsUsedToday)
        assertEquals(1, updatedSnapshot.downloadsRemainingToday)
        assertTrue(updatedSnapshot.downloadAvailableToday)

        val secondAllowed = limiter.tryConsume()
        assertTrue(secondAllowed.allowed)
        assertEquals(2, secondAllowed.snapshot.downloadsUsedToday)
        assertEquals(0, secondAllowed.snapshot.downloadsRemainingToday)
    }

    @Test
    fun `active purchase expands limit and exposes benefit metadata`() = runBlocking {
        val store = InMemoryQuotaStore()
        val nowMs = Instant.parse("2026-03-08T01:00:00Z").toEpochMilli()
        val expiresAtMs = Instant.parse("2026-04-08T01:00:00Z").toEpochMilli()
        val limiter = MemoryDownloadLimiter(
            quotaStore = store,
            limitProvider = MemoryDownloadLimitProvider {
                MemoryDownloadAccessPolicy(
                    dailyLimit = MemoryDownloadPassCatalog.EXPANDED_DAILY_LIMIT,
                    benefitProductId = MemoryDownloadPassCatalog.MONTH_MEMORY_DOWNLOAD_10_PER_DAY,
                    benefitExpiresAtEpochMs = expiresAtMs
                )
            },
            clock = { nowMs },
            zoneIdProvider = { ZoneId.of("UTC") }
        )

        val snapshot = limiter.currentSnapshot()

        assertEquals(MemoryDownloadPassCatalog.EXPANDED_DAILY_LIMIT, snapshot.dailyLimit)
        assertEquals(
            MemoryDownloadPassCatalog.MONTH_MEMORY_DOWNLOAD_10_PER_DAY,
            snapshot.benefitProductId
        )
        assertEquals(expiresAtMs, snapshot.benefitExpiresAtEpochMs)
        assertEquals(MemoryDownloadPassCatalog.EXPANDED_DAILY_LIMIT, snapshot.downloadsRemainingToday)
    }

    @Test
    fun `active subscription expands limit even without a known expiry`() = runBlocking {
        val store = InMemoryQuotaStore()
        val nowMs = Instant.parse("2026-03-08T01:00:00Z").toEpochMilli()
        val limiter = MemoryDownloadLimiter(
            quotaStore = store,
            limitProvider = MemoryDownloadLimitProvider {
                MemoryDownloadAccessPolicy(
                    dailyLimit = MemoryDownloadPassCatalog.EXPANDED_DAILY_LIMIT,
                    benefitProductId = MemoryDownloadPassCatalog.SUBSCRIPTION_MONTH_MEMORY_DOWNLOAD_10_PER_DAY,
                    benefitExpiresAtEpochMs = null
                )
            },
            clock = { nowMs },
            zoneIdProvider = { ZoneId.of("UTC") }
        )

        val snapshot = limiter.currentSnapshot()

        assertEquals(MemoryDownloadPassCatalog.EXPANDED_DAILY_LIMIT, snapshot.dailyLimit)
        assertEquals(
            MemoryDownloadPassCatalog.SUBSCRIPTION_MONTH_MEMORY_DOWNLOAD_10_PER_DAY,
            snapshot.benefitProductId
        )
        assertEquals(null, snapshot.benefitExpiresAtEpochMs)
        assertTrue(snapshot.downloadAvailableToday)
    }

    private class InMemoryQuotaStore : MemoryDownloadQuotaStore {
        private var record = MemoryDownloadQuotaRecord()

        override suspend fun read(): MemoryDownloadQuotaRecord = record

        override suspend fun update(
            transform: (MemoryDownloadQuotaRecord) -> MemoryDownloadQuotaRecord
        ): MemoryDownloadQuotaRecord {
            record = transform(record)
            return record
        }
    }
}
