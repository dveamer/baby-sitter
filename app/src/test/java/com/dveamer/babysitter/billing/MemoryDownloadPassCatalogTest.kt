package com.dveamer.babysitter.billing

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDownloadPassCatalogTest {

    @Test
    fun `user visible products exclude unpublished one time IAP`() {
        val visibleProductIds = MemoryDownloadPassCatalog.USER_VISIBLE_PRODUCTS.map { it.productId }

        assertFalse(
            visibleProductIds.contains(MemoryDownloadPassCatalog.MONTH_MEMORY_DOWNLOAD_10_PER_DAY)
        )
        assertTrue(
            visibleProductIds.contains(
                MemoryDownloadPassCatalog.SUBSCRIPTION_MONTH_MEMORY_DOWNLOAD_10_PER_DAY
            )
        )
    }

    @Test
    fun `expiry is one calendar month after purchase time`() {
        val purchasedAtMs = Instant.parse("2026-01-31T15:45:00Z").toEpochMilli()

        val expiresAtMs = calculateMemoryDownloadPassExpiryAt(
            purchasedAtEpochMs = purchasedAtMs,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(
            Instant.parse("2026-02-28T15:45:00Z").toEpochMilli(),
            expiresAtMs
        )
    }
}
