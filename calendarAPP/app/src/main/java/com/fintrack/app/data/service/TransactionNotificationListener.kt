package com.fintrack.app.data.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fintrack.app.data.AppFilterStore
import com.fintrack.app.data.CategoryRuleStore
import com.fintrack.app.data.PendingTxStore
import com.fintrack.app.data.TxLink
import com.fintrack.app.data.TxLinkStore
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.remote.AuthRepository
import com.fintrack.app.data.repository.PatternRepository
import com.fintrack.app.data.repository.TransactionRepository
import com.fintrack.app.data.repository.toDomain
import com.fintrack.app.domain.NotificationParser
import com.fintrack.app.domain.ParsedTransaction
import com.fintrack.app.domain.ParseResult
import com.fintrack.app.domain.matchPatternByConcept
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
    private val ruleStore by lazy { CategoryRuleStore(applicationContext) }
    private val txLinkStore by lazy { TxLinkStore(applicationContext) }
    private val patternRepository = PatternRepository()

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
                // Reglas que aprendieron de tus correcciones (C5): si fallan,
                // se sigue con mapa vacío, nunca se pierde la detección.
                val rules = runCatching { ruleStore.snapshot() }.getOrDefault(emptyMap())

                when (val result = NotificationParser.parse(packageName, title, text, allowed, rules)) {
                    is ParseResult.Rejected -> {
                        Log.d(tag, "Ignorada (${result.reason}): $packageName | $title")
                        diag("RECHAZADA (${result.reason})", packageName, title)
                        // App fuera de la lista pero con pinta de banco: se
                        // sugiere una sola vez en vez de ignorarla en silencio.
                        // Doble gate: la categoría de Play descarta juegos/redes
                        // (ej. "saldo de monedas" no es un banco) y el parser
                        // exige señal bancaria fuerte ("te envió 3 fotos" no pasa).
                        if (result.reason == "app_no_permitida" &&
                            packageName != applicationContext.packageName &&
                            !isObviouslyNotBank(packageName) &&
                            NotificationParser.looksLikeBankActivity(packageName, title, text)
                        ) {
                            val fresh = runCatching {
                                appFilter.suggestBank(packageName, title)
                            }.getOrDefault(false)
                            if (fresh) {
                                DetectionNotifier.showBankSuggestion(
                                    applicationContext, packageName, title
                                )
                                diag("BANCO SUGERIDO", packageName, title)
                            }
                        }
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
                            // Auto-vínculo: si el aviso trae el concepto de un
                            // recurrente (ej. "hybridge"), se marca como ese
                            // evento sin duplicar (igual que Vincular manual).
                            if (tryAutoLinkEvent(userId, saved, parsed, title, text, packageName)) {
                                return@launch
                            }
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

    /**
     * Categorías de Play que nunca son bancos (juegos, redes, video...):
     * ni se les sugiere. `category` existe desde API 26 (= minSdk).
     * Si el paquete no declara categoría o falla la consulta, se deja
     * pasar al gate del parser (defensa en profundidad, no bloqueo).
     */
    private fun isObviouslyNotBank(packageName: String): Boolean = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        when (info.category) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME,
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL,
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO,
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO,
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE,
            android.content.pm.ApplicationInfo.CATEGORY_NEWS,
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> true
            else -> false
        }
    }.getOrDefault(false)

    /**
     * Auto-vínculo aviso → evento recurrente: si el texto trae el concepto
     * del recurrente y hay una ocurrencia esperada a ±2 días, el registro
     * detectado se vincula solo (excluye del balance y oculta la proyección,
     * con 🔗 y Desvincular en la app). true = vinculado (ya avisado).
     * Solo con sesión: sin red no hay patrones que comparar y el aviso
     * queda encolado para vincular a mano.
     */
    private suspend fun tryAutoLinkEvent(
        userId: String,
        saved: TransactionEntity,
        parsed: ParsedTransaction,
        title: String,
        text: String,
        packageName: String
    ): Boolean = runCatching {
        val isIncome = parsed.type == "INCOME"
        val rows = if (isIncome) patternRepository.getIncomePatterns(userId)
        else patternRepository.getExpensePatterns(userId)
        if (rows.isEmpty()) return false
        val occurrence = matchPatternByConcept(
            title, text, isIncome,
            rows.mapNotNull { it.toDomain(parsed.type) },
            java.time.LocalDate.now(), 2
        ) ?: return false
        txLinkStore.link(
            saved.id,
            TxLink(occurrence.pattern.id, occurrence.date.toString(), isIncome, occurrence.pattern.name)
        )
        Log.d(tag, "Evento auto-vinculado: ${occurrence.pattern.name} ${occurrence.date}")
        diag("EVENTO AUTO-VINCULADO ${occurrence.pattern.name}", packageName, title)
        DetectionNotifier.showEventMatched(
            applicationContext, saved.id, occurrence.pattern.name,
            parsed.amount, occurrence.date.toString(), isIncome
        )
        true
    }.getOrDefault(false)

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
