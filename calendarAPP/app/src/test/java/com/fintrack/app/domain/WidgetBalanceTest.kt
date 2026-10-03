package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetBalanceTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val yesterday = today.minusDays(1)

    private fun tx(
        day: LocalDate,
        amount: Double,
        type: String = "EXPENSE",
        id: String = "tx-$day-$amount"
    ) = TransactionEntity(
        id = id,
        amount = amount,
        type = type,
        category = "Comida",
        description = "Prueba",
        timestamp = day.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3600 * 1000
    )

    @Test
    fun balance_hoy_ingresos_menos_gastos() {
        val txs = listOf(
            tx(today, 8000.0, "INCOME", "in"),
            tx(today, 200.0, "EXPENSE", "g1"),
            tx(yesterday, 5000.0, "EXPENSE", "ayer")
        )
        assertEquals(7800.0, WidgetBalance.todayBalance(txs, day = today, zone = zone), 0.001)
    }

    @Test
    fun cargo_a_credito_no_resta() {
        val txs = listOf(tx(today, 1500.0, "EXPENSE", "cred"))
        val charges = mapOf("tx:cred" to "nu")
        assertEquals(0.0, WidgetBalance.todayBalance(txs, charges, today, zone), 0.001)
    }

    @Test
    fun dia_vacio_es_cero() {
        assertEquals(0.0, WidgetBalance.todayBalance(emptyList(), day = today, zone = zone), 0.001)
    }

    @Test
    fun formato_corto_en_pesos() {
        assertEquals("$7,800.00", WidgetBalance.format(7800.0))
        assertEquals("−$1,500.00", WidgetBalance.format(-1500.0))
    }
}
