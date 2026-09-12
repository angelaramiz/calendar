package com.fintrack.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.BudgetPlanner
import com.fintrack.app.domain.GoalEvaluation
import com.fintrack.app.domain.MonthProjection
import com.fintrack.app.domain.ShortTermReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

data class BudgetUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val shortTerm: ShortTermReport? = null,
    val mediumTerm: List<MonthProjection> = emptyList(),
    val goalAmount: Double = 20_000.0,
    val goalMonths: Int = 12,
    val goalEvaluation: GoalEvaluation? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false
)

class BudgetViewModel(
    private val transactionRepository: TransactionRepository,
    private val patternRepository: PatternRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadBudget()
    }

    fun loadBudget() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                needsLogin = true,
                error = "Inicia sesión para ver tu presupuesto."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false, error = null)
            try {
                val month = YearMonth.now()
                val transactions = transactionRepository.getTransactions(userId)
                val patterns =
                    patternRepository.getIncomePatterns(userId).mapNotNull { it.toDomain("INCOME") } +
                        patternRepository.getExpensePatterns(userId).mapNotNull { it.toDomain("EXPENSE") }
                val shortTerm = BudgetPlanner.buildShortTerm(transactions, month)
                val mediumTerm = BudgetPlanner.buildMediumTerm(patterns, transactions, month)
                val evaluation = BudgetPlanner.evaluateGoal(
                    _uiState.value.goalAmount,
                    _uiState.value.goalMonths,
                    BudgetPlanner.averageSurplus(mediumTerm)
                )
                _uiState.value = _uiState.value.copy(
                    currentMonth = month,
                    shortTerm = shortTerm,
                    mediumTerm = mediumTerm,
                    goalEvaluation = evaluation,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateGoal(amount: Double, months: Int) {
        val safeAmount = if (amount < 0.0) 0.0 else amount
        val evaluation = BudgetPlanner.evaluateGoal(
            safeAmount,
            months,
            BudgetPlanner.averageSurplus(_uiState.value.mediumTerm)
        )
        _uiState.value = _uiState.value.copy(
            goalAmount = safeAmount,
            goalMonths = months,
            goalEvaluation = evaluation
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
