package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

data class SubscriptionCandidate(
    val merchant: String,
    val amount: Double,
    val category: String,
    /** Meses distintos con cargo, ordenados. */
    val months: List<YearMonth>,
    val dayOfMonth: Int,
    /** Próximo cobro esperado (mismo día, mes siguiente al último). */
    val nextExpected: LocalDate
)

/**
 * Detecta suscripciones: cargos de gasto con mismo comercio y mismo monto en
 * 3+ meses distintos dentro de los últimos 6. Excluye las ya cubiertas por un
 * patrón activo (mismo nombre+monto en frecuencia mensual/bimestral).
 */
object SubscriptionDetector {

    const val MIN_MONTHS = 3
    const val LOOKBACK_MONTHS = 6L

    fun detect(
        transactions: List<TransactionEntity>,
        patterns: List<Pattern>,
        referenceMonth: YearMonth = YearMonth.now(ZoneOffset.UTC)
    ): List<SubscriptionCandidate> {
        val window = (0 until LOOKBACK_MONTHS).map { referenceMonth.minusMonths(it) }.toSet()
        val groups = mutableMapOf<Pair<String, Double>, MutableList<TransactionEntity>>()
        transactions.forEach { tx ->
            if (!tx.isExpense()) return@forEach
            val key = tx.merchant?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                ?: tx.description.trim().lowercase().takeIf { it.isNotBlank() }
                ?: return@forEach
            val date = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneOffset.UTC).toLocalDate()
            if (YearMonth.from(date) !in window) return@forEach
            groups.getOrPut(key to tx.amount) { mutableListOf() }.add(tx)
        }
        return groups.mapNotNull { (_, items) ->
            val months = items.map { YearMonth.from(txDate(it)) }.distinct().sorted()
            if (months.size < MIN_MONTHS) return@mapNotNull null
            val first = items.first()
            val label = first.merchant?.trim()?.takeIf { it.isNotBlank() }
                ?: first.description.trim()
            if (isCoveredByPattern(patterns, label, first.amount)) return@mapNotNull null
            val day = items.map { txDate(it).dayOfMonth }
                .groupingBy { it }.eachCount().maxBy { it.value }.key
            val last = months.last()
            val nextMonth = last.plusMonths(1)
            val nextDay = minOf(day, nextMonth.lengthOfMonth())
            SubscriptionCandidate(
                merchant = label,
                amount = first.amount,
                category = first.category.ifBlank { "Otros" },
                months = months,
                dayOfMonth = day,
                nextExpected = LocalDate.of(nextMonth.year, nextMonth.month, nextDay)
            )
        }.sortedBy { it.nextExpected }
    }

    private fun TransactionEntity.isExpense(): Boolean =
        !(type.equals("INCOME", ignoreCase = true) || type.equals("ingreso", ignoreCase = true))

    private fun txDate(tx: TransactionEntity): LocalDate =
        Instant.ofEpochMilli(tx.timestamp).atZone(ZoneOffset.UTC).toLocalDate()

    private fun isCoveredByPattern(
        patterns: List<Pattern>,
        label: String,
        amount: Double
    ): Boolean {
        val norm = label.lowercase()
        return patterns.any { p ->
            p.active &&
                (p.frequency == "monthly" || p.frequency == "bimonthly") &&
                p.type.equals("EXPENSE", ignoreCase = true) &&
                (p.name.lowercase().contains(norm) || norm.contains(p.name.lowercase())) &&
                kotlin.math.abs(p.baseAmount - amount) <= maxOf(1.0, amount * 0.01)
        }
    }
}
