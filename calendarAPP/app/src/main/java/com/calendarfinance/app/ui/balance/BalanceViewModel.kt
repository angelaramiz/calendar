package com.calendarfinance.app.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendarfinance.app.data.model.BalanceSummary
import com.calendarfinance.app.data.model.MonthlyBalance
import com.calendarfinance.app.data.repository.AuthRepository
import com.calendarfinance.app.data.repository.MovementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceUiState(
    val isLoading: Boolean = false,
    val balance: BalanceSummary = BalanceSummary(),
    val monthlyBalances: List<MonthlyBalance> = emptyList(),
    val error: String? = null
)

class BalanceViewModel(
    private val movementRepository: MovementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BalanceUiState())
    val uiState: StateFlow<BalanceUiState> = _uiState.asStateFlow()

    fun loadBalance(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val balance = movementRepository.getBalance(userId)
                val monthly = movementRepository.getMonthlyBalances(userId)
                _uiState.value = BalanceUiState(
                    isLoading = false,
                    balance = balance,
                    monthlyBalances = monthly
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
