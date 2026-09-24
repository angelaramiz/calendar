package com.fintrack.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.MovConfirmPayload
import com.fintrack.app.data.MovInsertPayload
import com.fintrack.app.data.MovUpdatePayload
import com.fintrack.app.data.PatternLink
import com.fintrack.app.data.PatternLinkKind
import com.fintrack.app.data.PatternLinkStore
import com.fintrack.app.data.PatternOpPayload
import com.fintrack.app.data.PendingOp
import com.fintrack.app.data.PendingOpCodec
import com.fintrack.app.data.PendingOpKind
import com.fintrack.app.data.PendingOpStore
import com.fintrack.app.data.ServiceBillStore
import com.fintrack.app.data.TxIdPayload
import com.fintrack.app.data.TxUpdatePayload
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
import com.fintrack.app.domain.friendlyErrorMessage
import com.fintrack.app.domain.kind
import com.fintrack.app.domain.toLocalDateIn
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
    /** Movimiento confirmado tocado: ver detalle, editar o eliminar. */
    val detailMov: MovementRow? = null,
    /** Registro de Inicio tocado: ver detalle, editar o eliminar. */
    val detailTx: TransactionEntity? = null,
    val showPatternDialog: Boolean = false,
    /** Patrón en edición (null = creando uno nuevo). */
    val patternEditTarget: Pattern? = null,
    val isSaving: Boolean = false,
    val needsLogin: Boolean = false,
    /** Billeteras para el selector del formulario. */
    val wallets: List<WalletRow> = emptyList(),
    /** Tarjetas de crédito para el tag de gastos. */
    val cards: List<CreditCardRow> = emptyList(),
    /** Override de billetera por "tx:<id>"/"mov:<id>" (editar con su cuenta). */
    val walletOverrides: Map<String, String> = emptyMap(),
    /** Tag de tarjeta por "tx:<id>"/"mov:<id>". */
    val cardCharges: Map<String, String> = emptyMap(),
    /** Clasificación de recurrentes: patternId -> link. */
    val links: Map<String, PatternLink> = emptyMap(),
    /** Avisos del mes (vencimientos y pagos): fecha -> etiquetas. */
    val markers: Map<LocalDate, List<String>> = emptyMap()
)

private fun MovementRow.isIncomeRow(): Boolean = kind?.isIncome == true

private fun TransactionEntity.isIncomeTx(): Boolean = kind?.isIncome == true

