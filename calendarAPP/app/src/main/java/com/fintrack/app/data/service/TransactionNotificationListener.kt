package com.fintrack.app.data.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fintrack.app.data.AppFilterStore
import com.fintrack.app.data.PendingTxStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.domain.NotificationParser
import com.fintrack.app.domain.ParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {

    private val tag = "NotificationListener"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val transactionRepository = TransactionRepository()
    private val authRepository = AuthRepository()
    private val appFilter by lazy { AppFilterStore(applicationContext) }
    private val pendingStore by lazy { PendingTxStore(applicationContext) }

    // Anti-duplicados: misma app + monto + minuto (las notificaciones se re-publican)
    @Volatile
    private var lastKey: String? = null

    @Volatile
    private var lastTime: Long = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Prueba de vida: si esto no aparece en el diagnóstico, el sistema
        // no está entregando notificaciones al servicio (permiso revocado,
        // ahorro de batería agresivo o servicio detenido).
        diag("LISTENER CONECTADO", packageName, "servicio activo")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        diag("LISTENER DESCONECTADO (revisa acceso y batería)", packageName, "servicio detenido")
        // Pide re-vinculación al sistema (API 24+).
        try { requestRebind(android.content.ComponentName(this, TransactionNotificationListener::class.java)) } catch (_: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        // Algunas notificaciones traen el texto en BIG_TEXT o en líneas (InboxStyle).
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val text = extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: textLines?.firstOrNull()?.toString()
            ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val packageName = sbn.packageName

        scope.launch {
            try {
                appFilter.recordSeen(packageName)
                // Fusiona bancos nuevos de cada update sin revivir bajas del usuario.
                appFilter.ensureDefaults()
                val allowed = appFilter.allowedSnapshot()

                when (val result = NotificationParser.parse(packageName, title, text, allowed)) {
                    is ParseResult.Rejected -> {
                        Log.d(tag, "Ignorada (${result.reason}): $packageName | $title")
                        diag("RECHAZADA (${result.reason})", packageName, title)
                        return@launch
                    }
                    is ParseResult.Accepted -> {
                        val parsed = result.tx
                        val bucket = System.currentTimeMillis() / 60_000
                        val key = "$packageName|${parsed.amount}|$bucket"
                        if (key == lastKey && System.currentTimeMillis() - lastTime < 120_000) {
                            Log.d(tag, "Duplicada, se omite: $key")
                            diag("DUPLICADA", packageName, title)
                            return@launch
                        }

                        // Sin sesión: se guarda en el teléfono y se sincroniza
                        // al entrar (la huella refresca el token). Nada se pierde.
                        val userId = authRepository.ensureSession()
                        val entity = TransactionEntity(
                            amount = parsed.amount,
                            type = parsed.type,
                            category = parsed.category,
                            description = parsed.description,
                            merchant = parsed.merchant,
                            timestamp = System.currentTimeMillis(),
                            source = parsed.source
                        )
                        if (userId == null) {
                            val total = pendingStore.enqueue(entity)
                            Log.w(tag, "Sin sesión: encolada local ($total en cola)")
                            diag("EN COLA ($total sin sesión)", packageName, title)
                            DetectionNotifier.showPending(applicationContext, entity, total)
                            return@launch
                        }

                        try {
                            val saved = transactionRepository.insertTransaction(userId, entity)
                            lastKey = key
                            lastTime = System.currentTimeMillis()
                            Log.d(tag, "Guardada: ${saved.id} ${saved.type} $${saved.amount} ${saved.category}")
                            diag(
                                "GUARDADA ${parsed.type} $${parsed.amount} ${parsed.category}",
                                packageName,
                                title
                            )
                            DetectionNotifier.showDetected(applicationContext, saved)
                        } catch (e: Exception) {
                            // Fallo de red/sesión a mitad de camino: también a la
                            // cola en vez de perderse (el dedup evita duplicados).
                            if (isRecoverable(e)) {
                                val total = pendingStore.enqueue(entity)
                                Log.w(tag, "Fallo recuperable, encolada ($total): ${e.message}")
                                diag("EN COLA ($total: ${e.message?.take(40)})", packageName, title)
                                DetectionNotifier.showPending(applicationContext, entity, total)
                            } else {
                                Log.e(tag, "Error guardando: ${e.message}")
                                diag("ERROR AL GUARDAR (${e.message?.take(60)})", packageName, title)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error procesando notificación: ${e.message}")
            }
        }
    }

    /** Errores donde reintentar después tiene sentido (sesión/red), no errores de datos. */
    private fun isRecoverable(e: Exception): Boolean {
        val msg = (e.message ?: "").lowercase()
        return listOf(
            "jwt", "auth", "401", "403", "unauthor", "forbidden", "token",
            "network", "timeout", "host", "ssl", "socket", "econn", "unreachable",
            "Unable to resolve".lowercase()
        ).any { msg.contains(it) }
    }

    /** Guarda la última decisión para el diagnóstico en Permisos. */
    private fun diag(outcome: String, packageName: String, title: String) {
        val cleanTitle = title.replace("\n", " ").take(40)
        val line = "${System.currentTimeMillis()}|$outcome|$packageName|$cleanTitle"
        scope.launch {
            runCatching { appFilter.recordDecision(line) }
        }
    }
}
