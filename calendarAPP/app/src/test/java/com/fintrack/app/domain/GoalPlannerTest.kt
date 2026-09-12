package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalPlannerTest {

    @Test
    fun contado_calcula_meses_como_precio_entre_superavit() {
        val plan = GoalPlanner.evaluateCash(price = 120_000.0, avgSurplus = 10_000.0)

        assertEquals(12, plan.monthsNeeded)
        assertEquals(GoalVerdict.FACTIBLE, plan.verdict)
    }

    @Test
    fun contado_redondea_hacia_arriba_los_meses() {
        val plan = GoalPlanner.evaluateCash(price = 100_000.0, avgSurplus = 30_000.0)

        assertEquals(4, plan.monthsNeeded)
    }

    @Test
    fun contado_sin_superavit_es_inviable_con_explicacion() {
        val plan = GoalPlanner.evaluateCash(price = 200_000.0, avgSurplus = 0.0)

        assertNull(plan.monthsNeeded)
        assertEquals(GoalVerdict.INVIABLE, plan.verdict)
        assertTrue(plan.explanation.isNotBlank())
    }

    @Test
    fun contado_con_superavit_negativo_es_inviable() {
        val plan = GoalPlanner.evaluateCash(price = 200_000.0, avgSurplus = -500.0)

        assertNull(plan.monthsNeeded)
        assertEquals(GoalVerdict.INVIABLE, plan.verdict)
    }

    @Test
    fun mensualidad_sistema_frances_con_tasa_conocida() {
        // P=100000, tasa anual 12% (r=0.01), n=12 -> cuota ~8884.88
        val cuota = GoalPlanner.monthlyPayment(
            principal = 100_000.0,
            annualRatePercent = 12.0,
            months = 12
        )

        assertEquals(8_884.88, cuota, 1.0)
    }

    @Test
    fun mensualidad_sin_interes_reparte_capital() {
        val cuota = GoalPlanner.monthlyPayment(
            principal = 120_000.0,
            annualRatePercent = 0.0,
            months = 12
        )

        assertEquals(10_000.0, cuota, 0.01)
    }

    @Test
    fun mensualidad_con_plazo_invalido_devuelve_cero() {
        assertEquals(0.0, GoalPlanner.monthlyPayment(100_000.0, 12.0, 0), 0.0)
        assertEquals(0.0, GoalPlanner.monthlyPayment(100_000.0, 12.0, -6), 0.0)
    }

    @Test
    fun credito_factible_cuando_cuota_no_supera_30_porciento_del_ingreso() {
        val plan = GoalPlanner.evaluateCredit(
            price = 200_000.0,
            downPayment = 40_000.0,
            annualRatePercent = 0.0,
            months = 48,
            monthlyIncome = 30_000.0,
            avgSurplus = 10_000.0
        )

        // Financiado 160000 / 48 = 3333.33 -> 11.1% del ingreso
        assertEquals(3_333.33, plan.monthlyPayment, 1.0)
        assertEquals(GoalVerdict.FACTIBLE, plan.verdict)
    }

    @Test
    fun credito_ajustado_cuando_cuota_llega_hasta_40_porciento() {
        val plan = GoalPlanner.evaluateCredit(
            price = 200_000.0,
            downPayment = 40_000.0,
            annualRatePercent = 0.0,
            months = 16,
            monthlyIncome = 30_000.0,
            avgSurplus = 10_000.0
        )

        // 160000 / 16 = 10000 -> 33.3% del ingreso
        assertEquals(GoalVerdict.AJUSTADO, plan.verdict)
    }

    @Test
    fun credito_inviable_cuando_cuota_supera_40_porciento() {
        val plan = GoalPlanner.evaluateCredit(
            price = 200_000.0,
            downPayment = 20_000.0,
            annualRatePercent = 0.0,
            months = 12,
            monthlyIncome = 30_000.0,
            avgSurplus = 10_000.0
        )

        // 180000 / 12 = 15000 -> 50% del ingreso
        assertEquals(GoalVerdict.INVIABLE, plan.verdict)
    }

    @Test
    fun credito_calcula_intereses_y_costo_total() {
        val plan = GoalPlanner.evaluateCredit(
            price = 100_000.0,
            downPayment = 0.0,
            annualRatePercent = 12.0,
            months = 12,
            monthlyIncome = 100_000.0,
            avgSurplus = 50_000.0
        )

        assertEquals(100_000.0, plan.financedAmount, 0.01)
        assertTrue(plan.totalInterest > 0.0)
        assertEquals(
            plan.monthlyPayment * 12,
            plan.totalCost - plan.downPayment,
            1.0
        )
    }

    @Test
    fun enganche_tarda_mas_de_6_meses_degrada_veredicto() {
        val factiblePeroEngancheLejano = GoalPlanner.evaluateCredit(
            price = 300_000.0,
            downPayment = 100_000.0,
            annualRatePercent = 0.0,
            months = 60,
            monthlyIncome = 50_000.0,
            avgSurplus = 5_000.0
        )

        // Cuota 3333 (6.7% del ingreso) seria FACTIBLE, pero el enganche
        // tarda 20 meses de ahorro -> debe degradarse a AJUSTADO.
        assertEquals(20, factiblePeroEngancheLejano.downPaymentMonths)
        assertEquals(GoalVerdict.AJUSTADO, factiblePeroEngancheLejano.verdict)
    }

    @Test
    fun enganche_con_porcentaje_calcula_monto_correcto() {
        assertEquals(40_000.0, GoalPlanner.downPaymentAmount(200_000.0, 20.0, null), 0.01)
        assertEquals(50_000.0, GoalPlanner.downPaymentAmount(200_000.0, null, 50_000.0), 0.01)
    }

    @Test
    fun comparador_muestra_diferencia_como_sobrecosto_del_credito() {
        val comparison = GoalPlanner.compareCashVsCredit(
            price = 200_000.0,
            creditTotalCost = 250_000.0
        )

        assertEquals(200_000.0, comparison.cashTotalCost, 0.01)
        assertEquals(50_000.0, comparison.extraCost, 0.01)
    }
}