class CalendarViewModel(
    private val patternRepository: PatternRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val pendingOpStore: PendingOpStore,
    private val walletStore: WalletStore,
    private val creditCardStore: CreditCardStore,
    private val linkStore: PatternLinkStore,
    private val billStore: ServiceBillStore
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
            val cards = runCatching { creditCardStore.cardsSnapshot() }
                .getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                wallets = wallets,
                cards = cards,
                walletOverrides = runCatching { walletStore.overridesSnapshot() }
                    .getOrDefault(emptyMap()),
                cardCharges = runCatching { creditCardStore.chargesSnapshot() }
                    .getOrDefault(emptyMap())
            )
        }
    }

    fun loadMonth(yearMonth: YearMonth, forceRefresh: Boolean = false) {
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

                // Registros de Inicio según su fecha en hora local (igual que
                // selectedDate): si no, lo de la noche cae en "mañana".
                val zone = java.time.ZoneId.systemDefault()
                val quickInMonth = transactionRepository.getTransactions(userId, forceRefresh)
                    .filter { it.timestamp.toLocalDateIn(zone).let { d -> !d.isBefore(from) && !d.isAfter(to) } }
                val quickByDate = quickInMonth.groupBy { it.timestamp.toLocalDateIn(zone) }

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
                    links = runCatching { linkStore.snapshot() }.getOrDefault(emptyMap()),
                    markers = buildMarkers(from, to),
                    walletOverrides = runCatching { walletStore.overridesSnapshot() }
                        .getOrDefault(emptyMap()),
                    cardCharges = runCatching { creditCardStore.chargesSnapshot() }
                        .getOrDefault(emptyMap()),
                    isLoading = false,
                    needsLogin = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyErrorMessage(e))
            }
        }
    }

    fun prevMonth() = loadMonth(_uiState.value.yearMonth.minusMonths(1))
    fun nextMonth() = loadMonth(_uiState.value.yearMonth.plusMonths(1))

    fun retry() = loadMonth(_uiState.value.yearMonth, forceRefresh = true)

    /**
     * Avisos del mes (capa local, sin crear movimientos): vencimientos de
     * servicios y fechas de pago de tarjetas que caigan en el rango.
     */
    private suspend fun buildMarkers(from: LocalDate, to: LocalDate): Map<LocalDate, List<String>> {
        val markers = mutableMapOf<LocalDate, MutableList<String>>()
        fun add(date: LocalDate, label: String) {
            if (!date.isBefore(from) && !date.isAfter(to)) {
                markers.getOrPut(date) { mutableListOf() }.add(label)
            }
        }
        val bills = runCatching { billStore.snapshot() }.getOrDefault(emptyList())
        bills.forEach { bill ->
            com.fintrack.app.domain.ServiceBills
                .duesInRange(bill.dueDay, bill.frequency, from, to)
                .forEach { add(it, "${bill.name} vence ${it.dayOfMonth}") }
        }
        val cards = runCatching { creditCardStore.cardsSnapshot() }.getOrDefault(emptyList())
        cards.forEach { card ->
            val nextCutoff = com.fintrack.app.domain.CreditCardPlanner
                .nextCutoff(card.cutoffDay, from)
            val payment = com.fintrack.app.domain.CreditCardPlanner
                .paymentFor(nextCutoff, card.paymentDay, card.graceDays)
            add(payment, "Pagar ${card.displayName} (límite ${payment.dayOfMonth})")
            val prevCutoff = com.fintrack.app.domain.CreditCardPlanner
                .lastCutoff(card.cutoffDay, from)
            val prevPayment = com.fintrack.app.domain.CreditCardPlanner
                .paymentFor(prevCutoff, card.paymentDay, card.graceDays)
            if (prevPayment != payment) {
                add(prevPayment, "Pagar ${card.displayName} (límite ${prevPayment.dayOfMonth})")
            }
        }
        return markers
    }

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

    /** Abre el detalle de un movimiento confirmado. */
    fun showMovementDetail(mov: MovementRow) {
        _uiState.value = _uiState.value.copy(detailMov = mov, detailTx = null)
    }

    fun dismissMovementDetail() {
        _uiState.value = _uiState.value.copy(detailMov = null)
    }

    /** Abre el detalle de un registro de Inicio. */
    fun showQuickDetail(tx: TransactionEntity) {
        _uiState.value = _uiState.value.copy(detailTx = tx, detailMov = null)
    }

    fun dismissQuickDetail() {
        _uiState.value = _uiState.value.copy(detailTx = null)
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
        endDate: LocalDate?,
        linkKind: String? = null,
        linkCardId: String? = null
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
        val cleanLinkKind = linkKind?.takeIf {
            it == PatternLinkKind.CREDIT || it == PatternLinkKind.SERVICE ||
                it == PatternLinkKind.SUBSCRIPTION
        }
        val cleanLinkCard = linkCardId.takeIf { cleanLinkKind == PatternLinkKind.CREDIT }
        viewModelScope.launch {
            // Sin sesión: a la bandeja (el link viaja en la operación y se
            // aplica con el id real al sincronizar).
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
                        endDateIso = endDate?.toString(),
                        linkKind = cleanLinkKind,
                        linkCardId = cleanLinkCard
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
                val savedId = if (editTarget != null) {
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
                    editTarget.id
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
                    ).id
                }
                runCatching { linkStore.setLink(savedId, cleanLinkKind, cleanLinkCard) }
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
                            endDateIso = endDate?.toString(),
                            linkKind = cleanLinkKind,
                            linkCardId = cleanLinkCard
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        showPatternDialog = false,
                        patternEditTarget = null,
                        isSaving = false,
                        error = "Sin conexión: el recurrente se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = friendlyErrorMessage(e))
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
                    _uiState.value = _uiState.value.copy(isSaving = false, error = friendlyErrorMessage(e))
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
        walletId: String? = null,
        cardId: String? = null
    ) {
        if (amount <= 0.0) return
        val cleanTitle = title.ifBlank { category }
        val cleanCategory = category.ifBlank { "Otros" }
        val cleanCard = if (isIncome) null else cardId
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
                        walletId = walletId,
                        cardId = cleanCard
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
                cleanCard?.let { runCatching { creditCardStore.setCharge("mov:${saved.id}", it) } }
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
                            walletId = walletId,
                            cardId = cleanCard
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        addTarget = null,
                        isSaving = false,
                        selectedDate = date,
                        error = "Sin conexión: el movimiento se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = friendlyErrorMessage(e))
                }
            }
        }
    }

    fun confirmOccurrence(occurrence: Occurrence, actualAmount: Double) {
        val pattern = occurrence.pattern
        viewModelScope.launch {
            // Si el recurrente es de tarjeta, el movimiento confirmado nace tageado.
            val linkCard = linkCardOf(pattern)
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
                        dateIso = occurrence.date.toString(),
                        cardId = linkCard
                    )
                )
                _uiState.value = _uiState.value.copy(
                    confirmTarget = null,
                    error = "Sin conexión: la confirmación se guardará al entrar."
                )
                return@launch
            }
            try {
                val saved = patternRepository.confirmOccurrence(
                    userId, occurrence, actualAmount, occurrence.date.toString()
                )
                linkCard?.let { runCatching { creditCardStore.setCharge("mov:${saved.id}", it) } }
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
                            dateIso = occurrence.date.toString(),
                            cardId = linkCard
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        confirmTarget = null,
                        error = "Sin conexión: la confirmación se guardará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        confirmTarget = null,
                        error = friendlyErrorMessage(e)
                    )
                }
            }
        }
    }

    /** Edita un registro de Inicio desde Calendario (tags locales + offline). */
    fun updateQuick(
        id: String,
        transaction: TransactionEntity,
        walletId: String? = null,
        cardId: String? = null
    ) {
        viewModelScope.launch {
            if (walletId != null) {
                runCatching { walletStore.setOverride("tx:$id", walletId) }
            } else if (cardId != null) {
                runCatching { walletStore.clearOverride("tx:$id") }
            }
            if (transaction.isIncomeTx()) {
                runCatching { creditCardStore.setCharge("tx:$id", null) }
            } else {
                runCatching { creditCardStore.setCharge("tx:$id", cardId) }
            }
            val uid = authRepository.ensureSession()
            if (uid == null) {
                enqueueOp(
                    PendingOpKind.TX_UPDATE,
                    TxUpdatePayload.serializer(),
                    TxUpdatePayload(id, transaction)
                )
                _uiState.value = _uiState.value.copy(
                    error = "Sin conexión: se modificará al entrar."
                )
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                transactionRepository.updateTransaction(uid, id, transaction)
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.TX_UPDATE,
                        TxUpdatePayload.serializer(),
                        TxUpdatePayload(id, transaction)
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Sin conexión: se modificará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo modificar. ${friendlyErrorMessage(e)}"
                    )
                }
            }
        }
    }

    /** Elimina un registro de Inicio desde Calendario (con offline). */
    fun deleteQuick(id: String) {
        viewModelScope.launch {
            runCatching { walletStore.clearOverride("tx:$id") }
            runCatching { creditCardStore.setCharge("tx:$id", null) }
            val uid = authRepository.ensureSession()
            if (uid == null) {
                enqueueOp(PendingOpKind.TX_DELETE, TxIdPayload.serializer(), TxIdPayload(id))
                _uiState.value = _uiState.value.copy(
                    error = "Sin conexión: se eliminará al entrar."
                )
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
                return@launch
            }
            try {
                transactionRepository.deleteTransaction(uid, id)
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(PendingOpKind.TX_DELETE, TxIdPayload.serializer(), TxIdPayload(id))
                    _uiState.value = _uiState.value.copy(
                        error = "Sin conexión: se eliminará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "No se pudo eliminar: ${e.message?.take(150)}"
                    )
                }
            }
        }
    }

    /** Edita un movimiento confirmado (título, nota, categoría, monto + tags). */
    fun updateMovement(
        id: String,
        title: String,
        description: String,
        category: String,
        amount: Double,
        walletId: String? = null,
        cardId: String? = null
    ) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            if (walletId != null) {
                runCatching { walletStore.setOverride("mov:$id", walletId) }
            }
            if (cardId != null) {
                runCatching { creditCardStore.setCharge("mov:$id", cardId) }
            }
            val uid = authRepository.ensureSession()
            if (uid == null || userId.isEmpty()) {
                enqueueOp(
                    PendingOpKind.MOV_UPDATE,
                    MovUpdatePayload.serializer(),
                    MovUpdatePayload(id, title, description, category, amount, walletId, cardId)
                )
                _uiState.value = _uiState.value.copy(
                    error = "Sin conexión: se modificará al entrar."
                )
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                patternRepository.updateMovement(
                    userId, id, title.ifBlank { category }, description, category, amount
                )
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(
                        PendingOpKind.MOV_UPDATE,
                        MovUpdatePayload.serializer(),
                        MovUpdatePayload(id, title, description, category, amount, walletId, cardId)
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Sin conexión: se modificará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo modificar. ${friendlyErrorMessage(e)}"
                    )
                }
            }
        }
    }

    /** Elimina un movimiento confirmado (con offline). */
    fun deleteMovement(id: String) {
        viewModelScope.launch {
            runCatching { walletStore.clearOverride("mov:$id") }
            runCatching { creditCardStore.setCharge("mov:$id", null) }
            val uid = authRepository.ensureSession()
            if (uid == null || userId.isEmpty()) {
                enqueueOp(PendingOpKind.MOV_DELETE, TxIdPayload.serializer(), TxIdPayload(id))
                _uiState.value = _uiState.value.copy(
                    error = "Sin conexión: se eliminará al entrar."
                )
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
                return@launch
            }
            try {
                patternRepository.deleteMovement(userId, id)
                loadMonth(_uiState.value.yearMonth, forceRefresh = true)
            } catch (e: Exception) {
                if (com.fintrack.app.domain.isRecoverableError(e)) {
                    enqueueOp(PendingOpKind.MOV_DELETE, TxIdPayload.serializer(), TxIdPayload(id))
                    _uiState.value = _uiState.value.copy(
                        error = "Sin conexión: se eliminará al entrar."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "No se pudo eliminar: ${e.message?.take(150)}"
                    )
                }
            }
        }
    }

    /** Alta de billetera propia (con terminación y tipo de cuenta) + recarga. */
    fun addWallet(name: String, last4: String = "", kind: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { walletStore.addWallet(name, last4, kind) }
            val wallets = runCatching {
                walletStore.ensureDefaults()
                walletStore.snapshot()
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(wallets = wallets)
        }
    }

    /** Tarjeta del recurrente si está clasificado como crédito. */
    private suspend fun linkCardOf(pattern: Pattern): String? {
        val link = runCatching { linkStore.snapshot()[pattern.id] }.getOrNull()
            ?: return null
        return link.cardId.takeIf { link.kind == PatternLinkKind.CREDIT }
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
