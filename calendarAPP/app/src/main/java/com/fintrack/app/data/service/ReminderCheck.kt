package com.fintrack.app.data.service

import android.content.Context
import com.fintrack.app.data.AnomalyDismissStore
import com.fintrack.app.data.BudgetCapsStore
import com.fintrack.app.data.CreditCardRow
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.HormigaFrequency
import com.fintrack.app.data.HormigaStore
import com.fintrack.app.data.ReminderStore
import com.fintrack.app.data.StreakStore
import com.fintrack.app.data.ServiceBillRow
import com.fintrack.app.data.ServiceBillStore
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.CreditCardPlanner
import com.fintrack.app.domain.Anomaly
import com.fintrack.app.domain.AnomalyChecker
import com.fintrack.app.domain.AnomalyKind
import com.fintrack.app.domain.HormigaDetector
import com.fintrack.app.domain.HormigaReport
import com.fintrack.app.domain.StreakPlanner
import com.fintrack.app.domain.PatternExpander
import com.fintrack.app.domain.ServiceBills
import com.fintrack.app.domain.toLocalDateIn
import java.time.DayOfWeek
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
    /** Tarjetas que cortan en 3 días con cargos del ciclo actual (C3). */
    val cutsDue: List<CreditCardPlanner.CutoffAlert> = emptyList(),
    /** Eventos del calendario de hoy (nombre, monto). */
    val todayEvents: List<Pair<String, Double>>,
    /** false = sin sesión: los eventos (y cortes) se omitieron. */
    val sessionOk: Boolean,
    /** D5: duplicados y subidas sin descartar. */
    val anomalies: List<Anomaly> = emptyList(),
    /** D11: fuga hormiga de la semana en curso (lun-hoy). */
    val hormiga: HormigaReport? = null
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
            val due = ServiceBills.nextDue(bill.dueDay, bill.frequency, today, bill.dueMonth)
            Triple(bill, due, ChronoUnit.DAYS.between(today, due).toInt())
        }
        // Pagado = silencio: el vencimiento marcado ya no avisa (ni mediodía ni noche).
        val unpaidBills = billsWithDays.filter { (bill, due, _) ->
            !ServiceBills.isPaidFor(bill.lastPaidDueIso, due)
        }
        val billsDue = unpaidBills.filter { (_, _, days) -> days in REMIND_DAYS }
        val nextBillIn = unpaidBills.filter { (_, _, days) -> days >= 0 }
            .minOfOrNull { (_, _, days) -> days }

        val cardStore = CreditCardStore(context)
        val cards = runCatching { cardStore.cardsSnapshot() }.getOrDefault(emptyList())
        val payments = runCatching { cardStore.paymentsSnapshot() }.getOrDefault(emptyList())
        val cardsWithDays = cards.mapNotNull { card ->
            // Estado abierto = último corte: solo él puede estar pagado o por pagar.
            val open = CreditCardPlanner.lastCutoff(card.cutoffDay, today)
            val due = CreditCardPlanner.paymentFor(open, card.paymentDay, card.graceDays)
            val alreadyPaid = payments.any {
                it.cardId == card.id && it.statementCutoffIso == open.toString()
            }
            if (alreadyPaid) return@mapNotNull null
            Triple(card, due, ChronoUnit.DAYS.between(today, due).toInt())
        }
        val cardsDue = cardsWithDays.filter { (_, _, days) -> days in REMIND_DAYS }
        val nextCardIn = cardsWithDays.filter { (_, _, days) -> days >= 0 }
            .minOfOrNull { (_, _, days) -> days }

        var sessionOk = false
        val events = mutableListOf<Pair<String, Double>>()
        var cuts: List<CreditCardPlanner.CutoffAlert> = emptyList()
        var anomalies: List<Anomaly> = emptyList()
        var hormiga: HormigaReport? = null
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
                // C3: cargos tagueados del ciclo actual (tx + movimientos del
                // mes previo y actual, que cubren el estado abierto).
                cuts = runCatching {
                    val txs = TransactionRepository().getTransactions(userId)
                    val tags = cardStore.chargesSnapshot()
                    val monthStart = today.minusMonths(1).withDayOfMonth(1).toString()
                    val movs = repo.getMovementsForMonth(userId, monthStart, today.toString())
                    val byCard = mutableMapOf<String, MutableList<Pair<LocalDate, Double>>>()
                    txs.forEach { tx ->
                        val cardId = tags["tx:${tx.id}"] ?: return@forEach
                        byCard.getOrPut(cardId) { mutableListOf() }
                            .add(tx.timestamp.toLocalDateIn() to tx.amount)
                    }
                    movs.forEach { mov ->
                        if (mov.archived) return@forEach
                        val cardId = tags["mov:${mov.id}"] ?: return@forEach
                        val date = runCatching { LocalDate.parse(mov.date) }.getOrNull()
                            ?: return@forEach
                        byCard.getOrPut(cardId) { mutableListOf() }
                            .add(date to mov.confirmed_amount)
                    }
                    CreditCardPlanner.cutoffAlerts(
                        cards.map {
                            CreditCardPlanner.CutoffCard(it.id, it.displayName, it.cutoffDay)
                        },
                        byCard, today
                    )
                }.getOrDefault(emptyList())
                // D5 + D11: vigilante y fuga hormiga sobre las mismas
                // transacciones (nada nuevo que pedir, todo local + cache).
                runCatching {
                    val txs = TransactionRepository().getTransactions(userId)
                    val dismissed = AnomalyDismissStore(context).snapshot()
                    anomalies = AnomalyChecker.check(txs)
                        .filter { it.stableId !in dismissed }
                    val monday = today.with(DayOfWeek.MONDAY)
                    hormiga = HormigaDetector.topHormiga(txs, monday, today)
                    // Rachas: al cerrar quincena se registra bajo-tope/sobre-tope.
                    val closing = StreakPlanner.closingFortnight(today)
                    if (closing != null) {
                        val caps = BudgetCapsStore(context).snapshot()
                        val under = StreakPlanner.underCaps(
                            txs, caps, closing.first, closing.second
                        )
                        StreakStore(context).recordPeriod(
                            StreakPlanner.periodKey(closing.second), under
                        )
                    }
                }
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
            cutsDue = cuts,
            todayEvents = events,
            sessionOk = sessionOk,
            anomalies = anomalies,
            hormiga = hormiga
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
                    "Vencimiento $due$amount. Al pagar, márcalo como pagado en Cuentas → Servicios."
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
                    "Fecha límite $payment. Al pagar, márcalo en Cuentas → Tarjetas."
                )
                fresh.add(key)
            }
        }

        // C3: "Tu <nombre> corta en 3 días y llevas $X". Dedup por corte.
        report.cutsDue.forEach { alert ->
            val key = "cut:${alert.cardId}:${alert.cutoff}:$slot"
            if (key !in reminded) {
                RemindersNotifier.show(
                    context, key,
                    "Tu ${alert.cardName} corta en 3 días",
                    "Llevas $${alert.cycleTotal.toInt()} acumulados en el ciclo actual " +
                        "(corte ${alert.cutoff}). Se pagan en el próximo corte."
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

        // D5: vigilante informativo (mismo canal, dedup `anom_`, respeta
        // silencio: si las notificaciones estan apagadas no sale nada).
        report.anomalies.forEach { anomaly ->
            val key = "anom:${anomaly.stableId}:$slot"
            if (key !in reminded) {
                val title = if (anomaly.kind == AnomalyKind.DUPLICATE) {
                    "Posible cobro doble en ${anomaly.merchant}"
                } else {
                    "${anomaly.merchant} subio de precio"
                }
                RemindersNotifier.show(context, key, title, anomaly.detail)
                fresh.add(key)
            }
        }

        // D11: fuga hormiga solo el lunes, segun frecuencia y sin repetir semana.
        report.hormiga?.let { h ->
            if (h.visits > 0) {
                val store = HormigaStore(context)
                val freq = runCatching { store.frequency() }
                    .getOrDefault(HormigaFrequency.SEMANAL)
                val weekKey = h.from.toString()
                val last = runCatching { store.lastSentWeek() }.getOrNull()
                val isMonday = report.today.dayOfWeek == DayOfWeek.MONDAY
                val weeksSinceEpoch = ChronoUnit.WEEKS.between(
                    LocalDate.of(1970, 1, 5), h.from
                )
                val due = when (freq) {
                    HormigaFrequency.OFF -> false
                    HormigaFrequency.SEMANAL -> isMonday && last != weekKey
                    HormigaFrequency.QUINCENAL -> isMonday && last != weekKey &&
                        weeksSinceEpoch % 2L == 0L
                }
                val key = "hormiga:$weekKey:$slot"
                if (due && key !in reminded) {
                    RemindersNotifier.show(
                        context, key,
                        "${h.merchant.replaceFirstChar { it.uppercase() }} te llevo $${h.total.toInt()}",
                        "Van ${h.visits} visitas esta semana. Rachas y topes en Presupuesto."
                    )
                    fresh.add(key)
                    runCatching { store.markSent(weekKey) }
                }
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
        val cuts = if (!report.sessionOk) "omitidos (sin sesión)"
        else if (report.cutsDue.isEmpty()) "ninguno en 3 días"
        else report.cutsDue.joinToString { "${it.cardName} ($${it.cycleTotal.toInt()})" }
        val anomalies = if (!report.sessionOk) "omitidas (sin sesión)"
        else if (report.anomalies.isEmpty()) "ninguna"
        else report.anomalies.joinToString { it.merchant }
        val hormiga = report.hormiga
            ?.takeIf { it.visits > 0 }
            ?.let { "${it.merchant} $${it.total.toInt()} (${it.visits} visitas)" }
            ?: "sin fuga"
        return "Revisado: recibos: $bills; tarjetas: $cards; cortes: $cuts; eventos: $events; " +
            "anomalías: $anomalies; hormiga: $hormiga. Avisos enviados: $fired."
    }
}
