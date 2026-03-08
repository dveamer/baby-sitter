package com.dveamer.babysitter.billing

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.BillingClient
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private const val MEMORY_DOWNLOAD_ENTITLEMENT_DATASTORE_NAME = "memory_download_entitlement"

private val Context.memoryDownloadEntitlementDataStore by preferencesDataStore(
    name = MEMORY_DOWNLOAD_ENTITLEMENT_DATASTORE_NAME
)

data class MemoryDownloadEntitlement(
    val productId: String = "",
    val productType: String = "",
    val purchaseToken: String = "",
    val purchasedAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long? = null
) {
    fun isActive(nowMs: Long): Boolean {
        return purchaseToken.isNotBlank() && (expiresAtEpochMs == null || expiresAtEpochMs > nowMs)
    }

    fun isOwned(): Boolean = purchaseToken.isNotBlank()

    fun isSubscription(): Boolean = productType == BillingClient.ProductType.SUBS
}

class MemoryDownloadEntitlementRepository(
    context: Context,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(MemoryDownloadEntitlement())
    val state: StateFlow<MemoryDownloadEntitlement> = mutableState

    suspend fun initialize() {
        mutableState.value = readPreferences().toEntitlement()
    }

    suspend fun storeOneTimePurchase(
        productId: String,
        purchaseToken: String,
        purchasedAtEpochMs: Long
    ): MemoryDownloadEntitlement {
        val entitlement = MemoryDownloadEntitlement(
            productId = productId,
            productType = BillingClient.ProductType.INAPP,
            purchaseToken = purchaseToken,
            purchasedAtEpochMs = purchasedAtEpochMs,
            expiresAtEpochMs = calculateMemoryDownloadPassExpiryAt(
                purchasedAtEpochMs = purchasedAtEpochMs,
                zoneId = zoneIdProvider()
            )
        )
        return write(entitlement)
    }

    suspend fun storeSubscriptionPurchase(
        productId: String,
        purchaseToken: String,
        purchasedAtEpochMs: Long
    ): MemoryDownloadEntitlement {
        return write(
            MemoryDownloadEntitlement(
                productId = productId,
                productType = BillingClient.ProductType.SUBS,
                purchaseToken = purchaseToken,
                purchasedAtEpochMs = purchasedAtEpochMs,
                expiresAtEpochMs = null
            )
        )
    }

    suspend fun clear() {
        write(MemoryDownloadEntitlement())
    }

    private suspend fun write(entitlement: MemoryDownloadEntitlement): MemoryDownloadEntitlement {
        appContext.memoryDownloadEntitlementDataStore.edit { prefs ->
            prefs[Keys.PRODUCT_ID] = entitlement.productId
            prefs[Keys.PRODUCT_TYPE] = entitlement.productType
            prefs[Keys.PURCHASE_TOKEN] = entitlement.purchaseToken
            prefs[Keys.PURCHASED_AT_EPOCH_MS] = entitlement.purchasedAtEpochMs
            val expiresAtEpochMs = entitlement.expiresAtEpochMs
            if (expiresAtEpochMs != null) {
                prefs[Keys.EXPIRES_AT_EPOCH_MS] = expiresAtEpochMs
            } else {
                prefs.remove(Keys.EXPIRES_AT_EPOCH_MS)
            }
        }
        mutableState.value = entitlement
        return entitlement
    }

    private suspend fun readPreferences(): Preferences {
        return appContext.memoryDownloadEntitlementDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
    }

    private fun Preferences.toEntitlement(): MemoryDownloadEntitlement {
        return MemoryDownloadEntitlement(
            productId = this[Keys.PRODUCT_ID] ?: "",
            productType = this[Keys.PRODUCT_TYPE] ?: "",
            purchaseToken = this[Keys.PURCHASE_TOKEN] ?: "",
            purchasedAtEpochMs = this[Keys.PURCHASED_AT_EPOCH_MS] ?: 0L,
            expiresAtEpochMs = this[Keys.EXPIRES_AT_EPOCH_MS]
        )
    }

    private object Keys {
        val PRODUCT_ID = stringPreferencesKey("product_id")
        val PRODUCT_TYPE = stringPreferencesKey("product_type")
        val PURCHASE_TOKEN = stringPreferencesKey("purchase_token")
        val PURCHASED_AT_EPOCH_MS = longPreferencesKey("purchased_at_epoch_ms")
        val EXPIRES_AT_EPOCH_MS = longPreferencesKey("expires_at_epoch_ms")
    }
}
