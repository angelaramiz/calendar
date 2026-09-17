package com.fintrack.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.BudgetCapsStore
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.GoalStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.BudgetPlanner
import com.fintrack.app.domain.CashPlan
import com.fintrack.app.domain.CreditPlan
import com.fintrack.app.domain.GoalComparison
import com.fintrack.app.domain.GoalEvaluation
import com.fintrack.app.domain.GoalPlanner
import com.fintrack.app.domain.SavingsGoal
import com.fintrack.app.domain.MonthProjection
import com.fintrack.app.domain.ShortTermReport
import com.fintrack.app.domain.SubscriptionCandidate
import com.fintrack.app.domain.SubscriptionDetector
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
    val goals: List<SavingsGoal> = emptyList(),
    val selectedGoalId: String? = null,
    /** Topes mensuales definidos por el usuario (reemplazan los automáticos). */
    val customCaps: Map<String, Double> = emptyMap(),
    /** Suscripciones detectadas (cargos repetidos sin patrón). */
    val subscriptions: List<SubscriptionCandidate> = emptyList(),
    /** Tarjetas de crédito (corte y pago por tarjeta). */
    val cards: List<CreditCardRow> = emptyList(),
    /** Tag de cargos: "tx:<id>" o "mov:<id>" -> cardId. */
    val cardCharges: Map<String, String> = emptyMap(),
    /** Movimientos del mes actual y anterior (para cargos a tarjeta). */
    val recentMovements: List<MovementRow> = emptyList(),
    /** Transacciones cargadas (para cargos a tarjeta). */
    val allTransactions: List<TransactionEntity> = emptyList(),
    val info: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false
)

