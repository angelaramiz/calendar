package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class SpendingHeatmapTest {

    private val month = YearMonth.of(2026, 9)

    private fun expense(day: Int, amount: Double, category: String = "Comida"): TransactionEntity {
        val ts = month.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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
    fun quintil_dias_sin_gasto_son_cero() {
        val daily = mapOf(
            LocalDate.of(2026, 9, 1) to 0.0,
            LocalDate.of(2026, 9, 2) to 100.0
        )
        val out = SpendingHeatmap.quintiles(daily)
        assertEquals(0, out[LocalDate.of(2026, 9, 1)])
    }

    @Test
    fun quintil_ordena_de_menor_a_mayor() {
        val daily = (1..10).associate { day ->
            LocalDate.of(2026, 9, day) to (day * 100.0)
        }
        val out = SpendingHeatmap.quintiles(daily)
        val first = out[LocalDate.of(2026, 9, 1)]!!
        val last = out[LocalDate.of(2026, 9, 10)]!!
        assertTrue(first < last)
        assertEquals(0, first)
        assertEquals(4, last)
    }

    @Test
    fun quintil_limites_0_a_4() {
        val sorted = listOf(50.0, 100.0, 200.0, 400.0, 800.0)
        sorted.forEach { amount ->
            val q = SpendingHeatmap.quintileOf(amount, sorted)
            assertTrue(q in 0..4)
        }
        assertEquals(0, SpendingHeatmap.quintileOf(0.0, sorted))
        assertEquals(0, SpendingHeatmap.quintileOf(100.0, emptyList()))
    }

    @Test
    fun gasto_diario_suma_solo_gastos_del_mes() {
        val inMonth = expense(5, 200.0)
        val income = expense(6, 500.0).copy(type = "INCOME")
        val otherMonth = expense(5, 300.0).copy(
            timestamp = YearMonth.of(2026, 8).atDay(5)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        val daily = SpendingHeatmap.dailyExpense(
            listOf(inMonth, income, otherMonth), month
        )
        assertEquals(200.0, daily[LocalDate.of(2026, 9, 5)] ?: 0.0, 0.0)
        assertTrue(LocalDate.of(2026, 9, 6) !in daily)
    }

    @Test
    fun diff_por_categoria_con_delta_y_porcentaje() {
        val diff = SpendingHeatmap.monthCategoryDiff(
            current = mapOf("Comida" to 1200.0, "Ocio" to 300.0),
            previous = mapOf("Comida" to 1000.0, "Ocio" to 500.0)
        )
        val comida = diff.first { it.category == "Comida" }
        assertEquals(200.0, comida.delta, 0.0)
        assertEquals(20.0, comida.percent ?: 0.0, 0.001)
        val ocio = diff.first { it.category == "Ocio" }
        assertEquals(-200.0, ocio.delta, 0.0)
        assertEquals(-40.0, ocio.percent ?: 0.0, 0.001)
    }

    @Test
    fun diff_sin_base_previa_no_tiene_porcentaje() {
        val diff = SpendingHeatmap.monthCategoryDiff(
            current = mapOf("Nueva" to 400.0),
            previous = emptyMap()
        )
        assertEquals(1, diff.size)
        assertEquals(400.0, diff[0].delta, 0.0)
        assertNull(diff[0].percent)
    }

    @Test
    fun diff_omite_categorias_vacias_en_ambos() {
        val diff = SpendingHeatmap.monthCategoryDiff(
            current = mapOf("Comida" to 100.0, "Vacia" to 0.0),
            previous = mapOf("Vacia" to 0.0)
        )
        assertEquals(1, diff.size)
        assertEquals("Comida", diff[0].category)
    }
}
