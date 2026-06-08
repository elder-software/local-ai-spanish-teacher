package com.example.localllmvoice.ui.onboarding

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmvoice.data.purchase.RevenueCatRepository
import com.example.localllmvoice.data.purchase.RevenueCatRepository.PurchaseState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    val uiState: StateFlow<PaywallUiState> = combine(
        purchaseRepository.formattedPrice,
        purchaseRepository.isEntitled,
        purchaseRepository.purchaseState,
    ) { formattedPrice, isEntitled, purchaseState ->
        PaywallUiState(
            priceText = formattedPrice,
            isPurchasing = purchaseState is PurchaseState.Pending,
            isEntitled = isEntitled,
            errorMessage = (purchaseState as? PurchaseState.Failed)?.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaywallUiState(),
    )

    fun purchase(activity: Activity) {
        viewModelScope.launch {
            purchaseRepository.purchase(activity)
        }
    }

    fun restore() {
        viewModelScope.launch {
            purchaseRepository.restore()
        }
    }
}
