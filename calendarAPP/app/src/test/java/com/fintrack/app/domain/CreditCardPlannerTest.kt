package com.fintrack.app.domain

import com.fintrack.app.data.CreditCardRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CreditCardPlannerTest {

    @Test
    fun displayName_con_y_sin_terminacion() {
        assertEquals(
            "Nu •1234",
            CreditCardRow("1", "Nu", 10, 30, "1234").displayName
        )
        assertEquals("Nu", CreditCardRow("1", "Nu", 10, 30).displayName)
    }

    @Test
    fun plazo_plata_30_dias_despues_del_corte() {
        // Corte 15 ene + 30 días = 14 feb (≈60 días de financiamiento total).
        assertEquals(
            LocalDate.of(2026, 2, 14),
            CreditCardPlanner.paymentForCutoffGrace(LocalDate.of(2026, 1, 15), 30)
        )
        assertEquals(
            LocalDate.of(2026, 2, 14),
            CreditCardPlanner.paymentFor(LocalDate.of(2026, 1, 15), 1, 30)
        )
    }

    @Test
    fun dia_fijo_sin_cambio_con_grace_cero() {
        assertEquals(
            LocalDate.of(2026, 2, 1),
            CreditCardPlanner.paymentFor(LocalDate.of(2026, 1, 15), 1, 0)
        )
    }

    @Test
    fun summarize_con_plata_usa_grace() {
        val summary = CreditCardPlanner.summarize(
            cardId = "plata",
            cutoffDay = 15,
            paymentDay = 1,
            // Estado abierto = corte 15 ene (periodo 15 dic -> 15 ene).
            charges = listOf(LocalDate.of(2026, 1, 10) to 1000.0),
            today = LocalDate.of(2026, 1, 20),
            graceDays = 30
        )
        // Pago del estado abierto: 15 ene + 30 = 14 feb.
        assertEquals(LocalDate.of(2026, 2, 14), summary.dueDate)
        assertEquals(LocalDate.of(2026, 1, 15), summary.statementCutoff)
        assertEquals(1000.0, summary.periodCharges, 0.001)
    }

    @Test
    fun corte_y_pago_del_mes_actual() {
        // Corte día 10, pago día 30; hoy 12 sep: estado abierto 10 sep, periodo 10 ago -> 10 sep.
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(
            LocalDate.of(2026, 8, 15) to 500.0,
            LocalDate.of(2026, 9, 9) to 200.0,
            LocalDate.of(2026, 9, 10) to 999.0 // día del corte: ya es del próximo estado
        )
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today)
        assertEquals(700.0, summary.periodCharges, 0.0)
        assertEquals(LocalDate.of(2026, 9, 10), summary.statementCutoff)
        assertEquals(LocalDate.of(2026, 9, 30), summary.dueDate)
    }

    @Test
    fun antes_del_corte_el_periodo_es_el_anterior() {
        // Hoy 5 sep, corte 10: estado abierto 10 ago (periodo 10 jul -> 10 ago), pago 30 ago.
        val today = LocalDate.of(2026, 9, 5)
        val charges = listOf(LocalDate.of(2026, 7, 20) to 300.0)
        val summary = CreditCardPlanner.summarize("plata", 10, 30, charges, today)
        assertEquals(300.0, summary.periodCharges, 0.0)
        assertEquals(LocalDate.of(2026, 8, 10), summary.statementCutoff)
        assertEquals(LocalDate.of(2026, 8, 30), summary.dueDate)
    }

    @Test
    fun dia_31_se_ajusta_al_mes() {
        val today = LocalDate.of(2026, 2, 10)
        // Corte 31 en febrero -> 28 feb.
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CreditCardPlanner.nextCutoff(31, today)
        )
    }

    @Test
    fun pago_siempre_despues_del_corte() {
        // Corte 25, pago 5: el pago del corte del 25 sep es el 5 oct.
        val cutoff = LocalDate.of(2026, 9, 25)
        assertEquals(
            LocalDate.of(2026, 10, 5),
            CreditCardPlanner.paymentForCutoff(cutoff, 5)
        )
    }

    @Test
    fun sin_cargos_el_total_es_cero() {
        val summary = CreditCardPlanner.summarize(
            "nu", 10, 30, emptyList(), LocalDate.of(2026, 9, 12)
        )
        assertEquals(0.0, summary.periodCharges, 0.0)
        assertEquals(0.0, summary.remaining, 0.0)
    }

    @Test
    fun pagos_descuentan_del_restante_del_corte() {
        val today = LocalDate.of(2026, 9, 12)
        // Estado abierto 10 sep: el pago va contra ese corte.
        val charges = listOf(LocalDate.of(2026, 9, 9) to 700.0)
        val payments = listOf(LocalDate.of(2026, 9, 10) to 500.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(700.0, summary.periodCharges, 0.0)
        assertEquals(500.0, summary.paid, 0.0)
        assertEquals(200.0, summary.remaining, 0.0)
        assertEquals(false, summary.isPaid)
    }

    @Test
    fun pago_mayor_al_total_no_deja_negativo() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(LocalDate.of(2026, 9, 9) to 700.0)
        val payments = listOf(LocalDate.of(2026, 9, 10) to 1_000.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(0.0, summary.remaining, 0.0)
        assertEquals(true, summary.isPaid)
    }

    @Test
    fun pago_de_otro_corte_no_descuenta() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(LocalDate.of(2026, 9, 9) to 700.0)
        // Pago contra un corte futuro (10 oct): no toca el estado abierto (10 sep).
        val payments = listOf(LocalDate.of(2026, 10, 10) to 500.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(0.0, summary.paid, 0.0)
        assertEquals(700.0, summary.remaining, 0.0)
        assertEquals(false, summary.isPaid)
    }

    @Test
    fun liquidar_el_abierto_marca_pagado() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(LocalDate.of(2026, 9, 9) to 700.0)
        val payments = listOf(LocalDate.of(2026, 9, 10) to 700.0)
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today, payments)
        assertEquals(0.0, summary.remaining, 0.0)
        assertEquals(true, summary.isPaid)
    }

    @Test
    fun cargos_del_corte_abierto_en_adelante_son_del_proximo() {
        val today = LocalDate.of(2026, 9, 12)
        val charges = listOf(
            LocalDate.of(2026, 9, 10) to 400.0, // día del corte abierto
            LocalDate.of(2026, 9, 11) to 100.0 // después del corte
        )
        val summary = CreditCardPlanner.summarize("nu", 10, 30, charges, today)
        assertEquals(0.0, summary.periodCharges, 0.0)
    }
}
