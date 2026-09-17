package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class SubscriptionDetectorTest {

    private val ref = YearMonth.of(2026, 9)

    private fun charge(
        merchant: String,
        amount: Double,
        yearMonth: YearMonth,
        day: Int = 15
    ): TransactionEntity {
        val ts = yearMonth.atDay(minOf(day, yearMonth.lengthOfMonth()))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return TransactionEntity(
            amount = amount,
            type = "EXPENSE",
            category = "Ocio",
            description = "Cargo $merchant",
            merchant = merchant,
            timestamp = ts,
            source = "com.nu.production"
        )
    }

    private fun noPatterns() = emptyList<Pattern>()

    @Test
    fun tres_meses_iguales_se_detectan() {
        val txs = listOf(
            charge("Netflix", 219.0, ref),
            charge("Netflix", 219.0, ref.minusMonths(1)),
            charge("Netflix", 219.0, ref.minusMonths(2))
        )
        val found = SubscriptionDetector.detect(txs, noPatterns(), ref)
        assertEquals(1, found.size)
        assertEquals("Netflix", found[0].merchant)
        assertEquals(219.0, found[0].amount, 0.0)
        assertEquals(ref.plusMonths(1), YearMonth.from(found[0].nextExpected))
    }

    @Test
    fun con_solo_dos_meses_no_hay_candidata() {
        val txs = listOf(
            charge("Spotify", 129.0, ref),
            charge("Spotify", 129.0, ref.minusMonths(1))
        )
        assertTrue(SubscriptionDetector.detect(txs, noPatterns(), ref).isEmpty())
    }

    @Test
    fun monto_distinto_no_agrupa() {
        val txs = listOf(
            charge("Amazon", 100.0, ref),
            charge("Amazon", 250.0, ref.minusMonths(1)),
            charge("Amazon", 100.0, ref.minusMonths(2))
        )
        assertTrue(SubscriptionDetector.detect(txs, noPatterns(), ref).isEmpty())
    }

    @Test
    fun ingresos_no_cuentan() {
        val txs = listOf(
            charge("Netflix", 219.0, ref),
            charge("Netflix", 219.0, ref.minusMonths(1)),
            charge("Netflix", 219.0, ref.minusMonths(2)).copy(type = "INCOME")
        )
        assertTrue(SubscriptionDetector.detect(txs, noPatterns(), ref).isEmpty())
    }

    @Test
    fun fuera_de_la_ventana_de_6_meses_no_cuenta() {
        val txs = listOf(
            charge("Netflix", 219.0, ref),
            charge("Netflix", 219.0, ref.minusMonths(1)),
            charge("Netflix", 219.0, ref.minusMonths(6))
        )
        assertTrue(SubscriptionDetector.detect(txs, noPatterns(), ref).isEmpty())
    }

    @Test
    fun patron_activo_existente_la_excluye() {
        val txs = listOf(
            charge("Netflix", 219.0, ref),
            charge("Netflix", 219.0, ref.minusMonths(1)),
            charge("Netflix", 219.0, ref.minusMonths(2))
        )
        val patterns = listOf(
            Pattern(
                id = "p1", name = "Netflix", type = "EXPENSE", baseAmount = 219.0,
                frequency = "monthly", startDate = ref.minusMonths(2).atDay(15)
            )
        )
        assertTrue(SubscriptionDetector.detect(txs, patterns, ref).isEmpty())
    }

    @Test
    fun dia_mas_comun_define_el_proximo_cobro() {
        val txs = listOf(
            charge("Gym", 500.0, ref, day = 5),
            charge("Gym", 500.0, ref.minusMonths(1), day = 5),
            charge("Gym", 500.0, ref.minusMonths(2), day = 6)
        )
        val found = SubscriptionDetector.detect(txs, noPatterns(), ref)
        assertEquals(1, found.size)
        assertEquals(5, found[0].dayOfMonth)
        assertEquals(5, found[0].nextExpected.dayOfMonth)
    }
}
