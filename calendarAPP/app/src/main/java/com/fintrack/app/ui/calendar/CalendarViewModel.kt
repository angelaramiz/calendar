package com.fintrack.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.MovConfirmPayload
import com.fintrack.app.data.MovInsertPayload
import com.fintrack.app.data.PatternOpPayload
import com.fintrack.app.data.PendingOp
import com.fintrack.app.data.PendingOpCodec
import com.fintrack.app.data.PendingOpKind
import com.fintrack.app.data.PendingOpStore
import com.fintrack.app.data.WalletRow
import com.fintrack.app.data.WalletStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.MovementRow
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.MonthSummary
import com.fintrack.app.domain.Occurrence
import com.fintrack.app.domain.Pattern
import com.fintrack.app.domain.PatternExpander
import com.fintrack.app.domain.PatternValidator
import com.fintrack.app.domain.computeMonthSummary
import com.fintrack.app.domain.toLocalDateUtc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayData(
    val date: LocalDate,
    val projected: List<Occurrence> = emptyList(),
    val confirmed: List<MovementRow> = emptyList(),
    /** Registros de Inicio (fintrack_transactions) caídos en este día. */
    val quick: List<TransactionEntity> = emptyList()
)

/** Balance actual del mes: confirmados (movimientos + Inicio). */
data class CalendarBalance(
    val income: Double = 0.0,
    val expense: Double = 0.0
) {
    val balance: Double get() = income - expense
}

data class CalendarUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val days: Map<LocalDate, DayData> = emptyMap(),
    val monthSummary: MonthSummary = MonthSummary(),
    val balance: CalendarBalance = CalendarBalance(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val confirmTarget: Occurrence? = null,
    val addTarget: LocalDate? = null,
    val showPatternDialog: Boolean = false,
    /** Patrón en edición (null = creando uno nuevo). */
    val patternEditTarget: Pattern? = null,
    val isSaving: Boolean = false,
    val needsLogin: Boolean = false,
    /** Billeteras para el selector del formulario. */
    val wallets: List<WalletRow> = emptyList()
)

private fun MovementRow.isIncomeRow(): Boolean =
    type.equals("ingreso", ignoreCase = true)

private fun TransactionEntity.isIncomeTx(): Boolean =
    type.equals("INCOME", ignoreCase = true) ||
        type.equals("ingreso", ignoreCase = true)

