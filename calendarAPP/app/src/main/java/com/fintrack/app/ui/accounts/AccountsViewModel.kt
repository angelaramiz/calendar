package com.fintrack.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.CardPayment
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.MsiPlan
import com.fintrack.app.data.MsiStore
import com.fintrack.app.data.PersonDebt
import com.fintrack.app.data.PersonDebtStore
import com.fintrack.app.data.ServiceBillRow
import com.fintrack.app.data.ServiceBillStore
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.WalletStore
import com.fintrack.app.data.toResolver
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.SubscriptionCandidate
import com.fintrack.app.domain.SubscriptionDetector
import com.fintrack.app.domain.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

data class AccountsUiState(
    /** Suscripciones detectadas (cargos repetidos sin patrón). */
    val subscriptions: List<SubscriptionCandidate> = emptyList(),
    /** Cuentas de débito / billeteras locales (nómina, vales, ahorro…). */
    val wallets: List<WalletRow> = emptyList(),
    /** Flujo del mes por billetera (ingresos/gastos/neto, sin tags de crédito). */
    val walletFlows: Map<String, com.fintrack.app.domain.WalletResolver.WalletMonthFlow> = emptyMap(),
    /** Tarjetas de crédito (corte y pago por tarjeta). */
    val cards: List<CreditCardRow> = emptyList(),
    /** Tag de cargos: "tx:<id>" o "mov:<id>" -> cardId. */
    val cardCharges: Map<String, String> = emptyMap(),
    /** Pagos registrados contra cortes. */
    val cardPayments: List<CardPayment> = emptyList(),
    /** Pagos de servicios (vencimientos con recordatorio). */
    val bills: List<ServiceBillRow> = emptyList(),
    /** Movimientos del mes actual y anterior (para cargos a tarjeta). */
    val recentMovements: List<MovementRow> = emptyList(),
    /** Transacciones cargadas (para cargos a tarjeta y suscripciones). */
    val allTransactions: List<TransactionEntity> = emptyList(),
    // === Región D8+A (deudas personales + MSI): estado local, sin DDL. ===
    /** Deudas personales (quién te debe / a quién debes). */
    val personDebts: List<PersonDebt> = emptyList(),
    /** Planes MSI activos por tarjeta. */
    val msiPlans: List<MsiPlan> = emptyList(),
    // === Fin región D8+A. ===
    val info: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false
)

/**
 * Pestaña Cuentas: administración de dinero (débito, crédito, servicios,
 * suscripciones). Presupuesto queda como pestaña de análisis financiero.
 */
