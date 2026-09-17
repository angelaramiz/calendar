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
 * Revisión diaria: servicios por vencer, pagos de tarjeta próximos y eventos
 * del calendario de hoy. Avisa a 3, 1 y 0 días; cada aviso sale una vez
 * (claves en ReminderStore).
 */
class RemindersWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val report = ReminderCheck.evaluate(applicationContext)
            ReminderCheck.fire(applicationContext, report)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "fintrack_daily_reminders"

        /**
         * Lanza una revisión inmediata (botón Probar): ejecuta la misma
         * lógica sin esperar a las 8:00. Si algo vence en 3/1/0 días (o hay
         * eventos hoy), el aviso sale en segundos.
         */
        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<RemindersWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** Programa la revisión diaria (~8:00). Idempotente. */
        fun schedule(context: Context) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
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
