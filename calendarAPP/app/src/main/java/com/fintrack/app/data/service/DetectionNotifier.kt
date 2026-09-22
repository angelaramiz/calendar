package com.fintrack.app.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fintrack.app.MainActivity
import com.fintrack.app.R
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object DetectionNotifier {

    const val CHANNEL_ID = "fintrack_detecciones"
    const val ACTION_DELETE = "com.fintrack.app.DELETE_DETECTION"
    const val EXTRA_TX_ID = "tx_id"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Movimientos detectados",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Avisa cada gasto o ingreso detectado automáticamente."
                    }
                )
            }
        }
    }

    fun showDetected(context: Context, tx: TransactionEntity) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val isIncome = tx.kind?.isIncome == true
        val title = if (isIncome) "Ingreso detectado" else "Gasto detectado"
        val detail = "$${String.format("%.2f", tx.amount)}" +
            (tx.merchant?.let { " en $it" } ?: "") + " · ${tx.category}"

        val openIntent = PendingIntent.getActivity(
            context,
            tx.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            tx.id.hashCode() + 1,
            Intent(context, DeleteDetectionReceiver::class.java).apply {
                action = ACTION_DELETE
                putExtra(EXTRA_TX_ID, tx.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_detect)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_detect, "Eliminar", deleteIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(tx.id.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    /** Aviso de detección guardada solo en el teléfono (sin sesión/red). */
    fun showPending(context: Context, tx: TransactionEntity, queueSize: Int) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val isIncome = tx.kind?.isIncome == true
        val kind = if (isIncome) "Ingreso" else "Gasto"
        val detail = "$kind $${String.format("%.2f", tx.amount)}" +
            (tx.merchant?.let { " en $it" } ?: "") +
            " · guardado en el teléfono ($queueSize en cola, se sincroniza al entrar)."

        val openIntent = PendingIntent.getActivity(
            context,
            ("pending" + tx.hashCode()).hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_detect)
            .setContentTitle("$kind detectado (pendiente de sincronizar)")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(("pending" + tx.hashCode()).hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun dismiss(context: Context, txId: String) {
        NotificationManagerCompat.from(context).cancel(txId.hashCode())
    }
}

class DeleteDetectionReceiver : BroadcastReceiver(), KoinComponent {
    private val authRepository: AuthRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DetectionNotifier.ACTION_DELETE) return
        val txId = intent.getStringExtra(DetectionNotifier.EXTRA_TX_ID) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Con ownership: sin sesión no se borra nada ajeno.
                val uid = authRepository.currentUserId ?: return@launch
                TransactionRepository().deleteTransaction(uid, txId)
            } catch (_: Exception) {
            }
            DetectionNotifier.dismiss(context.applicationContext, txId)
        }
    }
}
