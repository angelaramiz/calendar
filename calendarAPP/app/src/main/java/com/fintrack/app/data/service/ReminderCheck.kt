package com.fintrack.app.data.service

import android.content.Context
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.ReminderStore
import com.fintrack.app.data.ServiceBillRow
import com.fintrack.app.data.ServiceBillStore
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.CreditCardPlanner
import com.fintrack.app.domain.PatternExpander
import com.fintrack.app.domain.ServiceBills
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ReminderReport(
    val today: LocalDate,
    val billsChecked: Int,
    /** (recibo, vencimiento, días restantes) dentro de la ventana de aviso. */
    val billsDue: List<Triple<ServiceBillRow, LocalDate, Int>>,
    /** Días para el próximo vencimiento (cualquiera), null si no hay recibos. */
    val nextBillIn: Int?,
    val cardsChecked: Int,
    /** (tarjeta, fecha de pago, días restantes) dentro de la ventana. */
    val cardsDue: List<Triple<CreditCardRow, LocalDate, Int>>,
    val nextCardIn: Int?,
    /** Eventos del calendario de hoy (nombre, monto). */
    val todayEvents: List<Pair<String, Double>>,
    /** false = sin sesión: los eventos se omitieron. */
    val sessionOk: Boolean
)

/**
 * Lógica de revisión compartida entre el worker (12:00 y 22:00) y el botón
 * Probar: evalúa qué vence y dispara los avisos no repetidos.
 */
object ReminderCheck {

    val REMIND_DAYS = setOf(3, 1, 0)

    suspend fun evaluate(context: Context): ReminderReport {
        val today = LocalDate.now(ZoneId.systemDefault())

        val bills = runCatching { ServiceBillStore(context).snapshot() }.getOrDefault(emptyList())
        val billsWithDays = bills.map { bill ->
            val due = ServiceBills.nextDue(bill.dueDay, bill.frequency, today)
            Triple(bill, due, ChronoUnit.DAYS.between(today, due).toInt())
        }
        val billsDue = billsWithDays.filter { (_, _, days) -> days in REMIND_DAYS }
        val nextBillIn = billsWithDays.filter { (_, _, days) -> days >= 0 }
            .minOfOrNull { (_, _, days) -> days }

        val cardStore = CreditCardStore(context)
        val cards = runCatching { cardStore.cardsSnapshot() }.getOrDefault(emptyList())
        val payments = runCatching { cardStore.paymentsSnapshot() }.getOrDefault(emptyList())
        val cardsWithDays = cards.mapNotNull { card ->
            val nextCutoff = CreditCardPlanner.nextCutoff(card.cutoffDay, today)
            val nextPayment = CreditCardPlanner.paymentFor(nextCutoff, card.paymentDay, card.graceDays)
            val alreadyPaid = payments.any {
                it.cardId == card.id && it.statementCutoffIso == nextCutoff.toString()
            }
            if (alreadyPaid) return@mapNotNull null
            Triple(card, nextPayment, ChronoUnit.DAYS.between(today, nextPayment).toInt())
        }
        val cardsDue = cardsWithDays.filter { (_, _, days) -> days in REMIND_DAYS }
        val nextCardIn = cardsWithDays.filter { (_, _, days) -> days >= 0 }
            .minOfOrNull { (_, _, days) -> days }

        var sessionOk = false
        val events = mutableListOf<Pair<String, Double>>()
        runCatching {
            val userId = AuthRepository().ensureSession()
            if (userId != null) {
                sessionOk = true
                val repo = PatternRepository()
                val patterns =
                    repo.getIncomePatterns(userId).mapNotNull { it.toDomain("INCOME") } +
                        repo.getExpensePatterns(userId).mapNotNull { it.toDomain("EXPENSE") }
                patterns.filter { it.active }.flatMap { pattern ->
                    PatternExpander.expand(pattern, today, today)
                }.forEach { events.add(it.pattern.name to it.amount) }
            }
        }

        return ReminderReport(
            today = today,
            billsChecked = bills.size,
            billsDue = billsDue,
            nextBillIn = nextBillIn,
            cardsChecked = cards.size,
            cardsDue = cardsDue,
            nextCardIn = nextCardIn,
            todayEvents = events,
            sessionOk = sessionOk
        )
    }

    /**
     * Envía los avisos no repetidos; devuelve cuántos salieron.
     * [slot] es el turno ("mediodia", "noche", "prueba"): va en la clave
     * para que el aviso de la noche no lo consuma el del mediodía.
     */
    suspend fun fire(
        context: Context,
        report: ReminderReport,
        slot: String = RemindersWorker.SLOT_TEST
    ): Int {
        val reminded = runCatching { ReminderStore(context).snapshot() }.getOrDefault(emptySet())
        val fresh = mutableSetOf<String>()

        report.billsDue.forEach { (bill, due, days) ->
            val key = "bill:${bill.id}:$due:$slot"
            if (key !in reminded) {
                val whenText = if (days == 0) "hoy" else "en $days día${if (days == 1) "" else "s"}"
                val amount =
                    if (bill.estimatedAmount > 0.0) " (aprox. $${bill.estimatedAmount.toInt()})" else ""
                RemindersNotifier.show(
                    context, key,
                    "${bill.name} vence $whenText",
                    "Vencimiento $due$amount. Márcalo como pagado en tu banco."
                )
                fresh.add(key)
            }
        }

        report.cardsDue.forEach { (card, payment, days) ->
            val key = "card:${card.id}:$payment:$slot"
            if (key !in reminded) {
                val whenText = if (days == 0) "hoy" else "en $days día${if (days == 1) "" else "s"}"
                RemindersNotifier.show(
                    context, key,
                    "Pagar ${card.displayName} $whenText",
                    "Fecha límite $payment. El estimado está en Presupuesto → Tarjetas."
                )
                fresh.add(key)
            }
        }

        if (report.todayEvents.isNotEmpty()) {
            val key = "day:${report.today}:$slot"
            if (key !in reminded) {
                val names = report.todayEvents.take(3).joinToString(", ") { (name, amount) ->
                    "$name ($${amount.toInt()})"
                }
                val extra = if (report.todayEvents.size > 3) " y ${report.todayEvents.size - 3} más" else ""
                RemindersNotifier.show(context, key, "Hoy en tu calendario", "$names$extra.")
                fresh.add(key)
            }
        }

        runCatching { ReminderStore(context).markReminded(fresh) }
        return fresh.size
    }

    /** Resumen legible para el botón Probar (siempre responde algo). */
    fun describe(report: ReminderReport, fired: Int): String {
        val bills = if (report.billsChecked == 0) "sin recibos dados de alta"
        else if (report.billsDue.isEmpty()) {
            val next = report.nextBillIn?.let { " (próximo en $it días)" } ?: ""
            "${report.billsChecked} revisados, ninguno por vencer$next"
        } else "${report.billsDue.size} por vencer"
        val cards = if (report.cardsChecked == 0) "sin tarjetas"
        else if (report.cardsDue.isEmpty()) {
            val next = report.nextCardIn?.let { " (próximo en $it días)" } ?: ""
            "${report.cardsChecked} revisadas, ningún pago próximo$next"
        } else "${report.cardsDue.size} por pagar"
        val events = if (!report.sessionOk) "omitidos (sin sesión)"
        else if (report.todayEvents.isEmpty()) "ninguno hoy"
        else "${report.todayEvents.size} hoy"
        return "Revisado: recibos: $bills; tarjetas: $cards; eventos: $events. " +
            "Avisos enviados: $fired."
    }
}
