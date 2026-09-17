package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Billeteras locales (sin columna en el servidor): cada movimiento se asigna
 * por regla según la app que lo originó, salvo override manual por id.
 * Clave de override: "tx:<id>" o "mov:<id>".
 */
object WalletResolver {

    data class Wallet(
        val id: String,
        val name: String,
        val packages: List<String> = emptyList()
    )

    const val EFECTIVO_ID = "efectivo"
    const val MANUAL_SOURCE = "MANUAL"

    val DEFAULT_WALLETS = listOf(
        Wallet(EFECTIVO_ID, "Efectivo"),
        Wallet("nu", "Nu", listOf("com.nu.production")),
        Wallet("plata", "Plata", listOf("dif.tech.plata")),
        Wallet("azteca", "Banco Azteca", listOf("mx.com.bancoazteca.bazdigitalmovil")),
        Wallet("spin", "Spin", listOf("com.pagopopmobile")),
        Wallet("paypal", "PayPal", listOf("com.paypal.android.p2pmobile")),
        Wallet("banamex", "Banamex", listOf("com.citibanamex.banamexmobile")),
        Wallet("gpay", "Google Wallet", listOf("com.google.android.apps.walletnfcrel"))
    )

    fun walletForPackage(packageName: String?, wallets: List<Wallet>): String {
        if (packageName.isNullOrBlank()) return EFECTIVO_ID
        return wallets.firstOrNull { w -> w.packages.any { it.equals(packageName, ignoreCase = true) } }?.id
            ?: EFECTIVO_ID
    }

    /** Billetera de un movimiento: override manual, si no regla por origen. */
    fun resolve(
        tx: TransactionEntity,
        wallets: List<Wallet>,
        overrides: Map<String, String>
    ): String {
        overrides["tx:${tx.id}"]?.let { return it }
        return walletForPackage(tx.source.takeIf { it != MANUAL_SOURCE }, wallets)
    }

    fun resolveMovement(
        movementId: String,
        wallets: List<Wallet>,
        overrides: Map<String, String>
    ): String = overrides["mov:$movementId"] ?: EFECTIVO_ID

    private fun TransactionEntity.isIncome(): Boolean =
        type.equals("INCOME", ignoreCase = true) || type.equals("ingreso", ignoreCase = true)

    /** Neto (ingresos menos gastos) del mes por billetera. */
    fun monthNet(
        transactions: List<TransactionEntity>,
        month: YearMonth,
        wallets: List<Wallet>,
        overrides: Map<String, String>
    ): Map<String, Double> {
        val net = mutableMapOf<String, Double>()
        transactions.forEach { tx ->
            val txMonth = YearMonth.from(
                Instant.ofEpochMilli(tx.timestamp).atZone(ZoneOffset.UTC).toLocalDate()
            )
            if (txMonth != month) return@forEach
            val walletId = resolve(tx, wallets, overrides)
            val signed = if (tx.isIncome()) tx.amount else -tx.amount
            net[walletId] = (net[walletId] ?: 0.0) + signed
        }
        return net
    }
}
