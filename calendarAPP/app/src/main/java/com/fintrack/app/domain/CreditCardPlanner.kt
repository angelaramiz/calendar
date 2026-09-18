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
        /** Pagos registrados contra este corte. */
        val paid: Double,
        /** Inicio del periodo (último corte). */
        val lastCutoff: LocalDate,
        /** Fecha del próximo corte. */
        val nextCutoff: LocalDate,
        /** Fecha de pago correspondiente a ese corte. */
        val nextPayment: LocalDate
    ) {
        /** Restante a pagar (nunca negativo: de más se considera a favor). */
        val remaining: Double get() = (periodCharges - paid).coerceAtLeast(0.0)
    }

    private fun atDay(month: java.time.YearMonth, day: Int): LocalDate {
        val safe = day.coerceIn(1, month.lengthOfMonth())
        return LocalDate.of(month.year, month.month, safe)
    }

    /** Último corte en o antes de hoy. */
    fun lastCutoff(cutoffDay: Int, today: LocalDate): LocalDate {        val thisMonth = atDay(java.time.YearMonth.from(today), cutoffDay)
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

    /**
     * Plazo especial tipo Plata: el pago vence [graceDays] después del corte
     * (ej. corte día 15 + 30 días → ~14 del mes siguiente, 60 días de
     * financiamiento total contando el periodo).
     */
    fun paymentForCutoffGrace(cutoff: LocalDate, graceDays: Int): LocalDate =
        cutoff.plusDays(graceDays.coerceAtLeast(1).toLong())

    /**
     * Despachador: plazo de N días si [graceDays] > 0, día fijo si no.
     */
    fun paymentFor(cutoff: LocalDate, paymentDay: Int, graceDays: Int = 0): LocalDate =
        if (graceDays > 0) paymentForCutoffGrace(cutoff, graceDays)
        else paymentForCutoff(cutoff, paymentDay)

    fun summarize(
        cardId: String,
        cutoffDay: Int,
        paymentDay: Int,
        charges: List<Pair<LocalDate, Double>>,
        today: LocalDate,
        /** Pagos como (corte del estado de cuenta, monto). */
        payments: List<Pair<LocalDate, Double>> = emptyList(),
        /** Plazo tipo Plata: días después del corte (0 = día fijo). */
        graceDays: Int = 0
    ): CardSummary {
        val last = lastCutoff(cutoffDay, today)
        val next = nextCutoff(cutoffDay, today)
        val periodTotal = charges
            .filter { (date, _) -> !date.isBefore(last) && date.isBefore(next) }
            .sumOf { (_, amount) -> amount }
        val paid = payments
            .filter { (cutoff, _) -> cutoff == next }
            .sumOf { (_, amount) -> amount }
        return CardSummary(
            cardId = cardId,
            periodCharges = periodTotal,
            paid = paid,
            lastCutoff = last,
            nextCutoff = next,
            nextPayment = paymentFor(next, paymentDay, graceDays)
        )
    }
}
