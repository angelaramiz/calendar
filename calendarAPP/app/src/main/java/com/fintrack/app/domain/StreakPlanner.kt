package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Rachas de quincenas bajo tope por categoria (D11). Puro y testeable;
 * [StreakStore] solo persiste el mapa resultante.
 */
object StreakPlanner {

    /** "2026-09-Q1" (dias 1-15) o "2026-09-Q2" (16-fin). */
    fun periodKey(date: LocalDate): String =
        "${date.year}-${date.monthValue.toString().padStart(2, '0')}-" +
            if (date.dayOfMonth <= 15) "Q1" else "Q2"

    /** Rango cerrado de la quincena que contiene [date]. */
    fun fortnightRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val month = YearMonth.from(date)
        return if (date.dayOfMonth <= 15) {
            month.atDay(1) to month.atDay(15)
        } else {
            month.atDay(16) to month.atEndOfMonth()
        }
    }

    /**
     * Quincena que cierra hoy (para registrar la racha una sola vez):
     * dia 15 -> Q1 (1..15); ultimo dia del mes -> Q2 (16..fin).
     * null cualquier otro dia.
     */
    fun closingFortnight(today: LocalDate): Pair<LocalDate, LocalDate>? {
        val month = YearMonth.from(today)
        return when {
            today.dayOfMonth == 15 -> month.atDay(1) to month.atDay(15)
            today == month.atEndOfMonth() -> month.atDay(16) to month.atEndOfMonth()
            else -> null
        }
    }

    private fun TransactionEntity.isExpense(): Boolean = kind == TxKind.EXPENSE

    /**
     * Categoria -> true si cerro la quincena [from..to] sin pasar su tope.
     * Solo categorias con tope > 0.
     */
    fun underCaps(
        transactions: List<TransactionEntity>,
        caps: Map<String, Double>,
        from: LocalDate,
        to: LocalDate
    ): Map<String, Boolean> {
        val spent = mutableMapOf<String, Double>()
        transactions.filter { it.isExpense() }.forEach { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneOffset.UTC).toLocalDate()
            if (date < from || date > to) return@forEach
            val cat = tx.category.ifBlank { "Otros" }
            spent[cat] = (spent[cat] ?: 0.0) + tx.amount
        }
        return caps.filterValues { it > 0.0 }
            .mapValues { (cat, cap) -> (spent[cat] ?: 0.0) <= cap }
    }

    /**
     * Avanza las rachas con el resultado de una quincena cerrada.
     * Idempotente por periodo: si [period] == [lastPeriod] no cambia nada.
     * Categoria bajo tope suma 1; la que se pasa vuelve a 0.
     */
    fun nextStreaks(
        current: Map<String, Int>,
        lastPeriod: String?,
        period: String,
        under: Map<String, Boolean>
    ): Pair<Map<String, Int>, String> {
        if (period == lastPeriod) return current to (lastPeriod ?: period)
        val next = current.toMutableMap()
        under.forEach { (cat, ok) ->
            next[cat] = if (ok) (next[cat] ?: 0) + 1 else 0
        }
        return next to period
    }
}
