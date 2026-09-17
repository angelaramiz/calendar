package com.fintrack.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Pagos de servicios (luz, agua, internet...): distintos de suscripciones
 * porque el monto varía y lo que importa es la fecha de vencimiento.
 */
object ServiceBills {

    /** Próximo vencimiento en o después de hoy para día+frecuencia dados. */
    fun nextDue(dueDay: Int, frequency: String, today: LocalDate): LocalDate {
        val step = if (frequency == "bimonthly") 2 else 1
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
}
