package com.dveamer.babysitter.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class MemoryDownloadProductUiState(
    val productId: String,
    val productName: String,
    val priceText: String? = null,
    val productType: String,
    val available: Boolean = false,
    val canPurchase: Boolean = false,
    val purchaseInProgress: Boolean = false
)

data class MemoryDownloadPurchaseUiState(
    val loading: Boolean = true,
    val pendingPurchase: Boolean = false,
    val pendingProductName: String? = null,
    val activeProductId: String? = null,
    val activeProductName: String? = null,
    val activeProductType: String? = null,
    val activeUntilEpochMs: Long? = null,
    val currentDailyLimit: Int = MemoryDownloadPassCatalog.DEFAULT_DAILY_LIMIT,
    val products: List<MemoryDownloadProductUiState> = emptyList()
) {
    val entitlementActive: Boolean
        get() = !activeProductId.isNullOrBlank()

    val hasPurchaseOptions: Boolean
        get() = products.any { it.available }
}

class MemoryDownloadPurchaseManager(
    context: Context,
    private val entitlementRepository: MemoryDownloadEntitlementRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val stateMutex = Mutex()
    private val mutableUiState = MutableStateFlow(MemoryDownloadPurchaseUiState())
    val uiState: StateFlow<MemoryDownloadPurchaseUiState> = mutableUiState

    private var productOffers: Map<String, ProductOffer> = emptyMap()
    private var purchasePendingProductId: String? = null
    private var purchaseInProgressProductId: String? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    suspend fun initialize() {
        entitlementRepository.initialize()
        publishState(loading = true)
        refresh()
    }

    fun refresh() {
        scope.launch {
            refreshInternal()
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        scope.launch {
            refreshInternal()
            val offer = stateMutex.withLock {
                val currentState = mutableUiState.value
                val selected = currentState.products.firstOrNull { it.productId == productId }
                if (selected == null || !selected.canPurchase) return@launch
                purchaseInProgressProductId = productId
                publishStateLocked(loading = false)
                productOffers[productId]
            } ?: return@launch

            val result = withContext(Dispatchers.Main) {
                billingClient.launchBillingFlow(activity, buildBillingFlowParams(offer))
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                stateMutex.withLock {
                    purchaseInProgressProductId = null
                    publishStateLocked(loading = false)
                }
                if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    refresh()
                } else {
                    Log.w(TAG, "launchBillingFlow failed code=${result.responseCode} msg=${result.debugMessage}")
                }
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                scope.launch {
                    stateMutex.withLock {
                        purchaseInProgressProductId = null
                        publishStateLocked(loading = false)
                    }
                }
            }

            else -> {
                Log.w(
                    TAG,
                    "onPurchasesUpdated failed code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
                )
                scope.launch {
                    stateMutex.withLock {
                        purchaseInProgressProductId = null
                        publishStateLocked(loading = false)
                    }
                }
            }
        }
    }

    private suspend fun refreshInternal() {
        refreshMutex.withLock {
            publishState(loading = true)
            if (!ensureReady()) {
                publishState(loading = false)
                return
            }

            val nextOffers = queryAllProductDetails()
            stateMutex.withLock {
                productOffers = nextOffers
                publishStateLocked(loading = true)
            }

            val inAppPurchases = queryPurchases(BillingClient.ProductType.INAPP)
            val subscriptionPurchases = queryPurchases(BillingClient.ProductType.SUBS)
            val hasFullPurchaseSnapshot =
                inAppPurchases.first.responseCode == BillingClient.BillingResponseCode.OK &&
                    subscriptionPurchases.first.responseCode == BillingClient.BillingResponseCode.OK
            if (!hasFullPurchaseSnapshot) {
                Log.w(
                    TAG,
                    "purchase sync incomplete inapp=${inAppPurchases.first.responseCode} subs=${subscriptionPurchases.first.responseCode}"
                )
                publishState(loading = false)
                return
            }

            syncPurchases(inAppPurchases.second + subscriptionPurchases.second)
        }
    }

    private suspend fun syncPurchases(purchases: List<Purchase>) {
        val nowMs = clock()
        val oneTimePurchase = latestPurchaseFor(
            purchases = purchases,
            productId = MemoryDownloadPassCatalog.ONE_TIME_PRODUCT.productId
        )
        val subscriptionPurchase = latestPurchaseFor(
            purchases = purchases,
            productId = MemoryDownloadPassCatalog.SUBSCRIPTION_PRODUCT.productId
        )
        val pendingPurchase = purchases
            .filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PENDING &&
                    purchase.products.any { productId -> MemoryDownloadPassCatalog.find(productId) != null }
            }
            .maxByOrNull { it.purchaseTime }

        if (subscriptionPurchase != null) {
            storeSubscriptionEntitlement(subscriptionPurchase)
        } else if (oneTimePurchase != null) {
            storeOneTimeEntitlement(oneTimePurchase, nowMs)
        } else {
            entitlementRepository.clear()
        }

        stateMutex.withLock {
            purchasePendingProductId = if (entitlementRepository.state.value.isActive(nowMs)) {
                null
            } else {
                pendingPurchase?.products?.firstOrNull { productId ->
                    MemoryDownloadPassCatalog.find(productId) != null
                }
            }
            purchaseInProgressProductId = null
            publishStateLocked(loading = false)
        }
    }

    private suspend fun storeSubscriptionEntitlement(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(purchase.purchaseToken)
        }
        entitlementRepository.storeSubscriptionPurchase(
            productId = MemoryDownloadPassCatalog.SUBSCRIPTION_PRODUCT.productId,
            purchaseToken = purchase.purchaseToken,
            purchasedAtEpochMs = purchase.purchaseTime
        )
    }

    private suspend fun storeOneTimeEntitlement(
        purchase: Purchase,
        nowMs: Long
    ) {
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(purchase.purchaseToken)
        }
        val entitlement = entitlementRepository.storeOneTimePurchase(
            productId = MemoryDownloadPassCatalog.ONE_TIME_PRODUCT.productId,
            purchaseToken = purchase.purchaseToken,
            purchasedAtEpochMs = purchase.purchaseTime
        )
        if (!entitlement.isActive(nowMs)) {
            consumePurchase(purchase.purchaseToken)
            entitlementRepository.clear()
        }
    }

    private suspend fun publishState(loading: Boolean) {
        stateMutex.withLock {
            publishStateLocked(loading)
        }
    }

    private fun publishStateLocked(loading: Boolean) {
        val nowMs = clock()
        val entitlement = entitlementRepository.state.value
        val activeDefinition = MemoryDownloadPassCatalog.find(entitlement.productId)
        val activeUntilEpochMs = entitlement.expiresAtEpochMs.takeIf { entitlement.isActive(nowMs) }
        val activeProductId = entitlement.productId.takeIf { entitlement.isActive(nowMs) }
        val pendingDefinition = purchasePendingProductId?.let(MemoryDownloadPassCatalog::find)
        mutableUiState.value = MemoryDownloadPurchaseUiState(
            loading = loading,
            pendingPurchase = purchasePendingProductId != null && activeProductId == null,
            pendingProductName = pendingDefinition?.defaultName,
            activeProductId = activeProductId,
            activeProductName = activeDefinition?.defaultName,
            activeProductType = entitlement.productType.takeIf { entitlement.isActive(nowMs) },
            activeUntilEpochMs = activeUntilEpochMs,
            currentDailyLimit = if (activeProductId != null) {
                MemoryDownloadPassCatalog.EXPANDED_DAILY_LIMIT
            } else {
                MemoryDownloadPassCatalog.DEFAULT_DAILY_LIMIT
            },
            products = MemoryDownloadPassCatalog.USER_VISIBLE_PRODUCTS.map { definition ->
                val offer = productOffers[definition.productId]
                val canPurchase = !loading &&
                    activeProductId == null &&
                    purchasePendingProductId == null &&
                    purchaseInProgressProductId == null &&
                    offer != null
                MemoryDownloadProductUiState(
                    productId = definition.productId,
                    productName = offer?.displayName ?: definition.defaultName,
                    priceText = offer?.formattedPrice,
                    productType = definition.productType,
                    available = offer != null,
                    canPurchase = canPurchase,
                    purchaseInProgress = purchaseInProgressProductId == definition.productId
                )
            }
        )
    }

    private suspend fun ensureReady(): Boolean {
        if (billingClient.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "billing service disconnected")
                }
            })
        }
    }

    private suspend fun queryAllProductDetails(): Map<String, ProductOffer> {
        return buildMap {
            for (definition in MemoryDownloadPassCatalog.USER_VISIBLE_PRODUCTS) {
                val (_, product) = queryProductDetails(definition)
                val offer = product?.toProductOffer(definition)
                if (offer != null) {
                    put(definition.productId, offer)
                }
            }
        }
    }

    private suspend fun queryProductDetails(
        definition: MemoryDownloadProductDefinition
    ): Pair<BillingResult, ProductDetails?> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(definition.productId)
                        .setProductType(definition.productType)
                        .build()
                )
            )
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(
                params,
                ProductDetailsResponseListener { billingResult, productDetailsResult ->
                    val product = extractProductDetails(productDetailsResult, definition.productId)
                    continuation.resume(billingResult to product)
                }
            )
        }
    }

    private suspend fun queryPurchases(productType: String): Pair<BillingResult, List<Purchase>> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(
                params,
                PurchasesResponseListener { billingResult, purchases ->
                    continuation.resume(billingResult to purchases)
                }
            )
        }
    }

    private suspend fun acknowledgePurchase(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val billingResult = suspendCancellableCoroutine<BillingResult> { continuation ->
            billingClient.acknowledgePurchase(params) { result ->
                continuation.resume(result)
            }
        }
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                TAG,
                "acknowledgePurchase failed code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
            )
        }
    }

    private suspend fun consumePurchase(purchaseToken: String) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val billingResult = suspendCancellableCoroutine<BillingResult> { continuation ->
            billingClient.consumeAsync(params) { result, _ ->
                continuation.resume(result)
            }
        }
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                TAG,
                "consumeAsync failed code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
            )
        }
    }

    private fun buildBillingFlowParams(offer: ProductOffer): BillingFlowParams {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
            .apply {
                if (!offer.offerToken.isNullOrBlank()) {
                    setOfferToken(offer.offerToken)
                }
            }
            .build()

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
    }

    private fun extractProductDetails(
        result: QueryProductDetailsResult,
        productId: String
    ): ProductDetails? {
        return result.productDetailsList.firstOrNull { it.productId == productId }
    }

    private fun ProductDetails.toProductOffer(
        definition: MemoryDownloadProductDefinition
    ): ProductOffer {
        return when (definition.productType) {
            BillingClient.ProductType.SUBS -> {
                val offer = subscriptionOfferDetails?.firstOrNull()
                ProductOffer(
                    productId = definition.productId,
                    productType = definition.productType,
                    productDetails = this,
                    offerToken = offer?.offerToken,
                    formattedPrice = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
                    displayName = name.takeIf { it.isNotBlank() } ?: definition.defaultName
                )
            }

            else -> {
                val multiOffer = oneTimePurchaseOfferDetailsList?.firstOrNull()
                val fallbackOffer = oneTimePurchaseOfferDetails
                ProductOffer(
                    productId = definition.productId,
                    productType = definition.productType,
                    productDetails = this,
                    offerToken = multiOffer?.offerToken,
                    formattedPrice = multiOffer?.formattedPrice ?: fallbackOffer?.formattedPrice,
                    displayName = name.takeIf { it.isNotBlank() } ?: definition.defaultName
                )
            }
        }
    }

    private fun latestPurchaseFor(
        purchases: List<Purchase>,
        productId: String
    ): Purchase? {
        return purchases
            .filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(productId)
            }
            .maxByOrNull { it.purchaseTime }
    }

    private data class ProductOffer(
        val productId: String,
        val productType: String,
        val productDetails: ProductDetails,
        val offerToken: String?,
        val formattedPrice: String?,
        val displayName: String
    )

    private companion object {
        const val TAG = "MemoryDownloadPurchase"
    }
}
