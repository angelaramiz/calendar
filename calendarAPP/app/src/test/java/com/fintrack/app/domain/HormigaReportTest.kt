package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HormigaReportTest {

    private fun expense(day: Int, amount: Double, merchant: String): TransactionEntity {
        val ts = LocalDate.of(2026, 9, day)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return TransactionEntity(
            amount = amount,
            type = "EXPENSE",
            category = "Comida",
            description = "Compra",
            merchant = merchant,
            timestamp = ts,
            source = "MANUAL"
        )
    }

    private val from = LocalDate.of(2026, 9, 7)
    private val to = LocalDate.of(2026, 9, 13)

    @Test
    fun conteo_de_visitas_y_total_del_top() {
        val txs = listOf(
            expense(7, 120.0, "Oxxo Centro"),
            expense(8, 80.0, "OXXO norte"),
            expense(9, 60.0, "oxxo sur"),
            expense(10, 500.0, "Walmart")
        )
        val report = HormigaDetector.topHormiga(txs, from, to)
        assertEquals("oxxo", report?.merchant)
        assertEquals(3, report?.visits)
        assertEquals(260.0, report?.total ?: 0.0, 0.0)
    }

    @Test
    fun fuera_de_la_semana_no_cuenta() {
        val txs = listOf(expense(1, 120.0, "Oxxo Centro"))
        assertNull(HormigaDetector.topHormiga(txs, from, to))
    }

    @Test
    fun sin_visitas_no_hay_reporte() {
        val txs = listOf(expense(8, 500.0, "Walmart"))
        assertNull(HormigaDetector.topHormiga(txs, from, to))
    }

    @Test
    fun ingresos_no_cuentan_como_visita() {
        val txs = listOf(
            expense(8, 120.0, "Oxxo Centro").copy(type = "INCOME")
        )
        assertTrue(HormigaDetector.topHormiga(txs, from, to) == null)
    }
}
