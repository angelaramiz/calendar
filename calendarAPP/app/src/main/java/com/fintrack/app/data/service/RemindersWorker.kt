package com.fintrack.app.data.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Dos pasadas diarias: vencimientos de servicios, pagos de tarjeta y eventos
 * del calendario. Avisa a 3, 1 y 0 días: una al mediodía (12:00) y otra
 * antes de medianoche (22:00). Cada aviso sale una vez por turno
 * (claves con turno en ReminderStore).
 */
class RemindersWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val slot = inputData.getString(KEY_SLOT) ?: SLOT_MIDDAY
            val report = ReminderCheck.evaluate(applicationContext)
            ReminderCheck.fire(applicationContext, report, slot)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SLOT = "slot"
        const val SLOT_MIDDAY = "mediodia"
        const val SLOT_NIGHT = "noche"
        const val SLOT_TEST = "prueba"

        private const val LEGACY_WORK = "fintrack_daily_reminders"
        private const val WORK_MIDDAY = "fintrack_reminders_midday"
        private const val WORK_NIGHT = "fintrack_reminders_night"

        /**
         * Lanza una revisión inmediata (botón Probar): ejecuta la misma
         * lógica sin esperar a las 12:00/22:00. Usa turno de prueba para
         * no consumir los avisos del día. Si algo vence en 3/1/0 días (o hay
         * eventos hoy), el aviso sale en segundos.
         */
        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<RemindersWorker>()
                .setInputData(androidx.work.workDataOf(KEY_SLOT to SLOT_TEST))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** Programa las dos pasadas diarias (12:00 y 22:00). Idempotente. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            // Migración: cancela la pasada única antigua (~8:00).
            runCatching { wm.cancelUniqueWork(LEGACY_WORK) }
            enqueueDaily(wm, WORK_MIDDAY, SLOT_MIDDAY, 12, 0)
            enqueueDaily(wm, WORK_NIGHT, SLOT_NIGHT, 22, 0)
        }

        private fun enqueueDaily(
            wm: WorkManager,
            workName: String,
            slot: String,
            hour: Int,
            minute: Int
        ) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            var next = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMin = ChronoUnit.MINUTES.between(now, next)
            val request = PeriodicWorkRequestBuilder<RemindersWorker>(24, TimeUnit.HOURS)
                .setInputData(androidx.work.workDataOf(KEY_SLOT to slot))
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
