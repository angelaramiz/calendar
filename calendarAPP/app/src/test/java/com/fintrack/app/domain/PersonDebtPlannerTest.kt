package com.fintrack.app.domain

import com.fintrack.app.data.PersonDebt
import com.fintrack.app.data.PersonDebtAbono
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonDebtPlannerTest {

    private fun deuda(
        monto: Double,
        esDeudaMia: Boolean = false,
        abonos: List<Double> = emptyList()
    ) = PersonDebt(
        id = "d1",
        nombre = "Juan",
        monto = monto,
        esDeudaMia = esDeudaMia,
        fechaIso = "2026-09-01",
        abonos = abonos.mapIndexed { i, m ->
            PersonDebtAbono("a$i", m, "2026-09-0${i + 1}")
        }
    )

    @Test
    fun saldo_resta_abonos() {
        assertEquals(300.0, PersonDebtPlanner.saldo(deuda(500.0, abonos = listOf(200.0))), 0.001)
    }

    @Test
    fun saldo_sin_abonos_es_monto() {
        assertEquals(500.0, PersonDebtPlanner.saldo(deuda(500.0)), 0.001)
    }

    @Test
    fun saldo_nunca_negativo() {
        assertEquals(0.0, PersonDebtPlanner.saldo(deuda(500.0, abonos = listOf(600.0))), 0.001)
    }

    @Test
    fun liquidada_al_llegar_a_cero() {
        assertTrue(PersonDebtPlanner.estaLiquidada(deuda(500.0, abonos = listOf(500.0))))
        assertFalse(PersonDebtPlanner.estaLiquidada(deuda(500.0, abonos = listOf(499.0))))
    }

    @Test
    fun totales_separan_por_cobrar_y_por_pagar() {
        val deudas = listOf(
            deuda(500.0, esDeudaMia = false, abonos = listOf(200.0)),
            deuda(1000.0, esDeudaMia = true, abonos = listOf(400.0))
        )
        val totales = PersonDebtPlanner.totales(deudas)
        assertEquals(300.0, totales.porCobrar, 0.001)
        assertEquals(600.0, totales.porPagar, 0.001)
        assertEquals(-300.0, totales.neto, 0.001)
    }

    @Test
    fun totales_ignoran_liquidadas() {
        val deudas = listOf(
            deuda(500.0, esDeudaMia = false, abonos = listOf(500.0)),
            deuda(200.0, esDeudaMia = false)
        )
        val totales = PersonDebtPlanner.totales(deudas)
        assertEquals(200.0, totales.porCobrar, 0.001)
        assertEquals(0.0, totales.porPagar, 0.001)
    }
}
