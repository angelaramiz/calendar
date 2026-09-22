package com.fintrack.app.data

import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository

/**
 * Sincroniza la bandeja de salida en orden. Para ante el primer fallo
 * (conserva el resto para el siguiente intento). Devuelve cuántas subieron.
 */
class PendingOpSync(
    private val opStore: PendingOpStore,
    private val transactionRepository: TransactionRepository,
    private val patternRepository: PatternRepository,
    private val walletStore: WalletStore,
    private val creditCardStore: CreditCardStore,
    private val linkStore: PatternLinkStore
) {

    suspend fun sync(userId: String): Int {
        val queued = runCatching { opStore.snapshot() }.getOrNull() ?: return 0
        if (queued.isEmpty()) return 0
        val done = mutableListOf<PendingOp>()
        for (op in queued) {
            val ok = runCatching { apply(userId, op) }.getOrDefault(false)
            if (!ok) break
            done.add(op)
        }
        if (done.isNotEmpty()) runCatching { opStore.removeAll(done) }
        return done.size
    }

    private suspend fun apply(userId: String, op: PendingOp): Boolean {
        return when (op.kind) {
            PendingOpKind.TX_INSERT -> {
                val p = PendingOpCodec.payload<TxInsertPayload>(op) ?: return true
                val saved = transactionRepository.insertTransaction(userId, p.tx)
                p.walletId?.let { walletStore.setOverride("tx:${saved.id}", it) }
                p.cardId?.let { creditCardStore.setCharge("tx:${saved.id}", it) }
                true
            }
            PendingOpKind.TX_UPDATE -> {
                val p = PendingOpCodec.payload<TxUpdatePayload>(op) ?: return true
                transactionRepository.updateTransaction(userId, p.id, p.tx)
                true
            }
            PendingOpKind.TX_DELETE -> {
                val p = PendingOpCodec.payload<TxIdPayload>(op) ?: return true
                transactionRepository.deleteTransaction(userId, p.id)
                true
            }
            PendingOpKind.MOV_INSERT -> {
                val p = PendingOpCodec.payload<MovInsertPayload>(op) ?: return true
                val saved = patternRepository.addManualMovement(
                    userId = userId,
                    dateIso = p.dateIso,
                    isIncome = p.isIncome,
                    title = p.title,
                    description = p.description,
                    category = p.category,
                    amount = p.amount
                )
                p.walletId?.let { walletStore.setOverride("mov:${saved.id}", it) }
                p.cardId?.let { creditCardStore.setCharge("mov:${saved.id}", it) }
                true
            }
            PendingOpKind.MOV_CONFIRM -> {
                val p = PendingOpCodec.payload<MovConfirmPayload>(op) ?: return true
                val date = runCatching { java.time.LocalDate.parse(p.dateIso) }
                    .getOrNull() ?: return true
                val pattern = com.fintrack.app.domain.Pattern(
                    id = p.patternId,
                    name = p.name,
                    type = if (p.isIncome) "INCOME" else "EXPENSE",
                    baseAmount = p.baseAmount,
                    frequency = "monthly",
                    startDate = date,
                    category = p.category,
                    description = p.description
                )
                val saved = patternRepository.confirmOccurrence(
                    userId,
                    com.fintrack.app.domain.Occurrence(date = date, pattern = pattern),
                    p.actualAmount,
                    p.dateIso
                )
                p.cardId?.let { creditCardStore.setCharge("mov:${saved.id}", it) }
                true
            }
            PendingOpKind.PATTERN_INSERT -> {
                val p = PendingOpCodec.payload<PatternOpPayload>(op) ?: return true
                val saved = patternRepository.insertPattern(
                    userId = userId,
                    isIncome = p.isIncome,
                    name = p.name,
                    description = p.description,
                    category = p.category,
                    baseAmount = p.baseAmount,
                    frequency = p.frequency,
                    startDateIso = p.startDateIso,
                    endDateIso = p.endDateIso
                )
                linkStore.setLink(saved.id, p.linkKind, p.linkCardId)
                true
            }
            PendingOpKind.PATTERN_UPDATE -> {
                val p = PendingOpCodec.payload<PatternOpPayload>(op) ?: return true
                val id = p.patternId ?: return true
                patternRepository.updatePattern(
                    patternId = id,
                    isIncome = p.isIncome,
                    name = p.name,
                    description = p.description,
                    category = p.category,
                    baseAmount = p.baseAmount,
                    frequency = p.frequency,
                    startDateIso = p.startDateIso,
                    endDateIso = p.endDateIso
                )
                linkStore.setLink(id, p.linkKind, p.linkCardId)
                true
            }
            PendingOpKind.PATTERN_DEACTIVATE -> {
                val p = PendingOpCodec.payload<PatternOpPayload>(op) ?: return true
                val id = p.patternId ?: return true
                patternRepository.deactivatePattern(patternId = id, isIncome = p.isIncome)
                true
            }
            else -> true
        }
    }
}
