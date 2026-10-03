package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class AnomalyCheckerTest {

    private val base = YearMonth.of(2026, 9).atDay(10)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun expense(
        label: String,
        amount: Double,
        atMs: Long,
        merchant: Boolean = true
    ) = TransactionEntity(
        amount = amount,
        type = "EXPENSE",
        category = "Compras",
        description = if (merchant) "Cargo $label" else label,
        merchant = if (merchant) label else null,
        timestamp = atMs,
        source = "MANUAL"
    )

    private fun hour(h: Long) = h * 3600L * 1000L

    @Test
    fun duplicado_mismo_comercio_y_monto_en_72h() {
        val txs = listOf(
            expense("Liverpool", 500.0, base),
            expense("Liverpool", 500.0, base + hour(30))
        )
        val found = AnomalyChecker.findDuplicates(txs)
        assertEquals(1, found.size)
        assertEquals(AnomalyKind.DUPLICATE, found[0].kind)
        assertEquals("Liverpool", found[0].merchant)
    }

    @Test
    fun ventana_72h_fuera_no_avisa() {
        val txs = listOf(
            expense("Liverpool", 500.0, base),
            expense("Liverpool", 500.0, base + hour(73))
        )
        assertTrue(AnomalyChecker.findDuplicates(txs).isEmpty())
    }

    @Test
    fun montos_distintos_no_son_duplicado() {
        val txs = listOf(
            expense("Liverpool", 500.0, base),
            expense("Liverpool", 501.0, base + hour(5))
        )
        assertTrue(AnomalyChecker.findDuplicates(txs).isEmpty())
    }

    @Test
    fun comercios_distintos_no_son_duplicado() {
        val txs = listOf(
            expense("Liverpool", 500.0, base),
            expense("Palacio", 500.0, base + hour(5))
        )
        assertTrue(AnomalyChecker.findDuplicates(txs).isEmpty())
    }

    @Test
    fun ingresos_repetidos_no_son_duplicado() {
        val txs = listOf(
            expense("Nomina", 9000.0, base).copy(type = "INCOME"),
            expense("Nomina", 9000.0, base + hour(10)).copy(type = "INCOME")
        )
        assertTrue(AnomalyChecker.findDuplicates(txs).isEmpty())
    }

    @Test
    fun subida_de_suscripcion_mayor_al_historico() {
        val month = YearMonth.of(2026, 9)
        fun charge(amount: Double, m: YearMonth): TransactionEntity {
            val ts = m.atDay(5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return expense("Netflix", amount, ts)
        }
        val txs = listOf(
            charge(219.0, month.minusMonths(3)),
            charge(219.0, month.minusMonths(2)),
            charge(219.0, month.minusMonths(1)),
            charge(249.0, month)
        )
        val found = AnomalyChecker.findPriceHikes(txs)
        assertEquals(1, found.size)
        assertEquals(AnomalyKind.PRICE_HIKE, found[0].kind)
        assertEquals("Netflix", found[0].merchant)
    }

    @Test
    fun centavos_dentro_de_tolerancia_no_es_subida() {
        val month = YearMonth.of(2026, 9)
        fun charge(amount: Double, m: YearMonth): TransactionEntity {
            val ts = m.atDay(5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return expense("Netflix", amount, ts)
        }
        val txs = listOf(
            charge(219.0, month.minusMonths(3)),
            charge(219.0, month.minusMonths(2)),
            charge(219.0, month.minusMonths(1)),
            charge(219.30, month)
        )
        assertTrue(AnomalyChecker.findPriceHikes(txs).isEmpty())
    }

    @Test
    fun sin_historial_de_3_meses_no_hay_subida() {
        val month = YearMonth.of(2026, 9)
        fun charge(amount: Double, m: YearMonth): TransactionEntity {
            val ts = m.atDay(5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return expense("HBO", amount, ts)
        }
        val txs = listOf(
            charge(149.0, month.minusMonths(1)),
            charge(199.0, month)
        )
        assertTrue(AnomalyChecker.findPriceHikes(txs).isEmpty())
    }
}
