package com.fintrack.app.domain

import java.time.LocalDate

/**
 * Lógica de tarjetas de crédito: los gastos con tag de tarjeta no se aplican
 * al balance del momento; se acumulan para el próximo pago según el corte.
 */
object CreditCardPlanner {

    data class CardSummary(
        val cardId: String,
        /** Cargos del periodo actual (tras el último corte). */
        val periodCharges: Double,
        /** Inicio del periodo (último corte). */
        val lastCutoff: LocalDate,
        /** Fecha del próximo corte. */
        val nextCutoff: LocalDate,
        /** Fecha de pago correspondiente a ese corte. */
        val nextPayment: LocalDate
    )

    private fun atDay(month: java.time.YearMonth, day: Int): LocalDate {
        val safe = day.coerceIn(1, month.lengthOfMonth())
        return LocalDate.of(month.year, month.month, safe)
    }

    /** Último corte en o antes de hoy. */
    fun lastCutoff(cutoffDay: Int, today: LocalDate): LocalDate {
        val thisMonth = atDay(java.time.YearMonth.from(today), cutoffDay)
        return if (!thisMonth.isAfter(today)) thisMonth
        else atDay(java.time.YearMonth.from(today).minusMonths(1), cutoffDay)
    }

    /** Próximo corte estrictamente después de hoy. */
    fun nextCutoff(cutoffDay: Int, today: LocalDate): LocalDate {
        val thisMonth = atDay(java.time.YearMonth.from(today), cutoffDay)
        return if (thisMonth.isAfter(today)) thisMonth
        else atDay(java.time.YearMonth.from(today).plusMonths(1), cutoffDay)
    }

    /**
     * Pago del corte dado: la primera ocurrencia del día de pago
     * estrictamente después del corte.
     */
    fun paymentForCutoff(cutoff: LocalDate, paymentDay: Int): LocalDate {
        var month = java.time.YearMonth.from(cutoff)
        while (true) {
            val candidate = atDay(month, paymentDay)
            if (candidate.isAfter(cutoff)) return candidate
            month = month.plusMonths(1)
        }
    }

    fun summarize(
        cardId: String,
        cutoffDay: Int,
        paymentDay: Int,
        charges: List<Pair<LocalDate, Double>>,
        today: LocalDate
    ): CardSummary {
        val last = lastCutoff(cutoffDay, today)
        val next = nextCutoff(cutoffDay, today)
        val periodTotal = charges
            .filter { (date, _) -> !date.isBefore(last) && date.isBefore(next) }
            .sumOf { (_, amount) -> amount }
        return CardSummary(
            cardId = cardId,
            periodCharges = periodTotal,
            lastCutoff = last,
            nextCutoff = next,
            nextPayment = paymentForCutoff(next, paymentDay)
        )
    }
}
