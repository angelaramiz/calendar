package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class StreakPlannerTest {

    private fun expense(day: Int, amount: Double, category: String): TransactionEntity {
        val ts = YearMonth.of(2026, 9).atDay(day)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return TransactionEntity(
            amount = amount,
            type = "EXPENSE",
            category = category,
            description = "Gasto",
            timestamp = ts,
            source = "MANUAL"
        )
    }

    @Test
    fun quincena_bajo_tope_suma_racha() {
        val txs = listOf(expense(3, 500.0, "Comida"))
        val under = StreakPlanner.underCaps(
            txs, mapOf("Comida" to 2000.0),
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)
        )
        assertEquals(mapOf("Comida" to true), under)
        val (next, period) = StreakPlanner.nextStreaks(
            emptyMap(), null, "2026-09-Q1", under
        )
        assertEquals(1, next["Comida"])
        assertEquals("2026-09-Q1", period)
    }

    @Test
    fun racha_rota_vuelve_a_cero() {
        val txs = listOf(expense(3, 5000.0, "Comida"))
        val under = StreakPlanner.underCaps(
            txs, mapOf("Comida" to 2000.0),
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)
        )
        assertEquals(mapOf("Comida" to false), under)
        val (next, _) = StreakPlanner.nextStreaks(
            mapOf("Comida" to 3), "2026-08-Q2", "2026-09-Q1", under
        )
        assertEquals(0, next["Comida"])
    }

    @Test
    fun mismo_periodo_no_cuenta_doble() {
        val under = mapOf("Comida" to true)
        val (once, period) = StreakPlanner.nextStreaks(emptyMap(), null, "2026-09-Q1", under)
        val (twice, _) = StreakPlanner.nextStreaks(once, period, "2026-09-Q1", under)
        assertEquals(once, twice)
        assertEquals(1, twice["Comida"])
    }

    @Test
    fun periodKey_corta_en_15_y_16() {
        assertEquals("2026-09-Q1", StreakPlanner.periodKey(LocalDate.of(2026, 9, 15)))
        assertEquals("2026-09-Q2", StreakPlanner.periodKey(LocalDate.of(2026, 9, 16)))
        assertTrue(StreakPlanner.periodKey(LocalDate.of(2026, 9, 1)).endsWith("Q1"))
        assertFalse(StreakPlanner.periodKey(LocalDate.of(2026, 2, 28)).endsWith("Q1"))
    }

    @Test
    fun categorias_sin_tope_no_entran() {
        val txs = listOf(expense(3, 100.0, "Otros"))
        val under = StreakPlanner.underCaps(
            txs, mapOf("Comida" to 2000.0),
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)
        )
        assertTrue("Otros" !in under)
    }
}