class AccountsViewModel(
    private val transactionRepository: TransactionRepository,
    private val patternRepository: PatternRepository,
    private val authRepository: AuthRepository,
    private val walletStore: WalletStore,
    private val creditCardStore: CreditCardStore,
    private val billStore: ServiceBillStore,
    // === Región D8+A: stores inyectados (ver AppModule). ===
    private val personDebtStore: PersonDebtStore,
    private val msiStore: MsiStore
    // === Fin región D8+A. ===
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                needsLogin = true,
                error = "Inicia sesión para ver tus cuentas."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false, error = null)
            try {
                val month = YearMonth.now()
                val transactions = transactionRepository.getTransactions(userId)
                val wallets = runCatching {
                    walletStore.ensureDefaults()
                    walletStore.snapshot()
                }.getOrDefault(emptyList())
                val cards = runCatching { creditCardStore.cardsSnapshot() }
                    .getOrDefault(emptyList())
                val cardCharges = runCatching { creditCardStore.chargesSnapshot() }
                    .getOrDefault(emptyMap())
                val cardPayments = runCatching { creditCardStore.paymentsSnapshot() }
                    .getOrDefault(emptyList())
                val bills = runCatching { billStore.snapshot() }.getOrDefault(emptyList())
                // === Región D8+A: deudas personales + MSI (100% local). ===
                val personDebts = runCatching { personDebtStore.snapshot() }
                    .getOrDefault(emptyList())
                val msiPlans = runCatching { msiStore.snapshot() }.getOrDefault(emptyList())
                // === Fin región D8+A. ===
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
                val subscriptions = SubscriptionDetector.detect(transactions, patterns, month)
                // Flujo del mes por cuenta en hora local: los gastos con tag de
                // tarjeta no restan a la billetera (se pagan al corte).
                val zone = java.time.ZoneId.systemDefault()
                val localMonth = java.time.YearMonth.now(zone)
                val overrides = runCatching { walletStore.overridesSnapshot() }
                    .getOrDefault(emptyMap())
                val cashOnly = transactions.filter { cardCharges["tx:${it.id}"] == null }
                val walletFlows = com.fintrack.app.domain.WalletResolver.monthFlow(
                    cashOnly,
                    localMonth,
                    wallets.map { it.toResolver() },
                    overrides,
                    zone
                )
                _uiState.value = _uiState.value.copy(
                    subscriptions = subscriptions,
                    wallets = wallets,
                    walletFlows = walletFlows,
                    cards = cards,
                    cardCharges = cardCharges,
                    cardPayments = cardPayments,
                    bills = bills,
                    recentMovements = recentMovements,
                    allTransactions = transactions,
                    // === Región D8+A. ===
                    personDebts = personDebts,
                    msiPlans = msiPlans,
                    // === Fin región D8+A. ===
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearInfo() {
        _uiState.value = _uiState.value.copy(info = null)
    }

    /** Alta de cuenta de débito propia (nómina, vales, ahorro…) + recarga. */
    fun addWallet(name: String, last4: String = "", kind: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { walletStore.addWallet(name, last4, kind) }
            _uiState.value = _uiState.value.copy(info = "Cuenta agregada.")
            loadAccounts()
        }
    }

    /**
     * Quita una cuenta de débito. Si era fija, no vuelve a aparecer
     * (igual que en Mis billeteras).
     */
    fun deleteWallet(id: String) {
        viewModelScope.launch {
            runCatching { walletStore.deleteWallet(id) }
            loadAccounts()
        }
    }

    /** Crea o actualiza una tarjeta (día fijo de pago o plazo de N días tipo Plata). */
    fun saveCard(
        id: String?,
        name: String,
        cutoffDay: Int,
        paymentDay: Int,
        last4: String = "",
        graceDays: Int = 0
    ) {
        val cleanName = name.trim().ifBlank { "Mi tarjeta" }
        val grace = graceDays.coerceIn(0, 90)
        val card = CreditCardRow(
            id = id ?: "card-${System.currentTimeMillis()}",
            name = cleanName,
            cutoffDay = cutoffDay.coerceIn(1, 31),
            paymentDay = paymentDay.coerceIn(1, 31),
            last4 = last4.filter { it.isDigit() }.take(4),
            graceDays = grace
        )
        viewModelScope.launch {
            runCatching { creditCardStore.upsertCard(card) }
            val terms = if (grace > 0) "pago +$grace días"
            else "pago día ${card.paymentDay}"
            _uiState.value = _uiState.value.copy(
                info = "Tarjeta ${card.displayName} guardada (corte día ${card.cutoffDay}, $terms)."
            )
            loadAccounts()
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            runCatching { creditCardStore.deleteCard(id) }
            _uiState.value = _uiState.value.copy(info = "Tarjeta eliminada.")
            loadAccounts()
        }
    }

    /** Quita el tag de tarjeta a un cargo (vuelve a ser gasto normal). */
    fun untagCharge(key: String) {
        viewModelScope.launch {
            runCatching { creditCardStore.setCharge(key, null) }
            loadAccounts()
        }
    }

    /** Registra lo que en realidad se pagó contra el corte indicado. */
    fun recordCardPayment(cardId: String, statementCutoffIso: String, amount: Double) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            runCatching {
                creditCardStore.addPayment(
                    CardPayment(
                        id = "pay-${System.currentTimeMillis()}",
                        cardId = cardId,
                        amount = amount,
                        dateIso = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString(),
                        statementCutoffIso = statementCutoffIso
                    )
                )
            }
            _uiState.value = _uiState.value.copy(info = "Pago registrado.")
            loadAccounts()
        }
    }

    /** Alta/edición de pago de servicio (vencimiento + recordatorio). */
    fun saveBill(
        id: String?,
        name: String,
        estimatedAmount: Double,
        dueDay: Int,
        dueMonth: Int = 1,
        frequency: String
    ) {
        val cleanName = name.trim().ifBlank { "Servicio" }
        // Editar no borra el estado de pagado.
        val previous = _uiState.value.bills.firstOrNull { it.id == id }
        val cleanFrequency =
            if (frequency == "bimonthly") "bimonthly"
            else if (frequency == "yearly") "yearly"
            else "monthly"
        val bill = ServiceBillRow(
            id = id ?: "bill-${System.currentTimeMillis()}",
            name = cleanName,
            estimatedAmount = if (estimatedAmount < 0.0) 0.0 else estimatedAmount,
            dueDay = dueDay.coerceIn(1, 31),
            dueMonth = dueMonth.coerceIn(1, 12),
            frequency = cleanFrequency,
            lastPaidDueIso = previous?.lastPaidDueIso
        )
        viewModelScope.launch {
            runCatching { billStore.upsert(bill) }
            _uiState.value = _uiState.value.copy(info = "${bill.name} programado.")
            loadAccounts()
        }
    }

    /** Marca el vencimiento actual como pagado: ya no llegan sus avisos. */
    fun markBillPaid(id: String, dueIso: String) {
        viewModelScope.launch {
            runCatching { billStore.markPaid(id, dueIso) }
            _uiState.value = _uiState.value.copy(info = "Servicio marcado como pagado.")
            loadAccounts()
        }
    }

    /** Quita el estado de pagado (vuelven los avisos del vencimiento). */
    fun unmarkBillPaid(id: String) {
        viewModelScope.launch {
            runCatching { billStore.clearPaid(id) }
            loadAccounts()
        }
    }

    fun deleteBill(id: String) {
        viewModelScope.launch {
            runCatching { billStore.delete(id) }
            loadAccounts()
        }
    }

    // === Región D8 (deudas personales): alta/edición, abono parcial, liquidar. ===
    /** Crea o actualiza una deuda personal ("me deben" o "yo debo"). */
    fun savePersonDebt(
        id: String?,
        nombre: String,
        monto: Double,
        esDeudaMia: Boolean,
        fechaIso: String
    ) {
        val cleanName = nombre.trim().ifBlank { "Sin nombre" }
        if (monto <= 0.0) return
        val previous = _uiState.value.personDebts.firstOrNull { it.id == id }
        val debt = PersonDebt(
            id = id ?: "pdebt-${System.currentTimeMillis()}",
            nombre = cleanName,
            monto = monto,
            esDeudaMia = esDeudaMia,
            fechaIso = fechaIso,
            abonos = previous?.abonos ?: emptyList()
        )
        viewModelScope.launch {
            runCatching { personDebtStore.upsert(debt) }
            _uiState.value = _uiState.value.copy(info = "Deuda guardada.")
            loadAccounts()
        }
    }

    fun deletePersonDebt(id: String) {
        viewModelScope.launch {
            runCatching { personDebtStore.delete(id) }
            loadAccounts()
        }
    }

    /** Abono parcial contra una deuda personal. */
    fun addPersonAbono(debtId: String, amount: Double) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            runCatching {
                personDebtStore.addAbono(
                    debtId,
                    amount,
                    java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
                )
            }
            _uiState.value = _uiState.value.copy(info = "Abono registrado.")
            loadAccounts()
        }
    }

    /** Liquida una deuda (la quita de la lista). */
    fun liquidarPersonDebt(id: String) {
        viewModelScope.launch {
            runCatching { personDebtStore.delete(id) }
            _uiState.value = _uiState.value.copy(info = "Deuda liquidada.")
            loadAccounts()
        }
    }
    // === Fin región D8. ===

    // === Región A (MSI): alta manual o desde un cargo, liquidar, eliminar. ===
    /** Crea un plan MSI manual (meses validados contra lo que ofrecen los bancos). */
    fun saveMsiPlan(
        cardId: String,
        concepto: String,
        montoTotal: Double,
        meses: Int,
        primerCorteIso: String
    ) {
        val cleanConcept = concepto.trim().ifBlank { "Compra a MSI" }
        if (montoTotal <= 0.0) return
        val mesesValidos = if (com.fintrack.app.domain.MsiPlanner.MESES_VALIDOS.contains(meses)) {
            meses
        } else 12
        val plan = MsiPlan(
            id = "msi-${System.currentTimeMillis()}",
            cardId = cardId,
            concepto = cleanConcept,
            montoTotal = montoTotal,
            meses = mesesValidos,
            primerCorteIso = primerCorteIso
        )
        viewModelScope.launch {
            runCatching { msiStore.upsert(plan) }
            _uiState.value = _uiState.value.copy(info = "Plan MSI creado.")
            loadAccounts()
        }
    }

    /**
     * "Pasar a MSI" desde un cargo del ciclo actual: crea el plan con el monto
     * del cargo (el cargo tagueado original cubre la verificación, sin duplicar).
     */
    fun pasarCargoAMsi(cardId: String, concepto: String, monto: Double, meses: Int) {
        val card = _uiState.value.cards.firstOrNull { it.id == cardId } ?: return
        val hoy = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        // Primer corte real: el próximo corte de la tarjeta.
        val next = com.fintrack.app.domain.CreditCardPlanner.nextCutoff(card.cutoffDay, hoy)
        saveMsiPlan(cardId, concepto, monto, meses, next.toString())
    }

    fun liquidarMsi(id: String) {
        viewModelScope.launch {
            runCatching { msiStore.liquidar(id) }
            _uiState.value = _uiState.value.copy(info = "Plan MSI liquidado.")
            loadAccounts()
        }
    }

    fun deleteMsi(id: String) {
        viewModelScope.launch {
            runCatching { msiStore.delete(id) }
            loadAccounts()
        }
    }
    // === Fin región A. ===

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
                loadAccounts()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyErrorMessage(e)
                )
            }
        }
    }
}
