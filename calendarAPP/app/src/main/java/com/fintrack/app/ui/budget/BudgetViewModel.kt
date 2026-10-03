package com.fintrack.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.BudgetCapsStore
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.GoalStore
import com.fintrack.app.data.MsiPlan
import com.fintrack.app.data.MsiStore
import com.fintrack.app.data.PersonDebt
import com.fintrack.app.data.PersonDebtStore
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
import com.fintrack.app.domain.GoalContributions
import com.fintrack.app.domain.GoalEvaluation
import com.fintrack.app.domain.GoalPlanner
import com.fintrack.app.domain.SavingsGoal
import com.fintrack.app.domain.toLocalDateIn
import com.fintrack.app.domain.MonthProjection
import com.fintrack.app.domain.ShortTermReport
import com.fintrack.app.domain.friendlyErrorMessage
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
    /** Movimientos del mes actual y anterior (para cargos a tarjeta). */
    val recentMovements: List<MovementRow> = emptyList(),
    /** Transacciones cargadas (para cargos a tarjeta). */
    val allTransactions: List<TransactionEntity> = emptyList(),
    // === Región D2 (estrategia de deudas): saldos agregados, CAT por tarjeta. ===
    /** Tarjetas para la estrategia (el CAT vive en cada tarjeta, default 60%). */
    val debtCards: List<CreditCardRow> = emptyList(),
    /** Saldo a pagar por tarjeta (remaining del estado abierto). */
    val debtBalances: Map<String, Double> = emptyMap(),
    /** MSI activos (entran sin intereses). */
    val debtMsi: List<MsiPlan> = emptyList(),
    /** Deudas personales (solo "yo debo", sin intereses). */
    val debtPersons: List<PersonDebt> = emptyList(),
    // === Fin región D2. ===
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
    // === Región D2: stores para agregar deudas (ver AppModule). ===
    private val creditCardStore: CreditCardStore,
    private val msiStore: MsiStore,
    private val personDebtStore: PersonDebtStore
    // === Fin región D2. ===
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
                val evaluation = BudgetPlanner.evaluateGoal(
                    _uiState.value.goalAmount,
                    _uiState.value.goalMonths,
                    BudgetPlanner.averageSurplus(mediumTerm)
                )
                // === Región D2: agrega tarjetas + MSI + personales (solo lectura). ===
                val debtCards = runCatching { creditCardStore.cardsSnapshot() }
                    .getOrDefault(emptyList())
                val debtCharges = runCatching { creditCardStore.chargesSnapshot() }
                    .getOrDefault(emptyMap())
                val debtMsi = runCatching { msiStore.snapshot() }.getOrDefault(emptyList())
                val debtPersons = runCatching { personDebtStore.snapshot() }
                    .getOrDefault(emptyList())
                // === Fin región D2. ===
                _uiState.value = _uiState.value.copy(
                    currentMonth = month,
                    shortTerm = shortTerm,
                    mediumTerm = mediumTerm,
                    recentMovements = recentMovements,
                    allTransactions = transactions,
                    goalEvaluation = evaluation,
                    // === Región D2: saldos para la estrategia de deudas. ===
                    debtCards = debtCards,
                    debtBalances = saldosTarjetas(debtCards, debtCharges, transactions, recentMovements),
                    debtMsi = debtMsi,
                    debtPersons = debtPersons,
                    // === Fin región D2. ===
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyErrorMessage(e)
                )
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

    /** Define el tope mensual de una categoría (<=0 lo devuelve al automático). */
    fun setCap(category: String, cap: Double) {
        viewModelScope.launch {
            runCatching { capsStore.setCap(category, cap) }
            loadBudget()
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
        addGoalFromProduct(name, price, "", "")
    }

    /** Alta desde un link scraperado: guarda tienda y URL para re-consultar precio. */
    fun addGoalFromProduct(name: String, price: Double, url: String, store: String) {
        val cleanName = name.trim().ifBlank { "Mi objetivo" }
        val safePrice = if (price < 0.0) 0.0 else price
        val goal = SavingsGoal(
            id = "goal-${System.currentTimeMillis()}-${_uiState.value.goals.size}",
            name = cleanName,
            price = safePrice,
            url = url.trim(),
            store = store.trim()
        )
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals + goal,
            selectedGoalId = goal.id
        )
        persistGoals()
    }

    /** Recarga las metas del DataStore (ej. al volver de Planificar compra). */
    fun refreshGoals() {
        loadGoals()
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

    /** D4: aporta a la meta (suma + historial) y persiste. */
    fun addContribution(goalId: String, monto: Double) {
        if (monto <= 0.0) return
        val fechaIso = java.time.LocalDate.now().toString()
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals.map {
                if (it.id == goalId) GoalContributions.aportar(it, monto, fechaIso) else it
            }
        )
        persistGoals()
    }

    /** D4: retira de la meta sin dejar el juntado en negativo y persiste. */
    fun withdrawContribution(goalId: String, monto: Double) {
        if (monto <= 0.0) return
        val fechaIso = java.time.LocalDate.now().toString()
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals.map {
                if (it.id == goalId) GoalContributions.retirar(it, monto, fechaIso) else it
            }
        )
        persistGoals()
    }

    // === Región D2 (estrategia de deudas): saldos, lista agregada y CAT. ===
    /**
     * Saldo a pagar por tarjeta con cargos reales: remaining del estado
     * abierto vía `CreditCardPlanner.summarize` (mismo criterio que Cuentas).
     */
    private fun saldosTarjetas(
        cards: List<CreditCardRow>,
        charges: Map<String, String>,
        transactions: List<TransactionEntity>,
        movements: List<MovementRow>
    ): Map<String, Double> {
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        return cards.associate { card ->
            val dated = mutableListOf<Pair<java.time.LocalDate, Double>>()
            transactions.filter { charges["tx:${it.id}"] == card.id }.forEach { tx ->
                dated.add(tx.timestamp.toLocalDateIn() to tx.amount)
            }
            movements.filter { charges["mov:${it.id}"] == card.id && !it.archived }
                .forEach { mov ->
                    runCatching { java.time.LocalDate.parse(mov.date) }.getOrNull()?.let {
                        dated.add(it to mov.confirmed_amount)
                    }
                }
            val pagos = emptyList<Pair<java.time.LocalDate, Double>>()
            val resumen = com.fintrack.app.domain.CreditCardPlanner.summarize(
                card.id, card.cutoffDay, card.paymentDay, dated, today, pagos,
                graceDays = card.graceDays
            )
            card.id to resumen.remaining
        }
    }

    /**
     * TODO lo que debes, agregado: tarjetas (con su CAT) + MSI activos
     * (CAT 0) + deudas personales "yo debo" (CAT 0). Lo que te deben no es
     * deuda tuya y no entra.
     */
    fun deudasParaEstrategia(): List<com.fintrack.app.domain.DebtPlanner.Deuda> {
        val state = _uiState.value
        val hoy = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        val tarjetas = state.debtCards.map { card ->
            com.fintrack.app.domain.DebtPlanner.Deuda(
                id = "card:${card.id}",
                nombre = card.displayName,
                saldo = state.debtBalances.getOrDefault(card.id, 0.0),
                catAnual = card.catAnual
            )
        }
        val msi = state.debtMsi.filter { !it.liquidado }.mapNotNull { plan ->
            val card = state.debtCards.firstOrNull { it.id == plan.cardId } ?: return@mapNotNull null
            val saldo = com.fintrack.app.domain.MsiPlanner.saldoPendiente(plan, card.cutoffDay, hoy)
            if (saldo <= 0.0) null else com.fintrack.app.domain.DebtPlanner.Deuda(
                id = "msi:${plan.id}",
                nombre = "${plan.concepto} (MSI)",
                saldo = saldo,
                catAnual = 0.0
            )
        }
        val personales = state.debtPersons.filter { it.esDeudaMia }.mapNotNull { deuda ->
            val saldo = com.fintrack.app.domain.PersonDebtPlanner.saldo(deuda)
            if (saldo <= 0.0) null else com.fintrack.app.domain.DebtPlanner.Deuda(
                id = "per:${deuda.id}",
                nombre = deuda.nombre,
                saldo = saldo,
                catAnual = 0.0
            )
        }
        return com.fintrack.app.domain.DebtPlanner.agregar(tarjetas, msi, personales)
    }

    /** CAT anual editable por tarjeta (default 60%, <=0 vuelve al default). */
    fun setCardCat(cardId: String, cat: Double) {
        viewModelScope.launch {
            val card = _uiState.value.debtCards.firstOrNull { it.id == cardId } ?: return@launch
            val safe = if (cat <= 0.0) com.fintrack.app.domain.DebtPlanner.DEFAULT_CAT else cat
            runCatching { creditCardStore.upsertCard(card.copy(catAnual = safe)) }
            loadBudget()
        }
    }
    // === Fin región D2. ===
}
