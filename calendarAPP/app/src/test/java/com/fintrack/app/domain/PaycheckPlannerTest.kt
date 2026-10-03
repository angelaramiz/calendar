package com.fintrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaycheckPlannerTest {

    private fun input(
        ingreso: Double = 12_000.0,
        servicios: Double = 2_000.0,
        minimos: Map<String, Double> = mapOf("nu" to 3_000.0, "plata" to 1_000.0),
        ahorro: Double = 1_000.0,
        extraRate: Double = 0.20,
        start: LocalDate = LocalDate.of(2026, 10, 1),
        end: LocalDate = LocalDate.of(2026, 10, 15)
    ) = PaycheckPlanner.PaycheckInput(
        ingreso = ingreso,
        serviciosTotal = servicios,
        minimosTarjetas = minimos,
        ahorroMeta = ahorro,
        extraRate = extraRate,
        periodStart = start,
        periodEnd = end
    )

    @Test
    fun reparto_quincena_15_fijos_minimos_excedente_ahorro_libre() {
        //Ingreso 12_000 − fijos (2_000 + 4_000) − ahorro 1_000 = resto 5_000.
        // Extra 20% = 1_000 (750 Nu, 250 Plata); libre 4_000.
        val plan = PaycheckPlanner.plan(input())

        assertEquals(6_000.0, plan.fijos, 0.001)
        assertEquals(4_000.0, plan.minimosTotal, 0.001)
        assertEquals(750.0, plan.extraDeuda.getValue("nu"), 0.001)
        assertEquals(250.0, plan.extraDeuda.getValue("plata"), 0.001)
        assertEquals(1_000.0, plan.ahorro, 0.001)
        assertEquals(4_000.0, plan.libre, 0.001)
        assertTrue(plan.markers.size >= 4)
    }

    @Test
    fun reparto_fin_de_mes_usa_periodo_16_a_fin() {
        val (start, end) = PaycheckPlanner.periodFor(LocalDate.of(2026, 10, 28))

        assertEquals(LocalDate.of(2026, 10, 16), start)
        assertEquals(LocalDate.of(2026, 10, 31), end)

        val plan = PaycheckPlanner.plan(input(start = start, end = end))
        assertTrue(plan.markers.any { it.contains("16") && it.contains("31") })
    }

    @Test
    fun sin_deudas_todo_el_resto_es_libre() {
        val plan = PaycheckPlanner.plan(input(minimos = emptyMap()))

        assertEquals(0.0, plan.minimosTotal, 0.001)
        assertTrue(plan.extraDeuda.isEmpty())
        // 12_000 − 2_000 − 1_000 = 9_000 libres.
        assertEquals(9_000.0, plan.libre, 0.001)
    }

    @Test
    fun ingreso_insuficiente_deja_libre_en_cero() {
        val plan = PaycheckPlanner.plan(input(ingreso = 5_000.0))

        assertEquals(0.0, plan.libre, 0.001)
        assertTrue(plan.extraDeuda.isEmpty())
        assertEquals(6_000.0, plan.fijos, 0.001)
    }

    @Test
    fun excedente_cero_respeta_tasa_configurable() {
        val plan = PaycheckPlanner.plan(input(extraRate = 0.0))

        assertTrue(plan.extraDeuda.values.all { it == 0.0 })
        assertEquals(5_000.0, plan.libre, 0.001)
    }

    @Test
    fun prorrateo_proporcional_a_saldos() {
        val plan = PaycheckPlanner.plan(
            input(minimos = mapOf("a" to 9_000.0, "b" to 1_000.0))
        )
        // Resto = 12_000 − (2_000+10_000) − 1_000 < 0 → sin extra.
        // Con ingreso mayor sí prorratea 90/10.
        val amplio = PaycheckPlanner.plan(
            input(ingreso = 22_000.0, minimos = mapOf("a" to 9_000.0, "b" to 1_000.0))
        )
        // Resto = 22_000 − 12_000 − 1_000 = 9_000; extra = 1_800.
        assertEquals(1_620.0, amplio.extraDeuda.getValue("a"), 0.001)
        assertEquals(180.0, amplio.extraDeuda.getValue("b"), 0.001)
        assertEquals(0.0, plan.libre, 0.001)
    }
}
