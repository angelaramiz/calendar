package com.fintrack.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Pagos de servicios (luz, agua, internet...): distintos de suscripciones
 * porque el monto varía y lo que importa es la fecha de vencimiento.
 */
object ServiceBills {

    /**
     * Próximo vencimiento en o después de hoy para día+frecuencia dados.
     * [dueMonth] solo aplica a "yearly" (día/mes fijos, ej. predial).
     */
    fun nextDue(dueDay: Int, frequency: String, today: LocalDate, dueMonth: Int = 1): LocalDate {
        if (frequency == "yearly") {
            val month = dueMonth.coerceIn(1, 12)
            var candidate = atDayOf(today.year, month, dueDay)
            if (candidate.isBefore(today)) candidate = atDayOf(today.year + 1, month, dueDay)
            return candidate
        }
        val step = if (frequency == "bimonthly") 2 else 1
        var month = YearMonth.from(today)
        while (true) {
            val candidate = atDay(month, dueDay)
            if (!candidate.isBefore(today)) return candidate
            month = month.plusMonths(step.toLong())
        }
    }

    /**
     * Estado de pagado: el vencimiento [due] ya se liquidó si coincide con
     * el vencimiento que el usuario marcó como pagado. Como guarda la fecha
     * exacta, el próximo periodo vuelve a avisar solo.
     */
    fun isPaidFor(lastPaidDueIso: String?, due: LocalDate): Boolean =
        !lastPaidDueIso.isNullOrBlank() && lastPaidDueIso == due.toString()

    private fun atDay(month: YearMonth, day: Int): LocalDate {
        val safe = day.coerceIn(1, month.lengthOfMonth())
        return LocalDate.of(month.year, month.month, safe)
    }

    /**
     * Día fijo de un año/mes dados, ajustado al largo del mes: el 29-feb
     * cae en 28 en años no bisiestos (29-feb seguro).
     */
    private fun atDayOf(year: Int, month: Int, day: Int): LocalDate {
        val length = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, length))
    }

    /** Todos los vencimientos dentro del rango [from, to] (para el calendario). */
    fun duesInRange(
        dueDay: Int,
        frequency: String,
        from: LocalDate,
        to: LocalDate,
        dueMonth: Int = 1
    ): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        if (frequency == "yearly") {
            val month = dueMonth.coerceIn(1, 12)
            return (from.year - 1..to.year + 1)
                .map { atDayOf(it, month, dueDay) }
                .filter { !it.isBefore(from) && !it.isAfter(to) }
        }
        val step = if (frequency == "bimonthly") 2L else 1L
        val out = mutableListOf<LocalDate>()
        // Arranca dos periodos antes para no perder el primero en rango.
        var month = YearMonth.from(from).minusMonths(2 * step)
        repeat(30) {
            val candidate = atDay(month, dueDay)
            if (!candidate.isBefore(from) && !candidate.isAfter(to)) out.add(candidate)
            month = month.plusMonths(step)
            if (month.atDay(1).isAfter(to)) return out
        }
        return out
    }
}
