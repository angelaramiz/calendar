package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class WalletResolverTest {

    private val wallets = WalletResolver.DEFAULT_WALLETS

    private fun tx(id: String, source: String, amount: Double = 100.0, type: String = "EXPENSE") =
        TransactionEntity(id = id, amount = amount, type = type, source = source)

    @Test
    fun paquete_conocido_resuelve_su_billetera() {
        assertEquals(
            "nu",
            WalletResolver.walletForPackage("com.nu.production", wallets)
        )
    }

    @Test
    fun paquete_desconocido_y_manual_caen_a_efectivo() {
        assertEquals("efectivo", WalletResolver.walletForPackage("com.whatsapp", wallets))
        assertEquals("efectivo", WalletResolver.walletForPackage(null, wallets))
        assertEquals(
            "efectivo",
            WalletResolver.resolve(tx("1", "MANUAL"), wallets, emptyMap())
        )
    }

    @Test
    fun override_manual_gana_a_la_regla() {
        val t = tx("abc", "com.nu.production")
        assertEquals("nu", WalletResolver.resolve(t, wallets, emptyMap()))
        assertEquals(
            "efectivo",
            WalletResolver.resolve(t, wallets, mapOf("tx:abc" to "efectivo"))
        )
    }

    @Test
    fun monthNet_suma_neto_por_billetera_solo_del_mes() {
        val now = YearMonth.now(ZoneOffset.UTC)
        val base = now.atDay(10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val txs = listOf(
            tx("1", "com.nu.production", 500.0).copy(timestamp = base),
            tx("2", "com.nu.production", 200.0, "INCOME").copy(timestamp = base),
            tx("3", "MANUAL", 50.0).copy(timestamp = base),
            tx("4", "com.nu.production", 999.0).copy(
                timestamp = now.minusMonths(2).atDay(5).atStartOfDay(ZoneOffset.UTC)
                    .toInstant().toEpochMilli()
            )
        )
        val net = WalletResolver.monthNet(txs, now, wallets, emptyMap())
        assertEquals(-300.0, net["nu"] ?: 0.0, 0.0)
        assertEquals(-50.0, net["efectivo"] ?: 0.0, 0.0)
        assertTrue(net.keys.all { it == "nu" || it == "efectivo" })
    }

    @Test
    fun resolveMovement_usa_override_o_efectivo() {
        assertEquals(
            "plata",
            WalletResolver.resolveMovement("m1", wallets, mapOf("mov:m1" to "plata"))
        )
        assertEquals("efectivo", WalletResolver.resolveMovement("m2", wallets, emptyMap()))
    }

    @Test
    fun dayNet_solo_cuenta_el_dia_pedido() {        val now = YearMonth.now(ZoneOffset.UTC)
        val todayTs = now.atDay(10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val yesterdayTs = now.atDay(9).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val txs = listOf(
            tx("1", "com.nu.production", 500.0).copy(timestamp = todayTs),
            tx("2", "com.nu.production", 999.0).copy(timestamp = yesterdayTs)
        )
        val net = WalletResolver.monthNet(txs, now, wallets, emptyMap())
        assertEquals(-1499.0, net["nu"] ?: 0.0, 0.0)
        val day = WalletResolver.dayNet(
            txs, now.atDay(10), wallets, emptyMap()
        )
        assertEquals(-500.0, day["nu"] ?: 0.0, 0.0)
    }

    @Test
    fun displayName_con_y_sin_terminacion_debito() {
        assertEquals(
            "Mercado Pago •1234",
            com.fintrack.app.data.WalletRow("w1", "Mercado Pago", custom = true, last4 = "1234").displayName
        )
        assertEquals(
            "Efectivo",
            com.fintrack.app.data.WalletRow("efectivo", "Efectivo").displayName
        )
    }

    @Test
    fun codec_listas_ida_y_vuelta() {
        val hidden = listOf("azteca")
        val raw = com.fintrack.app.data.PendingOpCodec.encodeStringList(hidden)
        assertEquals(hidden, com.fintrack.app.data.PendingOpCodec.decodeStringList(raw))
        assertEquals(emptyList<String>(), com.fintrack.app.data.PendingOpCodec.decodeStringList(null))
    }
}
