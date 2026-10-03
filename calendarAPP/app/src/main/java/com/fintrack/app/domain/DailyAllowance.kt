package com.fintrack.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * D6 Límite diario ("hoy puedes gastar $X"):
 * (ingresoPeriodo − fijosDelPeriodo − apartadoAhorro) / díasRestantes.
 * Puro y testeable: los fijos se arman con servicios del periodo
 * ([ServiceBills.duesInRange]) + mínimos de tarjetas ([CreditCardPlanner]).
 */
object DailyAllowance {

    /** Quincena en curso: días 1..15 o 16..fin de mes. */
    data class PayPeriod(val start: LocalDate, val end: LocalDate)

    /** Entrada mínima por tarjeta para estimar lo que hay que pagar. */
    data class CardMinInput(
        val cardId: String,
        val cutoffDay: Int,
        val paymentDay: Int,
        val graceDays: Int = 0
    )

    fun periodFor(today: LocalDate): PayPeriod {
        val month = YearMonth.from(today)
        return if (today.dayOfMonth <= 15) {
            PayPeriod(month.atDay(1), month.atDay(15))
        } else {
            PayPeriod(month.atDay(16), month.atEndOfMonth())
        }
    }

    /** Días restantes incluyendo hoy (mínimo 1: el último día todo lo disponible). */
    fun daysRemaining(today: LocalDate): Int {
        val period = periodFor(today)
        return ChronoUnit.DAYS.between(today, period.end).toInt().plus(1).coerceAtLeast(1)
    }

    /** Suma de vencimientos (fecha, monto) que caen dentro del periodo. */
    fun servicesInPeriod(
        dues: List<Pair<LocalDate, Double>>,
        period: PayPeriod
    ): Double = dues
        .filter { (date, _) -> !date.isBefore(period.start) && !date.isAfter(period.end) }
        .sumOf { (_, amount) -> amount }

    /**
     * Mínimos a pagar por tarjeta con el motor existente: lo restante del
     * estado abierto ([CreditCardPlanner.summarize], open-statement).
     */
    fun cardMinimums(
        cards: List<CardMinInput>,
        chargesByCard: Map<String, List<Pair<LocalDate, Double>>>,
        paymentsByCard: Map<String, List<Pair<LocalDate, Double>>>,
        today: LocalDate
    ): Map<String, Double> = cards.associate { card ->
        val summary = CreditCardPlanner.summarize(
            cardId = card.cardId,
            cutoffDay = card.cutoffDay,
            paymentDay = card.paymentDay,
            charges = chargesByCard.getOrDefault(card.cardId, emptyList()),
            today = today,
            payments = paymentsByCard.getOrDefault(card.cardId, emptyList()),
            graceDays = card.graceDays
        )
        card.cardId to summary.remaining
    }

    /**
     * Límite diario. null = sin ancla de ingreso (la UI oculta la sección,
     * no inventa números). Nunca negativo: si los fijos superan el ingreso,
     * el día permite $0.
     */
    fun calculate(
        ingresoPeriodo: Double?,
        fijosDelPeriodo: Double,
        apartadoAhorro: Double,
        diasRestantes: Int
    ): Double? {
        if (ingresoPeriodo == null || ingresoPeriodo <= 0.0) return null
        if (diasRestantes <= 0) return null
        val disponible = ingresoPeriodo - fijosDelPeriodo - apartadoAhorro.coerceAtLeast(0.0)
        return (disponible / diasRestantes).coerceAtLeast(0.0)
    }

    /** Aviso suave: el gasto de hoy ya pasó el límite del día. */
    fun overDay(gastoHoy: Double, allowance: Double?): Boolean =
        allowance != null && gastoHoy > allowance
}
