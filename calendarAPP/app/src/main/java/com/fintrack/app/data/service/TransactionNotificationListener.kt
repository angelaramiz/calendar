package com.fintrack.app.data.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fintrack.app.data.model.TransactionEntity
import com.fintrack.app.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {

    private val tag = "NotificationListener"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val bankPackages = listOf(
        "com.bancoamérica", "com.bbva.bbvacontigo",
        "com.bancanet", "com.santander.move",
        "com.scotiabank", "com.banorte.gobcams",
        "com.hsbc Mexican", "com.inbursa.bancamovil",
        "com.liverpool.superapp", "com.paypal.android.p2pmobile",
        "com.mercadopago.wallet"
    )

    private val expenseKeywords = listOf(
        "cargo", "compra", "pago", "retiro", "transferencia",
        "débito", "débito automático", "recargo", "comisión"
    )

    private val incomeKeywords = listOf(
        "abono", "depósito", "transferencia recibida", "pago recibido",
        "nómina", "ingreso", "reembolso"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val text = extras.getString(Notification.EXTRA_TEXT) ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val packageName = sbn.packageName

        Log.d(tag, "Notification from $packageName: $text")

        if (!isBankApp(packageName) && !isFinancialNotification(text)) return

        val transaction = parseTransaction(text, title) ?: return

        scope.launch {
            try {
                // Here we would save to database
                // For now, just log it
                Log.d(tag, "Transaction detected: ${transaction.type} $${transaction.amount} ${transaction.category}")
            } catch (e: Exception) {
                Log.e(tag, "Error saving transaction: ${e.message}")
            }
        }
    }

    private fun isBankApp(packageName: String): Boolean {
        return bankPackages.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun isFinancialNotification(text: String): Boolean {
        val lowerText = text.lowercase()
        return expenseKeywords.any { lowerText.contains(it) } ||
                incomeKeywords.any { lowerText.contains(it) }
    }

    private fun parseTransaction(text: String, title: String): TransactionEntity? {
        val lowerText = text.lowercase()

        // Detect amount
        val amountPattern = Regex("""\$?(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)""")
        val amountMatch = amountPattern.find(text) ?: return null
        val amountStr = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        // Detect type
        val isIncome = incomeKeywords.any { lowerText.contains(it) }
        val type = if (isIncome) "INCOME" else "EXPENSE"

        // Detect merchant
        val merchantPattern = Regex("""en\s+([A-Za-z0-9\s]+?)(?:\.|$)""")
        val merchantMatch = merchantPattern.find(text)
        val merchant = merchantMatch?.groupValues?.get(1)?.trim()

        // Auto-categorize
        val category = autoCategorize(merchant ?: text)

        return TransactionEntity(
            amount = amountStr,
            type = type,
            category = category,
            description = text.take(100),
            merchant = merchant,
            timestamp = System.currentTimeMillis(),
            source = "AUTO"
        )
    }

    private fun autoCategorize(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.containsAny("restaurante", "café", "comida", "restaurant", "starbucks", "mcdonald") -> "Comida"
            lower.containsAny("uber", "taxi", "gasolina", "estacionamiento", "metro") -> "Transporte"
            lower.containsAny("luz", "agua", "gas", "internet", "teléfono", "sky", "telcel") -> "Servicios"
            lower.containsAny("netflix", "spotify", "cinema", "cine", "juego") -> "Ocio"
            else -> "Otros"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
