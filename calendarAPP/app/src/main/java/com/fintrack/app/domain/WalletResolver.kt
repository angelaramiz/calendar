package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
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

    private fun TransactionEntity.isIncome(): Boolean = kind?.isIncome == true

    /** Flujo del mes por billetera (ingresos y gastos separados). */
    data class WalletMonthFlow(
        val income: Double = 0.0,
        val expense: Double = 0.0
    ) {
        /** Neto: cuánto dejó el mes en la cuenta (ingresos menos gastos). */
        val net: Double get() = income - expense
    }

    /**
     * Flujo (ingresos/gastos) del mes por billetera en la zona indicada.
     * Con UTC el mes se cortaba a las 18:00 (hora México): por defecto se usa
     * la zona del dispositivo; se conserva UTC explícito para compatibilidad.
     */
    fun monthFlow(
        transactions: List<TransactionEntity>,
        month: YearMonth,
        wallets: List<Wallet>,
        overrides: Map<String, String>,
        zone: ZoneId = ZoneOffset.UTC
    ): Map<String, WalletMonthFlow> {
        val flow = mutableMapOf<String, WalletMonthFlow>()
        transactions.forEach { tx ->
            val txMonth = YearMonth.from(
                Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
            )
            if (txMonth != month) return@forEach
            val walletId = resolve(tx, wallets, overrides)
            val current = flow[walletId] ?: WalletMonthFlow()
            flow[walletId] = if (tx.isIncome()) current.copy(income = current.income + tx.amount)
            else current.copy(expense = current.expense + tx.amount)
        }
        return flow
    }

    /** Neto (ingresos menos gastos) del mes por billetera. */
    fun monthNet(
        transactions: List<TransactionEntity>,
        month: YearMonth,
        wallets: List<Wallet>,
        overrides: Map<String, String>,
        zone: ZoneId = ZoneOffset.UTC
    ): Map<String, Double> =
        monthFlow(transactions, month, wallets, overrides, zone)
            .mapValues { (_, flow) -> flow.net }

    /** Neto del día por billetera (cuadra con la lista visible de Inicio). */
    fun dayNet(
        transactions: List<TransactionEntity>,
        day: java.time.LocalDate,
        wallets: List<Wallet>,
        overrides: Map<String, String>,
        zone: ZoneId = ZoneOffset.UTC
    ): Map<String, Double> {
        val net = mutableMapOf<String, Double>()
        transactions.forEach { tx ->
            val txDay = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
            if (txDay != day) return@forEach
            val walletId = resolve(tx, wallets, overrides)
            val signed = if (tx.isIncome()) tx.amount else -tx.amount
            net[walletId] = (net[walletId] ?: 0.0) + signed
        }
        return net
    }
}
