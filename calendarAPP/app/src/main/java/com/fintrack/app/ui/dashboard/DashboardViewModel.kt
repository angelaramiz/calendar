package com.fintrack.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.OtaUpdateInfo
import com.fintrack.app.data.repository.OtaUpdateRepository
import com.fintrack.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val currentBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val updateAvailable: OtaUpdateInfo? = null,
    val updateMessage: String? = null
)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val otaUpdateRepository: OtaUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadDashboard()
        checkForUpdate()
    }

    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val update = otaUpdateRepository.checkForUpdate()
            if (update != null) {
                _uiState.value = _uiState.value.copy(updateAvailable = update, updateMessage = null)
            } else if (manual) {
                _uiState.value = _uiState.value.copy(updateMessage = "Ya tienes la última versión.")
            }
        }
    }

    fun clearUpdateMessage() {
        _uiState.value = _uiState.value.copy(updateMessage = null)
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(updateAvailable = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadDashboard() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                needsLogin = true,
                error = "Inicia sesión para ver tus transacciones."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false, error = null)
            try {
                val transactions = transactionRepository.getTransactions(userId)
                val income = transactions.filter { it.isIncomeType() }.sumOf { it.amount }
                val expenses = transactions.filter { !it.isIncomeType() }.sumOf { it.amount }

                _uiState.value = _uiState.value.copy(
                    currentBalance = income - expenses,
                    totalIncome = income,
                    totalExpenses = expenses,
                    recentTransactions = transactions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addTransaction(transaction: TransactionEntity) {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                needsLogin = true,
                error = "Inicia sesión para guardar transacciones."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                transactionRepository.insertTransaction(userId, transaction)
                loadDashboard()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se pudo guardar: ${e.message?.take(150)}"
                )
            }
        }
    }

    fun deleteTransaction(id: String) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(id)
                loadDashboard()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "No se pudo eliminar: ${e.message?.take(150)}"
                )
            }
        }
    }

    fun updateTransaction(id: String, transaction: TransactionEntity) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                transactionRepository.updateTransaction(id, transaction)
                loadDashboard()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se pudo modificar: ${e.message?.take(150)}"
                )
            }
        }
    }
}

fun TransactionEntity.isIncomeType(): Boolean =
    type.equals("INCOME", ignoreCase = true) || type.equals("ingreso", ignoreCase = true)
