package com.example.localllmvoice.data.purchase

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.ExperimentalPreviewRevenueCatPurchasesAPI
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RevenueCatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isEntitled = MutableStateFlow(false)
    val isEntitled: StateFlow<Boolean> = _isEntitled.asStateFlow()

    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private var fullLibraryPackage: Package? = null

    fun start() {
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> updateEntitlement(info) }

        scope.launch {
            loadCustomerInfoAndOfferings()
        }
    }

    @OptIn(ExperimentalPreviewRevenueCatPurchasesAPI::class)
    private suspend fun loadCustomerInfoAndOfferings() {
        try {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            updateEntitlement(info)
        } catch (_: PurchasesException) {
            // Entitlement stays false; RevenueCat may retry on next listener update.
        }

        try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            fullLibraryPackage = offerings.current?.availablePackages?.firstOrNull()
            _formattedPrice.value = fullLibraryPackage?.product?.price?.formatted
        } catch (_: PurchasesException) {
            // Price stays null; UI falls back to FALLBACK_PRICE.
        }
    }

    private fun updateEntitlement(info: CustomerInfo) {
        _isEntitled.value = info.entitlements[ENTITLEMENT_ID]?.isActive == true
    }

    @OptIn(ExperimentalPreviewRevenueCatPurchasesAPI::class)
    suspend fun purchase(activity: Activity) {
        _purchaseState.value = PurchaseState.Pending

        val pkg = fullLibraryPackage
        if (pkg == null) {
            _purchaseState.value = PurchaseState.Failed("Store unavailable")
            return
        }

        try {
            val result = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, pkg).build()
            )
            updateEntitlement(result.customerInfo)
            _purchaseState.value = PurchaseState.Success
        } catch (e: PurchasesTransactionException) {
            _purchaseState.value = if (e.userCancelled) {
                PurchaseState.Idle
            } else {
                PurchaseState.Failed(e.message)
            }
        } catch (e: PurchasesException) {
            _purchaseState.value = PurchaseState.Failed(e.message)
        }
    }

    fun resetPurchaseState() {
        if (_purchaseState.value is PurchaseState.Failed) {
            _purchaseState.value = PurchaseState.Idle
        }
    }

    @OptIn(ExperimentalPreviewRevenueCatPurchasesAPI::class)
    suspend fun restore() {
        try {
            val info = Purchases.sharedInstance.awaitRestore()
            updateEntitlement(info)
        } catch (e: PurchasesException) {
            _purchaseState.value = PurchaseState.Failed(e.message)
        }
    }

    sealed interface PurchaseState {
        data object Idle : PurchaseState
        data object Pending : PurchaseState
        data object Success : PurchaseState
        data class Failed(val message: String) : PurchaseState
    }

    companion object {
        const val ENTITLEMENT_ID = "full_library"
    }
}
