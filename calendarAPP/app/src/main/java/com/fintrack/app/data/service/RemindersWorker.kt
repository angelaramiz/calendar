package com.fintrack.app.data.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fintrack.app.data.CreditCardStore
import com.fintrack.app.data.ReminderStore
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
import java.util.concurrent.TimeUnit

/**
 * Revisión diaria: servicios por vencer, pagos de tarjeta próximos y eventos
 * del calendario de hoy. Avisa a 3, 1 y 0 días; cada aviso sale una vez
 * (claves en ReminderStore).
 */
class RemindersWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val today = LocalDate.now(ZoneId.systemDefault())
        val reminded = runCatching { ReminderStore(context).snapshot() }.getOrDefault(emptySet())
        val fresh = mutableSetOf<String>()

        // 1. Servicios por vencer.
        val bills = runCatching { ServiceBillStore(context).snapshot() }.getOrDefault(emptyList())
        bills.forEach { bill ->
            val due = ServiceBills.nextDue(bill.dueDay, bill.frequency, today)
            val days = ChronoUnit.DAYS.between(today, due).toInt()
            val key = "bill:${bill.id}:$due"
            if (days in REMIND_DAYS && key !in reminded) {
                val whenText = if (days == 0) "hoy" else "en $days día${if (days == 1) "" else "s"}"
                val amount = if (bill.estimatedAmount > 0.0) " (aprox. $${bill.estimatedAmount.toInt()})" else ""
                RemindersNotifier.show(
                    context, key,
                    "${bill.name} vence $whenText",
                    "Vencimiento $due$amount. Márcalo como pagado en tu banco."
                )
                fresh.add(key)
            }
        }

        // 2. Pagos de tarjeta próximos (salta si ya hay pago contra ese corte).
        val cardStore = CreditCardStore(context)
        val cards = runCatching { cardStore.cardsSnapshot() }.getOrDefault(emptyList())
        val payments = runCatching { cardStore.paymentsSnapshot() }.getOrDefault(emptyList())
        cards.forEach { card ->
            val nextCutoff = CreditCardPlanner.nextCutoff(card.cutoffDay, today)
            val nextPayment = CreditCardPlanner.paymentForCutoff(nextCutoff, card.paymentDay)
            val days = ChronoUnit.DAYS.between(today, nextPayment).toInt()
            val key = "card:${card.id}:$nextPayment"
            val alreadyPaid = payments.any {
                it.cardId == card.id && it.statementCutoffIso == nextCutoff.toString()
            }
            if (days in REMIND_DAYS && key !in reminded && !alreadyPaid) {
                val whenText = if (days == 0) "hoy" else "en $days día${if (days == 1) "" else "s"}"
                RemindersNotifier.show(
                    context, key,
                    "Pagar ${card.name} $whenText",
                    "Fecha límite $nextPayment. El estimado está en Presupuesto → Tarjetas."
                )
                fresh.add(key)
            }
        }

        // 3. Eventos del calendario de hoy (requiere sesión; si no hay, se omite).
        runCatching {
            val auth = AuthRepository()
            val userId = auth.ensureSession() ?: return@runCatching
            val repo = PatternRepository()
            val patterns =
                repo.getIncomePatterns(userId).mapNotNull { it.toDomain("INCOME") } +
                    repo.getExpensePatterns(userId).mapNotNull { it.toDomain("EXPENSE") }
            val todays = patterns.filter { it.active }.flatMap { pattern ->
                PatternExpander.expand(pattern, today, today)
            }
            if (todays.isNotEmpty()) {
                val key = "day:${today}"
                if (key !in reminded) {
                    val names = todays.take(3).joinToString(", ") {
                        "${it.pattern.name} ($${it.amount.toInt()})"
                    }
                    val extra = if (todays.size > 3) " y ${todays.size - 3} más" else ""
                    RemindersNotifier.show(
                        context, key,
                        "Hoy en tu calendario",
                        "$names$extra."
                    )
                    fresh.add(key)
                }
            }
        }

        runCatching { ReminderStore(context).markReminded(fresh) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "fintrack_daily_reminders"
        private val REMIND_DAYS = setOf(3, 1, 0)

        /** Programa la revisión diaria (~8:00). Idempotente. */
        fun schedule(context: Context) {
            val now = java.time.ZonedDateTime.now()
            var next = now.toLocalDate().atTime(8, 0).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMin = ChronoUnit.MINUTES.between(now, next)
            val request = PeriodicWorkRequestBuilder<RemindersWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
