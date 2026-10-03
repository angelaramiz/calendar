package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

data class CategoryDiff(
    val category: String,
    val current: Double,
    val previous: Double
) {
    val delta: Double get() = current - previous

    /** null cuando no hay base previa (division entre cero). */
    val percent: Double? get() = if (previous <= 0.0) null else (delta / previous) * 100.0
}

/**
 * Heatmap de gasto (C6): 100% lectura sobre flujos existentes.
 * Quintiles 0..4 (0 = gasto bajo, 4 = gasto alto); dias sin gasto = 0.
 */
object SpendingHeatmap {

    private fun TransactionEntity.isExpense(): Boolean = kind == TxKind.EXPENSE

    private fun txDate(tx: TransactionEntity): LocalDate =
        Instant.ofEpochMilli(tx.timestamp).atZone(ZoneOffset.UTC).toLocalDate()

    /** Gasto por dia del mes (solo gastos, misma base UTC que BudgetPlanner). */
    fun dailyExpense(
        transactions: List<TransactionEntity>,
        month: YearMonth
    ): Map<LocalDate, Double> {
        val out = mutableMapOf<LocalDate, Double>()
        transactions.filter { it.isExpense() }.forEach { tx ->
            val date = txDate(tx)
            if (YearMonth.from(date) != month) return@forEach
            out[date] = (out[date] ?: 0.0) + tx.amount
        }
        return out
    }

    /**
     * Quintil 0..4 de un gasto dentro de la lista ordenada de gastos
     * distintos de cero del mes. 0.0 o menos siempre es 0.
     */
    fun quintileOf(expense: Double, sortedNonZero: List<Double>): Int {
        if (expense <= 0.0 || sortedNonZero.isEmpty()) return 0
        val rank = sortedNonZero.count { it <= expense } - 1
        return (rank.coerceAtLeast(0) * 5 / sortedNonZero.size).coerceIn(0, 4)
    }

    /** Quintil por dia a partir del gasto diario del mes. */
    fun quintiles(daily: Map<LocalDate, Double>): Map<LocalDate, Int> {
        val nonZero = daily.values.filter { it > 0.0 }.sorted()
        if (nonZero.isEmpty()) return daily.mapValues { 0 }
        return daily.mapValues { (_, expense) -> quintileOf(expense, nonZero) }
    }

    /**
     * Comparativa "este mes vs anterior" por categoria.
     * Omite categorias sin gasto en ambos meses; ordena por gasto actual desc.
     */
    fun monthCategoryDiff(
        current: Map<String, Double>,
        previous: Map<String, Double>
    ): List<CategoryDiff> =
        (current.keys + previous.keys)
            .map { cat -> CategoryDiff(cat, current[cat] ?: 0.0, previous[cat] ?: 0.0) }
            .filter { it.current > 0.0 || it.previous > 0.0 }
            .sortedByDescending { it.current }
}