class CalendarViewModel(
    private val patternRepository: PatternRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val pendingOpStore: PendingOpStore,
    private val walletStore: WalletStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val userId get() = authRepository.currentUserId ?: ""

    init {
        loadMonth(YearMonth.now())
        viewModelScope.launch {
            val wallets = runCatching {
                walletStore.ensureDefaults()
                walletStore.snapshot()
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(wallets = wallets)
        }
    }

    fun loadMonth(yearMonth: YearMonth) {
        if (userId.isEmpty()) {
            _uiState.value = _uiState.value.copy(needsLogin = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, yearMonth = yearMonth)
            try {
                val from = yearMonth.atDay(1)
                val to = yearMonth.atEndOfMonth()

                val income = patternRepository.getIncomePatterns(userId)
                    .mapNotNull { it.toDomain("INCOME") }
                val expense = patternRepository.getExpensePatterns(userId)
                    .mapNotNull { it.toDomain("EXPENSE") }
                val movements = patternRepository.getMovementsForMonth(
                    userId, from.toString(), to.toString()
                )

                val confirmedKeys = movements
                    .filter { it.income_pattern_id != null || it.expense_pattern_id != null }
                    .map {
                        "${it.income_pattern_id ?: it.expense_pattern_id}_${it.date}"
                    }.toSet()

                val projected = (income + expense)
                    .flatMap { PatternExpander.expand(it, from, to) }
                    .filter { occ ->
                        val key = "${occ.pattern.id}_${occ.date}"
                        !confirmedKeys.contains(key)
                    }
                val projectedByDate = projected.groupBy { it.date }

                val confirmedByDate = movements.groupBy {
                    runCatching { LocalDate.parse(it.date) }.getOrNull()
                }.filterKeys { it != null }.mapKeys { it.key!! }

                // Registros de Inicio según su fecha (timestamp UTC).
                val quickInMonth = transactionRepository.getTransactions(userId)
                    .filter { it.timestamp.toLocalDateUtc().let { d -> !d.isBefore(from) && !d.isAfter(to) } }
                val quickByDate = quickInMonth.groupBy { it.timestamp.toLocalDateUtc() }

                val allDates = (projectedByDate.keys + confirmedByDate.keys + quickByDate.keys)
                    .associateWith { date ->
                        DayData(
                            date = date,
                            projected = projectedByDate[date].orEmpty(),
                            confirmed = confirmedByDate[date].orEmpty(),
                            quick = quickByDate[date].orEmpty()
                        )
                    }

                val confirmedFlat = confirmedByDate.values.flatten()
                val balance = CalendarBalance(
                    income = confirmedFlat.filter { it.isIncomeRow() }.sumOf { it.confirmed_amount } +
                        quickInMonth.filter { it.isIncomeTx() }.sumOf { it.amount },
                    expense = confirmedFlat.filter { !it.isIncomeRow() }.sumOf { it.confirmed_amount } +
                        quickInMonth.filter { !it.isIncomeTx() }.sumOf { it.amount }
                )

                _uiState.value = _uiState.value.copy(
                    days = allDates,
                    monthSummary = computeMonthSummary(
                        projected,
                        confirmedFlat
                    ),
                    balance = balance,
                    isLoading = false,
                    needsLogin = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun prevMonth() = loadMonth(_uiState.value.yearMonth.minusMonths(1))
    fun nextMonth() = loadMonth(_uiState.value.yearMonth.plusMonths(1))

    fun retry() = loadMonth(_uiState.value.yearMonth)

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun askConfirm(occurrence: Occurrence) {
        _uiState.value = _uiState.value.copy(confirmTarget = occurrence)
    }

    fun dismissConfirm() {
        _uiState.value = _uiState.value.copy(confirmTarget = null)
    }

    fun showAddMovement(date: LocalDate) {
        _uiState.value = _uiState.value.copy(addTarget = date)
    }

    fun dismissAddMovement() {
        _uiState.value = _uiState.value.copy(addTarget = null)
    }

    fun showPatternDialog() {
        _uiState.value = _uiState.value.copy(
            showPatternDialog = true,
            patternEditTarget = null,
            error = null
        )
    }

    /** Abre el diálogo precargado para editar un recurrente existente. */
    fun showPatternEdit(pattern: Pattern) {
        _uiState.value = _uiState.value.copy(
            showPatternDialog = true,
            patternEditTarget = pattern,
            error = null
        )
    }

    fun dismissPatternDialog() {
        _uiState.value = _uiState.value.copy(
            showPatternDialog = false,
            patternEditTarget = null
        )
    }

    /** Crea o actualiza un recurrente validado y recarga el mes de su inicio. */
    fun savePattern(
        isIncome: Boolean,
        name: String,
        description: String,
        category: String,
        baseAmount: Double,
        frequency: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ) {
        val validationError = PatternValidator.validate(
            name, baseAmount, frequency, startDate, endDate
        )
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(error = validationError)
            return
        }
        if (startDate == null) return
        val editTarget = _uiState.value.patternEditTarget
        viewModelScope.launch {
            // Sin sesión: a la bandeja (se sube al entrar).
            if (authRepository.ensureSession() == null || userId.isEmpty()) {
                enqueueOp(
                    if (editTarget != null) PendingOpKind.PATTERN_UPDATE
                    else PendingOpKind.PATTERN_INSERT,
                    PatternOpPayload.serializer(),
                    PatternOpPayload(
                        patternId = editTarget?.id,
                        isIncome = editTarget?.type == "INCOME" || (editTarget == null && isIncome),
                        name = name.trim(),
                        description = description.trim(),
                        category = category.ifBlank { "Otros" },
                        baseAmount = baseAmount,
                        frequency = frequency,
                        startDateIso = startDate.toString(),
                        endDateIso = endDate?.toString()
                    )
                )
                _uiState.value = _uiState.value.copy(
                    showPatternDialog = false,
                    patternEditTarget = null,
                    isSaving = false,
                    error = "Sin conexión: el recurrente se guardará al entrar."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                if (editTarget != null) {
                    patternRepository.updatePattern(
                        patternId = editTarget.id,
                        isIncome = editTarget.type == "INCOME",
                        name = name.trim(),
                        description = description.trim(),
                        category = category.ifBlank { "Otros" },
                        baseAmount = baseAmount,
                        frequency = frequency,
                        startDateIso = startDate.toString(),
                        endDateIso = endDate?.toString()
                    )
                } else {
                    patternRepository.insertPattern(
                        userId = userId,
                        isIncome = isIncome,
                        name = name.trim(),
                        description = description.trim(),
                        category = category.ifBlank { "Otros" },
                        baseAmount = baseAmount,
                        frequency = frequency,
                        startDateIso = startDate.toString(),
                        endDateIso = endDate?.toString()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    showPatternDialog = false,
                    patternEditTarget = null,
                    isSaving = false,
                    selectedDate = startDate
                )
                loadMonth(YearMonth.from(startDate))
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(
                        if (editTarget != null) PendingOpKind.PATTERN_UPDATE
                        else PendingOpKind.PATTERN_INSERT,
                        PatternOpPayload.serializer(),
                        PatternOpPayload(
                            patternId = editTarget?.id,
                            isIncome = editTarget?.type == "INCOME" || (editTarget == null && isIncome),
                            name = name.trim(),
                            description = description.trim(),
                            category = category.ifBlank { "Otros" },
                            baseAmount = baseAmount,
                            frequency = frequency,
                            startDateIso = startDate.toString(),
                            endDateIso = endDate?.toString()
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        showPatternDialog = false,
                        patternEditTarget = null,
                        isSaving = false,
                        error = "Sin conexión: el recurrente se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
                }
            }
        }
    }

    /** Desactiva el recurrente en edición: no se proyecta más, se conserva el historial. */
    fun deactivatePattern() {
        val target = _uiState.value.patternEditTarget ?: return
        val payload = PatternOpPayload(
            patternId = target.id,
            isIncome = target.type == "INCOME"
        )
        viewModelScope.launch {
            if (authRepository.ensureSession() == null || userId.isEmpty()) {
                enqueueOp(
                    PendingOpKind.PATTERN_DEACTIVATE,
                    PatternOpPayload.serializer(),
                    payload
                )
                _uiState.value = _uiState.value.copy(
                    showPatternDialog = false,
                    patternEditTarget = null,
                    isSaving = false,
                    error = "Sin conexión: la baja se guardará al entrar."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                patternRepository.deactivatePattern(
                    patternId = target.id,
                    isIncome = target.type == "INCOME"
                )
                _uiState.value = _uiState.value.copy(
                    showPatternDialog = false,
                    patternEditTarget = null,
                    isSaving = false
                )
                loadMonth(_uiState.value.yearMonth)
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.PATTERN_DEACTIVATE,
                        PatternOpPayload.serializer(),
                        payload
                    )
                    _uiState.value = _uiState.value.copy(
                        showPatternDialog = false,
                        patternEditTarget = null,
                        isSaving = false,
                        error = "Sin conexión: la baja se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
                }
            }
        }
    }

    fun saveManualMovement(
        date: LocalDate,
        isIncome: Boolean,
        title: String,
        category: String,
        amount: Double,
        description: String,
        walletId: String? = null
    ) {
        if (amount <= 0.0) return
        val cleanTitle = title.ifBlank { category }
        val cleanCategory = category.ifBlank { "Otros" }
        viewModelScope.launch {
            val uid = authRepository.ensureSession()
            if (uid == null || userId.isEmpty()) {
                enqueueOp(
                    PendingOpKind.MOV_INSERT,
                    MovInsertPayload.serializer(),
                    MovInsertPayload(
                        dateIso = date.toString(),
                        isIncome = isIncome,
                        title = cleanTitle,
                        description = description,
                        category = cleanCategory,
                        amount = amount,
                        walletId = walletId
                    )
                )
                _uiState.value = _uiState.value.copy(
                    addTarget = null,
                    isSaving = false,
                    selectedDate = date,
                    error = "Sin conexión: el movimiento se guardará al entrar."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val saved = patternRepository.addManualMovement(
                    userId = userId,
                    dateIso = date.toString(),
                    isIncome = isIncome,
                    title = cleanTitle,
                    description = description,
                    category = cleanCategory,
                    amount = amount
                )
                walletId?.let { runCatching { walletStore.setOverride("mov:${saved.id}", it) } }
                _uiState.value = _uiState.value.copy(
                    addTarget = null,
                    isSaving = false,
                    selectedDate = date
                )
                loadMonth(YearMonth.from(date))
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.MOV_INSERT,
                        MovInsertPayload.serializer(),
                        MovInsertPayload(
                            dateIso = date.toString(),
                            isIncome = isIncome,
                            title = cleanTitle,
                            description = description,
                            category = cleanCategory,
                            amount = amount,
                            walletId = walletId
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        addTarget = null,
                        isSaving = false,
                        selectedDate = date,
                        error = "Sin conexión: el movimiento se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
                }
            }
        }
    }

    fun confirmOccurrence(occurrence: Occurrence, actualAmount: Double) {
        val pattern = occurrence.pattern
        viewModelScope.launch {
            if (authRepository.ensureSession() == null || userId.isEmpty()) {
                enqueueOp(
                    PendingOpKind.MOV_CONFIRM,
                    MovConfirmPayload.serializer(),
                    MovConfirmPayload(
                        patternId = pattern.id,
                        isIncome = pattern.type == "INCOME",
                        name = pattern.name,
                        description = pattern.description,
                        category = pattern.category,
                        baseAmount = pattern.baseAmount,
                        actualAmount = actualAmount,
                        dateIso = occurrence.date.toString()
                    )
                )
                _uiState.value = _uiState.value.copy(
                    confirmTarget = null,
                    error = "Sin conexión: la confirmación se guardará al entrar."
                )
                return@launch
            }
            try {
                patternRepository.confirmOccurrence(
                    userId, occurrence, actualAmount, occurrence.date.toString()
                )
                _uiState.value = _uiState.value.copy(confirmTarget = null)
                loadMonth(_uiState.value.yearMonth)
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.MOV_CONFIRM,
                        MovConfirmPayload.serializer(),
                        MovConfirmPayload(
                            patternId = pattern.id,
                            isIncome = pattern.type == "INCOME",
                            name = pattern.name,
                            description = pattern.description,
                            category = pattern.category,
                            baseAmount = pattern.baseAmount,
                            actualAmount = actualAmount,
                            dateIso = occurrence.date.toString()
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        confirmTarget = null,
                        error = "Sin conexión: la confirmación se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        confirmTarget = null,
                        error = e.message
                    )
                }
            }
        }
    }

    private suspend fun <T> enqueueOp(
        kind: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        payload: T
    ) {
        runCatching {
            pendingOpStore.enqueue(
                PendingOp(
                    id = "op-${System.currentTimeMillis()}-${(0..9999).random()}",
                    kind = kind,
                    payload = PendingOpCodec.json.encodeToString(serializer, payload)
                )
            )
        }
    }
}
