package com.fintrack.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Pagos de servicios (luz, agua, internet...): distintos de suscripciones
 * porque el monto varía y lo que importa es la fecha de vencimiento.
 */
object ServiceBills {

    /** Próximo vencimiento en o después de hoy para día+frecuencia dados. */
    fun nextDue(dueDay: Int, frequency: String, today: LocalDate): LocalDate {        val step = if (frequency == "bimonthly") 2 else 1
        var month = YearMonth.from(today)
        while (true) {
            val candidate = atDay(month, dueDay)
            if (!candidate.isBefore(today)) return candidate
            month = month.plusMonths(step.toLong())
        }
    }

    private fun atDay(month: YearMonth, day: Int): LocalDate {
        val safe = day.coerceIn(1, month.lengthOfMonth())
        return LocalDate.of(month.year, month.month, safe)
    }

    /** Todos los vencimientos dentro del rango [from, to] (para el calendario). */
    fun duesInRange(dueDay: Int, frequency: String, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
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
