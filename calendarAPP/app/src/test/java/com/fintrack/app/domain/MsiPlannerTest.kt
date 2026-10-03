package com.fintrack.app.domain

import com.fintrack.app.data.MsiPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MsiPlannerTest {

    private fun plan(
        total: Double = 10_200.0,
        meses: Int = 12,
        primerCorte: String = "2026-09-10",
        cardId: String = "didi"
    ) = MsiPlan("p1", cardId, "Celular", total, meses, primerCorte)

    @Test
    fun mensualidad_exacta_y_ajuste_ultima_cuota() {
        val parciales = MsiPlanner.parciales(plan(total = 10_200.0, meses = 12))
        assertEquals(12, parciales.size)
        parciales.forEach { assertEquals(850.0, it, 0.001) }
        assertEquals(10_200.0, parciales.sum(), 0.005)
        // Con centavos que no dividen exacto, la última absorbe el ajuste.
        val raros = MsiPlanner.parciales(plan(total = 10_000.0, meses = 3))
        assertEquals(3, raros.size)
        assertEquals(3_333.33, raros[0], 0.001)
        assertEquals(3_333.34, raros[2], 0.001)
        assertEquals(10_000.0, raros.sum(), 0.005)
    }

    @Test
    fun cuotas_caen_en_cortes() {
        val cuotas = MsiPlanner.cuotas(plan(meses = 3, primerCorte = "2026-09-10"), cutoffDay = 10)
        assertEquals(3, cuotas.size)
        assertEquals(LocalDate.of(2026, 9, 10), cuotas[0].corteIso)
        assertEquals(LocalDate.of(2026, 10, 10), cuotas[1].corteIso)
        assertEquals(LocalDate.of(2026, 11, 10), cuotas[2].corteIso)
        assertEquals(1, cuotas[0].numero)
        assertEquals(3, cuotas[0].total)
    }

    @Test
    fun cuota_cubierta_con_cargo_tag() {
        val cuota = MsiPlanner.cuotas(plan(meses = 12), cutoffDay = 10)[1]
        // Periodo [10 sep, 10 oct): cargo tagueado >= parcial la cubre.
        val cargos = listOf(LocalDate.of(2026, 9, 20) to 850.0)
        assertTrue(MsiPlanner.cuotaCubierta(cuota, 10, cargos))
    }

    @Test
    fun cuota_pendiente_sin_cargo() {
        val cuota = MsiPlanner.cuotas(plan(meses = 12), cutoffDay = 10)[1]
        assertFalse(MsiPlanner.cuotaCubierta(cuota, 10, emptyList()))
        // Cargo fuera del periodo no cubre.
        val fuera = listOf(LocalDate.of(2026, 8, 5) to 850.0)
        assertFalse(MsiPlanner.cuotaCubierta(cuota, 10, fuera))
        // Cargo parcial por debajo del parcial tampoco.
        val corto = listOf(LocalDate.of(2026, 9, 20) to 100.0)
        assertFalse(MsiPlanner.cuotaCubierta(cuota, 10, corto))
    }

    @Test
    fun liquidar_cierra_proyeccion() {
        val plan = plan()
        assertEquals(12, MsiPlanner.cuotas(plan, 10).size)
        val liquidado = MsiPlanner.liquidar(plan)
        assertTrue(liquidado.liquidado)
        assertTrue(MsiPlanner.cuotas(liquidado, 10).isEmpty())
        assertEquals(0.0, MsiPlanner.msiEnCorte(listOf(liquidado), 10, LocalDate.of(2026, 9, 10)), 0.001)
    }

    @Test
    fun caso_didi_12_meses() {
        // Compra DiDi a 12 MSI de $10,200: $850 por corte, termina sep 2027.
        val didi = plan(total = 10_200.0, meses = 12, primerCorte = "2026-10-10", cardId = "didi")
        val cuotas = MsiPlanner.cuotas(didi, 10)
        assertEquals(12, cuotas.size)
        assertEquals(LocalDate.of(2027, 9, 10), cuotas.last().corteIso)
        assertEquals(850.0, MsiPlanner.mensualidad(didi), 0.001)
        val hoy = LocalDate.of(2026, 12, 15)
        val (hechas, total) = MsiPlanner.progreso(didi, 10, hoy)
        assertEquals(12, total)
        assertEquals(3, hechas)
        // El A pagar del corte abierto (10 dic) incluye $850 de este plan.
        assertEquals(
            850.0,
            MsiPlanner.msiEnCorte(listOf(didi), 10, LocalDate.of(2026, 12, 10)),
            0.001
        )
        // Saldo pendiente = 10 cuotas restantes.
        assertEquals(8_500.0, MsiPlanner.saldoPendiente(didi, 10, hoy), 0.01)
    }
}
