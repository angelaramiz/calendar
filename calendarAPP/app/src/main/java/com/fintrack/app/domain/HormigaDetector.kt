package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class HormigaReport(
    /** Palabra detectada ("oxxo", "7-eleven"...). */
    val merchant: String,
    val total: Double,
    val visits: Int,
    val from: LocalDate,
    val to: LocalDate
)

/**
 * Fuga hormiga semanal (D11): gasto chico y frecuente por comercio.
 * Puro y testeable; el aviso del lunes sale desde ReminderCheck.
 */
object HormigaDetector {

    private fun TransactionEntity.isExpense(): Boolean = kind == TxKind.EXPENSE

    fun labelOf(tx: TransactionEntity): String =
        (tx.merchant?.trim()?.takeIf { it.isNotBlank() } ?: tx.description.trim())

    /**
     * Mejor candidato de la semana [from..to]: la palabra con mayor gasto
     * acumulado entre [keywords]. null si no hay visitas.
     */
    fun topHormiga(
        transactions: List<TransactionEntity>,
        from: LocalDate,
        to: LocalDate,
        keywords: List<String> = listOf("oxxo", "7-eleven", "seven eleven", "extra", "kiosko")
    ): HormigaReport? {
        val totals = mutableMapOf<String, Double>()
        val visits = mutableMapOf<String, Int>()
        transactions.filter { it.isExpense() }.forEach { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneOffset.UTC).toLocalDate()
            if (date < from || date > to) return@forEach
            val norm = labelOf(tx).lowercase()
            val hit = keywords.firstOrNull { it in norm } ?: return@forEach
            totals[hit] = (totals[hit] ?: 0.0) + tx.amount
            visits[hit] = (visits[hit] ?: 0) + 1
        }
        val best = totals.maxByOrNull { it.value } ?: return null
        return HormigaReport(
            merchant = best.key,
            total = best.value,
            visits = visits[best.key] ?: 0,
            from = from,
            to = to
        )
    }
}
