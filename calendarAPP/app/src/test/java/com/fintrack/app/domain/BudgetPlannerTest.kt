package com.fintrack.app.domain

import com.fintrack.app.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class BudgetPlannerTest {

    private fun tx(
        type: String,
        category: String,
        amount: Double,
        date: LocalDate
    ) = TransactionEntity(
        id = "$type-$category-$date-$amount",
        amount = amount,
        type = type,
        category = category,
        timestamp = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    private fun monthlyPattern(
        id: String,
        type: String,
        amount: Double,
        category: String = "Otros",
        start: LocalDate = LocalDate.of(2026, 1, 5)
    ) = Pattern(
        id = id,
        name = id,
        type = type,
        baseAmount = amount,
        frequency = "monthly",
        interval = 1,
        startDate = start,
        active = true,
        category = category
    )

    @Test
    fun topes_por_categoria_aplican_porcentajes_del_ingreso() {
        val caps = BudgetPlanner.categoryCaps(10_000.0)

        assertEquals(2_500.0, caps["Comida"]!!, 0.01)
        assertEquals(1_500.0, caps["Transporte"]!!, 0.01)
        assertEquals(2_000.0, caps["Servicios"]!!, 0.01)
        assertEquals(1_000.0, caps["Ocio"]!!, 0.01)
    }

    @Test
    fun corto_plazo_marca_alerta_cuando_supera_80_porciento_del_tope() {
        val month = YearMonth.of(2026, 9)
        val transactions = listOf(
            tx("INCOME", "Sueldo", 4_000.0, LocalDate.of(2026, 9, 1)),
            tx("EXPENSE", "Comida", 850.0, LocalDate.of(2026, 9, 10))
        )

        val report = BudgetPlanner.buildShortTerm(transactions, month)
        val comida = report.items.first { it.category == "Comida" }

        assertEquals(1_000.0, comida.cap, 0.01)
        assertEquals(850.0, comida.spent, 0.01)
        assertTrue(comida.nearLimit)
        assertFalse(comida.overCap)
    }

    @Test
    fun corto_plazo_marca_exceso_cuando_supera_el_tope() {
        val month = YearMonth.of(2026, 9)
        val transactions = listOf(
            tx("INCOME", "Sueldo", 4_000.0, LocalDate.of(2026, 9, 1)),
            tx("EXPENSE", "Comida", 1_200.0, LocalDate.of(2026, 9, 10))
        )

        val report = BudgetPlanner.buildShortTerm(transactions, month)
        val comida = report.items.first { it.category == "Comida" }

        assertTrue(comida.overCap)
        assertTrue(comida.nearLimit)
    }

    @Test
    fun mes_sin_ingresos_no_rompe_ni_marca_falsas_alertas() {
        val month = YearMonth.of(2026, 9)
        val transactions = listOf(
            tx("EXPENSE", "Comida", 500.0, LocalDate.of(2026, 9, 10))
        )

        val report = BudgetPlanner.buildShortTerm(transactions, month)

        assertEquals(0.0, report.monthIncome, 0.01)
        assertTrue(report.items.all { it.cap == 0.0 })
        assertTrue(report.items.all { it.usageRatio == 0.0 })
        assertTrue(report.items.none { it.nearLimit })
        assertTrue(report.items.none { it.overCap })
    }

    @Test
    fun proyeccion_3_meses_con_patron_mensual() {
        val patterns = listOf(
            monthlyPattern("sueldo", "INCOME", 2_000.0),
            monthlyPattern("alquiler", "EXPENSE", 500.0, "Servicios")
        )

        val projections = BudgetPlanner.projectRecurring(
            patterns, YearMonth.of(2026, 9), 3
        )

        assertEquals(3, projections.size)
        assertEquals(YearMonth.of(2026, 9), projections[0].month)
        assertEquals(YearMonth.of(2026, 11), projections[2].month)
        projections.forEach {
            assertEquals(2_000.0, it.projectedIncome, 0.01)
            assertEquals(500.0, it.projectedExpense, 0.01)
            assertEquals(1_500.0, it.surplus, 0.01)
        }
    }

    @Test
    fun mediano_plazo_suma_promedio_variable_al_gasto() {
        val transactions = listOf(
            tx("EXPENSE", "Otros", 300.0, LocalDate.of(2026, 6, 12)),
            tx("EXPENSE", "Otros", 300.0, LocalDate.of(2026, 7, 12)),
            tx("EXPENSE", "Otros", 300.0, LocalDate.of(2026, 8, 12))
        )

        val projections = BudgetPlanner.buildMediumTerm(
            emptyList(), transactions, YearMonth.of(2026, 9), 3
        )

        assertEquals(3, projections.size)
        projections.forEach {
            assertEquals(300.0, it.projectedExpense, 0.01)
            assertEquals(-300.0, it.surplus, 0.01)
        }
    }

    @Test
    fun meta_factible_cuando_superavit_cubre_ahorro() {
        val evaluation = BudgetPlanner.evaluateGoal(
            goalAmount = 6_000.0, months = 12, avgMonthlySurplus = 1_500.0
        )

        assertEquals(500.0, evaluation.requiredMonthly, 0.01)
        assertEquals(GoalVerdict.FACTIBLE, evaluation.verdict)
    }

    @Test
    fun meta_ajustada_cuando_falta_menos_del_30_porciento() {
        val evaluation = BudgetPlanner.evaluateGoal(
            goalAmount = 12_000.0, months = 12, avgMonthlySurplus = 800.0
        )

        assertEquals(1_000.0, evaluation.requiredMonthly, 0.01)
        assertEquals(GoalVerdict.AJUSTADO, evaluation.verdict)
    }

    @Test
    fun meta_inviable_cuando_requerido_supera_ampliamente_el_superavit() {
        val evaluation = BudgetPlanner.evaluateGoal(
            goalAmount = 60_000.0, months = 12, avgMonthlySurplus = 1_500.0
        )

        assertEquals(5_000.0, evaluation.requiredMonthly, 0.01)
        assertEquals(GoalVerdict.INVIABLE, evaluation.verdict)
    }

    @Test
    fun ingresos_no_cuentan_como_gasto_y_solo_mes_actual_cuenta() {
        val month = YearMonth.of(2026, 9)
        val transactions = listOf(
            tx("INCOME", "Sueldo", 5_000.0, LocalDate.of(2026, 9, 1)),
            tx("EXPENSE", "Comida", 200.0, LocalDate.of(2026, 9, 5)),
            tx("EXPENSE", "Comida", 900.0, LocalDate.of(2026, 8, 5))
        )

        val report = BudgetPlanner.buildShortTerm(transactions, month)
        val comida = report.items.first { it.category == "Comida" }

        assertEquals(5_000.0, report.monthIncome, 0.01)
        assertEquals(200.0, comida.spent, 0.01)
    }

    @Test
    fun plazo_invalido_devuelve_veredicto_inviable_sin_romper() {
        val evaluation = BudgetPlanner.evaluateGoal(
            goalAmount = 5_000.0, months = 0, avgMonthlySurplus = 1_500.0
        )

        assertEquals(0.0, evaluation.requiredMonthly, 0.01)
        assertEquals(GoalVerdict.INVIABLE, evaluation.verdict)
    }
}