class BudgetViewModel(
    private val transactionRepository: TransactionRepository,
    private val patternRepository: PatternRepository,
    private val authRepository: AuthRepository,
    private val goalStore: GoalStore,
    private val capsStore: BudgetCapsStore,
    private val creditCardStore: CreditCardStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadBudget()
        loadGoals()
    }

    /** Recupera las metas guardadas en DataStore al abrir la pestaña. */
    private fun loadGoals() {
        viewModelScope.launch {
            val saved = runCatching { goalStore.snapshot() }.getOrDefault(emptyList())
            val currentSelected = _uiState.value.selectedGoalId
            _uiState.value = _uiState.value.copy(
                goals = saved,
                selectedGoalId = saved.firstOrNull { it.id == currentSelected }?.id
                    ?: saved.firstOrNull()?.id
            )
        }
    }

    /** Persiste la lista actual de metas (agregar/editar/eliminar). */
    private fun persistGoals() {
        val goals = _uiState.value.goals
        viewModelScope.launch {
            runCatching { goalStore.save(goals) }
        }
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
                val caps = runCatching { capsStore.snapshot() }.getOrDefault(emptyMap())
                _uiState.value = _uiState.value.copy(customCaps = caps)
                val transactions = transactionRepository.getTransactions(userId)
                val cards = runCatching { creditCardStore.cardsSnapshot() }
                    .getOrDefault(emptyList())
                val cardCharges = runCatching { creditCardStore.chargesSnapshot() }
                    .getOrDefault(emptyMap())
                val prevMonth = month.minusMonths(1)
                val recentMovements = runCatching {
                    patternRepository.getMovementsForMonth(
                        userId,
                        prevMonth.atDay(1).toString(),
                        month.atEndOfMonth().toString()
                    )
                }.getOrDefault(emptyList())
                val patterns =
                    patternRepository.getIncomePatterns(userId).mapNotNull { it.toDomain("INCOME") } +
                        patternRepository.getExpensePatterns(userId).mapNotNull { it.toDomain("EXPENSE") }
                val shortTerm = BudgetPlanner.buildShortTerm(
                    transactions, month, _uiState.value.customCaps
                )
                val mediumTerm = BudgetPlanner.buildMediumTerm(patterns, transactions, month)
                val subscriptions = SubscriptionDetector.detect(transactions, patterns, month)
                val evaluation = BudgetPlanner.evaluateGoal(
                    _uiState.value.goalAmount,
                    _uiState.value.goalMonths,
                    BudgetPlanner.averageSurplus(mediumTerm)
                )
                _uiState.value = _uiState.value.copy(
                    currentMonth = month,
                    shortTerm = shortTerm,
                    mediumTerm = mediumTerm,
                    subscriptions = subscriptions,
                    cards = cards,
                    cardCharges = cardCharges,
                    recentMovements = recentMovements,
                    allTransactions = transactions,
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

    /** Crea o actualiza una tarjeta (corte y pago por día del mes 1-31). */
    fun saveCard(id: String?, name: String, cutoffDay: Int, paymentDay: Int) {
        val cleanName = name.trim().ifBlank { "Mi tarjeta" }
        val card = CreditCardRow(
            id = id ?: "card-${System.currentTimeMillis()}",
            name = cleanName,
            cutoffDay = cutoffDay.coerceIn(1, 31),
            paymentDay = paymentDay.coerceIn(1, 31)
        )
        viewModelScope.launch {
            runCatching { creditCardStore.upsertCard(card) }
            _uiState.value = _uiState.value.copy(
                info = "Tarjeta ${card.name} guardada (corte día ${card.cutoffDay}, pago día ${card.paymentDay})."
            )
            loadBudget()
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            runCatching { creditCardStore.deleteCard(id) }
            _uiState.value = _uiState.value.copy(info = "Tarjeta eliminada.")
            loadBudget()
        }
    }

    /** Quita el tag de tarjeta a un cargo (vuelve a ser gasto normal). */
    fun untagCharge(key: String) {
        viewModelScope.launch {
            runCatching { creditCardStore.setCharge(key, null) }
            loadBudget()
        }
    }

    fun clearInfo() {
        _uiState.value = _uiState.value.copy(info = null)
    }

    /** Define el tope mensual de una categoría (<=0 lo devuelve al automático). */
    fun setCap(category: String, cap: Double) {
        viewModelScope.launch {
            runCatching { capsStore.setCap(category, cap) }
            loadBudget()
        }
    }

    /** Convierte una suscripción detectada en recurrente mensual de gasto. */
    fun createSubscriptionPattern(candidate: SubscriptionCandidate) {        if (userId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
            try {
                patternRepository.insertPattern(
                    userId = userId,
                    isIncome = false,
                    name = candidate.merchant,
                    description = "Detectada automáticamente",
                    category = candidate.category,
                    baseAmount = candidate.amount,
                    frequency = "monthly",
                    startDateIso = candidate.nextExpected.toString()
                )
                _uiState.value = _uiState.value.copy(
                    info = "${candidate.merchant} ahora es recurrente mensual."
                )
                loadBudget()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    val selectedGoal: SavingsGoal?
        get() = _uiState.value.goals.firstOrNull { it.id == _uiState.value.selectedGoalId }

    private val avgSurplus: Double
        get() = BudgetPlanner.averageSurplus(_uiState.value.mediumTerm)

    private val monthIncome: Double
        get() = _uiState.value.shortTerm?.monthIncome ?: 0.0

    fun cashPlanFor(goal: SavingsGoal): CashPlan =
        GoalPlanner.evaluateCash(goal.price, avgSurplus)

    fun creditPlanFor(goal: SavingsGoal): CreditPlan =
        GoalPlanner.evaluateCredit(
            price = goal.price,
            downPayment = GoalPlanner.resolveDownPayment(goal),
            annualRatePercent = goal.annualRatePercent,
            months = goal.termMonths,
            monthlyIncome = monthIncome,
            avgSurplus = avgSurplus
        )

    fun comparisonFor(goal: SavingsGoal): GoalComparison =
        GoalPlanner.compareCashVsCredit(goal.price, creditPlanFor(goal).totalCost)

    fun addGoal(name: String, price: Double) {
        val cleanName = name.trim().ifBlank { "Mi objetivo" }
        val safePrice = if (price < 0.0) 0.0 else price
        val goal = SavingsGoal(
            id = "goal-${System.currentTimeMillis()}-${_uiState.value.goals.size}",
            name = cleanName,
            price = safePrice
        )
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals + goal,
            selectedGoalId = goal.id
        )
        persistGoals()
    }

    fun updateGoal(updated: SavingsGoal) {
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals.map { if (it.id == updated.id) updated else it }
        )
        persistGoals()
    }

    fun removeGoal(id: String) {
        val remaining = _uiState.value.goals.filterNot { it.id == id }
        _uiState.value = _uiState.value.copy(
            goals = remaining,
            selectedGoalId = if (_uiState.value.selectedGoalId == id) {
                remaining.firstOrNull()?.id
            } else {
                _uiState.value.selectedGoalId
            }
        )
        persistGoals()
    }

    fun selectGoal(id: String?) {
        _uiState.value = _uiState.value.copy(selectedGoalId = id)
    }
}
