package com.dveamer.babysitter.billing

import com.android.billingclient.api.BillingClient
import java.time.Instant
import java.time.ZoneId

data class MemoryDownloadProductDefinition(
    val productId: String,
    val productType: String,
    val defaultName: String
)

object MemoryDownloadPassCatalog {
    const val MONTH_MEMORY_DOWNLOAD_10_PER_DAY = "month_memory_download_10_per_day"
    const val SUBSCRIPTION_MONTH_MEMORY_DOWNLOAD_10_PER_DAY =
        "subscription_month_memory_download_10_per_day"
    const val DEFAULT_DAILY_LIMIT = 1
    const val EXPANDED_DAILY_LIMIT = 10

    val ONE_TIME_PRODUCT = MemoryDownloadProductDefinition(
        productId = MONTH_MEMORY_DOWNLOAD_10_PER_DAY,
        productType = BillingClient.ProductType.INAPP,
        defaultName = "Memory Download Pass"
    )

    val SUBSCRIPTION_PRODUCT = MemoryDownloadProductDefinition(
        productId = SUBSCRIPTION_MONTH_MEMORY_DOWNLOAD_10_PER_DAY,
        productType = BillingClient.ProductType.SUBS,
        defaultName = "Memory Download Subscription"
    )

    val PRODUCTS = listOf(ONE_TIME_PRODUCT, SUBSCRIPTION_PRODUCT)

    fun find(productId: String): MemoryDownloadProductDefinition? {
        return PRODUCTS.firstOrNull { it.productId == productId }
    }
}

fun calculateMemoryDownloadPassExpiryAt(
    purchasedAtEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    return Instant.ofEpochMilli(purchasedAtEpochMs)
        .atZone(zoneId)
        .plusMonths(1)
        .toInstant()
        .toEpochMilli()
}
