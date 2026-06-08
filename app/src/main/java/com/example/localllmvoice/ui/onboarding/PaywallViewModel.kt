package com.example.localllmvoice.ui.onboarding

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.data.purchase.RevenueCatRepository
import com.example.localllmvoice.data.purchase.RevenueCatRepository.PurchaseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
    val priceText: String? = null,
    val isPurchasing: Boolean = false,
    val isEntitled: Boolean = false,
    val errorMessage: String? = null,
)

class PaywallViewModel(
    private val purchaseRepository: RevenueCatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                purchaseRepository.formattedPrice,
                purchaseRepository.isEntitled,
                purchaseRepository.purchaseState,
            ) { formattedPrice, isEntitled, purchaseState ->
                PaywallUiState(
                    priceText = formattedPrice,
                    isPurchasing = purchaseState is PurchaseState.Pending,
                    isEntitled = isEntitled,
                    errorMessage = (purchaseState as? PurchaseState.Failed)?.message?.let(::mapPurchaseError),
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun purchase(activity: Activity?) {
        viewModelScope.launch {
            if (activity == null) {
                _uiState.update { it.copy(errorMessage = "Something went wrong. Please try again.") }
                return@launch
            }
            purchaseRepository.purchase(activity)
        }
    }

    fun restore() {
        viewModelScope.launch {
            purchaseRepository.restore()
        }
    }

    fun dismissError() {
        purchaseRepository.resetPurchaseState()
    }

    private fun mapPurchaseError(raw: String?): String {
        val message = raw?.trim().orEmpty()
        return when {
            message.equals("Store unavailable", ignoreCase = true) ->
                "The Play Store isn't available right now. Check your connection and try again shortly."
            message.contains("network", ignoreCase = true) ||
                    message.contains("internet", ignoreCase = true) ||
                    message.contains("connection", ignoreCase = true) ->
                "Check your internet connection and try again."
            message.contains("already own", ignoreCase = true) ||
                    message.contains("item already owned", ignoreCase = true) ->
                "You already own the full library. Try restoring your purchase if it isn't unlocked yet."
            message.contains("not allowed", ignoreCase = true) ||
                    message.contains("billing", ignoreCase = true) ->
                "Purchases aren't available on this device. Check that you're signed into Google Play."
            message.contains("not found", ignoreCase = true) ||
                    message.contains("no active", ignoreCase = true) ||
                    message.contains("no purchases", ignoreCase = true) ->
                "We couldn't find a previous purchase linked to this Google account."
            else -> "Something went wrong. Please try again."
        }
    }
}
