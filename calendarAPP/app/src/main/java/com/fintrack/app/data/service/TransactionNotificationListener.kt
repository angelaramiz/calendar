package com.fintrack.app.data.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fintrack.app.data.AppFilterStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.SupabaseClientProvider
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.NotificationParser
import com.fintrack.app.domain.ParseResult
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {

    private val tag = "NotificationListener"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val transactionRepository = TransactionRepository()
    private val appFilter by lazy { AppFilterStore(applicationContext) }

    // Anti-duplicados: misma app + monto + minuto (las notificaciones se re-publican)
    @Volatile
    private var lastKey: String? = null

    @Volatile
    private var lastTime: Long = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        // Algunas notificaciones (BigTextStyle) traen el texto en EXTRA_BIG_TEXT
        val text = extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val packageName = sbn.packageName

        scope.launch {
            try {
                appFilter.recordSeen(packageName)
                val allowed = appFilter.allowedSnapshot()

                when (val result = NotificationParser.parse(packageName, title, text, allowed)) {
                    is ParseResult.Rejected -> {
                        Log.d(tag, "Ignorada (${result.reason}): $packageName | $title")
                        return@launch
                    }
                    is ParseResult.Accepted -> {
                        val parsed = result.tx
                        val bucket = System.currentTimeMillis() / 60_000
                        val key = "$packageName|${parsed.amount}|$bucket"
                        if (key == lastKey && System.currentTimeMillis() - lastTime < 120_000) {
                            Log.d(tag, "Duplicada, se omite: $key")
                            return@launch
                        }

                        val userId = try {
                            SupabaseClientProvider.client.auth.currentSessionOrNull()?.user?.id
                        } catch (e: Exception) {
                            Log.w(tag, "Sin sesión, no se puede guardar: ${e.message}")
                            null
                        } ?: return@launch

                        val saved = transactionRepository.insertTransaction(
                            userId,
                            TransactionEntity(
                                amount = parsed.amount,
                                type = parsed.type,
                                category = parsed.category,
                                description = parsed.description,
                                merchant = parsed.merchant,
                                timestamp = System.currentTimeMillis(),
                                source = parsed.source
                            )
                        )
                        lastKey = key
                        lastTime = System.currentTimeMillis()
                        Log.d(tag, "Guardada: ${saved.id} ${saved.type} $${saved.amount} ${saved.category}")
                        DetectionNotifier.showDetected(applicationContext, saved)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error procesando notificación: ${e.message}")
            }
        }
    }
}
