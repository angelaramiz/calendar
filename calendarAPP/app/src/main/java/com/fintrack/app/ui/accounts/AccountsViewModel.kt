package com.fintrack.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.CardPayment
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.ServiceBillRow
import com.fintrack.app.data.ServiceBillStore
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.WalletStore
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
    private val billStore: ServiceBillStore
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
                _uiState.value = _uiState.value.copy(
                    subscriptions = subscriptions,
                    wallets = wallets,
                    cards = cards,
                    cardCharges = cardCharges,
                    cardPayments = cardPayments,
                    bills = bills,
                    recentMovements = recentMovements,
                    allTransactions = transactions,
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
            val wallets = runCatching {
                walletStore.ensureDefaults()
                walletStore.snapshot()
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                wallets = wallets,
                info = "Cuenta agregada."
            )
        }
    }

    /**
     * Quita una cuenta de débito. Si era fija, no vuelve a aparecer
     * (igual que en Mis billeteras).
     */
    fun deleteWallet(id: String) {
        viewModelScope.launch {
            runCatching { walletStore.deleteWallet(id) }
            val wallets = runCatching { walletStore.snapshot() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(wallets = wallets)
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
    fun saveBill(id: String?, name: String, estimatedAmount: Double, dueDay: Int, frequency: String) {
        val cleanName = name.trim().ifBlank { "Servicio" }
        val bill = ServiceBillRow(
            id = id ?: "bill-${System.currentTimeMillis()}",
            name = cleanName,
            estimatedAmount = if (estimatedAmount < 0.0) 0.0 else estimatedAmount,
            dueDay = dueDay.coerceIn(1, 31),
            frequency = if (frequency == "bimonthly") "bimonthly" else "monthly"
        )
        viewModelScope.launch {
            runCatching { billStore.upsert(bill) }
            _uiState.value = _uiState.value.copy(info = "${bill.name} programado.")
            loadAccounts()
        }
    }

    fun deleteBill(id: String) {
        viewModelScope.launch {
            runCatching { billStore.delete(id) }
            loadAccounts()
        }
    }

    /** Convierte una suscripción detectada en recurrente mensual de gasto. */
    fun createSubscriptionPattern(candidate: SubscriptionCandidate) {
        if (userId.isEmpty()) return
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
