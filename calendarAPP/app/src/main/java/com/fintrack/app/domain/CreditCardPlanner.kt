package com.fintrack.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Lógica de tarjetas de crédito: los gastos con tag de tarjeta no se aplican
 * al balance del momento; se acumulan para el próximo pago según el corte.
 */
object CreditCardPlanner {

    data class CardSummary(
        val cardId: String,
        /** Cargos del estado de cuenta abierto (entre el corte anterior y el abierto). */
        val periodCharges: Double,
        /** Pagos registrados contra el corte abierto. */
        val paid: Double,
        /** Corte anterior: inicio del periodo del estado abierto. */
        val periodStart: LocalDate,
        /** Corte del estado de cuenta abierto (el que se debe pagar). */
        val statementCutoff: LocalDate,
        /** Fecha de pago del estado abierto. */
        val dueDate: LocalDate
    ) {
        /** Restante a pagar (nunca negativo: de más se considera a favor). */
        val remaining: Double get() = (periodCharges - paid).coerceAtLeast(0.0)
        /** true si el estado abierto ya quedó liquidado. */
        val isPaid: Boolean get() = paid > 0.0 && remaining <= 0.0
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

    /**
     * Cargos del ciclo actual: fecha en [openCutoff, today]. Son los que se
     * acumulan para el próximo corte, no entran al estado abierto a pagar.
     */
    fun <T> currentCycle(
        items: List<T>,
        dateOf: (T) -> LocalDate,
        openCutoff: LocalDate,
        today: LocalDate
    ): List<T> =
        items.filter { !dateOf(it).isBefore(openCutoff) && !dateOf(it).isAfter(today) }

    /**
     * Periodos de estados de cuenta (prev, open), del más nuevo al más viejo.
     * El historial es summarize() con today = cada corte: no duplica lógica.
     */
    fun statementPeriods(
        cutoffDay: Int,
        today: LocalDate,
        count: Int = 6
    ): List<Pair<LocalDate, LocalDate>> {
        val periods = mutableListOf<Pair<LocalDate, LocalDate>>()
        var open = lastCutoff(cutoffDay, today)
        repeat(count.coerceAtLeast(1)) {
            val prev = lastCutoff(cutoffDay, open.minusDays(1))
            periods.add(prev to open)
            open = prev
        }
        return periods
    }

    /** Entrada mínima para la alerta de corte (sin Context, testeable). */
    data class CutoffCard(
        val id: String,
        val name: String,
        val cutoffDay: Int
    )

    /** Tarjeta que corta en 3 días con cargos acumulados en el ciclo actual. */
    data class CutoffAlert(
        val cardId: String,
        val cardName: String,
        val cutoff: LocalDate,
        val cycleTotal: Double
    )

    /**
     * Alerta de corte próximo (C3): por cada tarjeta que corta exactamente
     * en 3 días y lleva cargos del ciclo actual > 0. Puro: [charges] es
     * cardId → (fecha, monto) de sus cargos tagueados.
     */
    fun cutoffAlerts(
        cards: List<CutoffCard>,
        charges: Map<String, List<Pair<LocalDate, Double>>>,
        today: LocalDate
    ): List<CutoffAlert> =
        cards.mapNotNull { card ->
            val next = nextCutoff(card.cutoffDay, today)
            if (ChronoUnit.DAYS.between(today, next).toInt() != 3) return@mapNotNull null
            val open = lastCutoff(card.cutoffDay, today)
            val total = currentCycle(
                charges.getOrDefault(card.id, emptyList()),
                { it.first }, open, today
            ).sumOf { it.second }
            if (total > 0.0) CutoffAlert(card.id, card.name, next, total)
            else null
        }

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
        // Estado abierto = el del último corte: es el que se debe pagar.
        // El próximo corte aún no existe como estado de cuenta.
        val open = lastCutoff(cutoffDay, today)
        val prev = lastCutoff(cutoffDay, open.minusDays(1))
        val periodTotal = charges
            .filter { (date, _) -> !date.isBefore(prev) && date.isBefore(open) }
            .sumOf { (_, amount) -> amount }
        val paid = payments
            .filter { (cutoff, _) -> cutoff == open }
            .sumOf { (_, amount) -> amount }
        return CardSummary(
            cardId = cardId,
            periodCharges = periodTotal,
            paid = paid,
            periodStart = prev,
            statementCutoff = open,
            dueDate = paymentFor(open, paymentDay, graceDays)
        )
    }
}
